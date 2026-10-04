#!/usr/bin/env python3
"""Validate public release contents and ELF LOAD alignment without installing the APK."""
import argparse
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--sdk', type=Path, required=True)
args = parser.parse_args()
build_tools = sorted((args.sdk/'build-tools').iterdir())[-1]
subprocess.run([str(build_tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(args.apk)], check=True)
subprocess.run([str(build_tools/'zipalign'), '-c', '-P', '16', '-v', '4', str(args.apk)], check=True, stdout=subprocess.DEVNULL)
with tempfile.TemporaryDirectory(dir=args.apk.parent, prefix='manifest-check-') as temporary:
    output = subprocess.check_output([str(build_tools/'aapt'), 'dump', 'xmltree', str(args.apk), 'AndroidManifest.xml'], text=True)
    assert 'io.github.sigmasd.foldercamera' in output
    assert 'TestDocumentsProvider' not in output and 'MANAGE_DOCUMENTS' not in output
    assert '.debug' not in output and 'debuggable(0x0101000f)=(type 0x12)0xffffffff' not in output
with zipfile.ZipFile(args.apk) as archive:
    assert 'assets/legal/privacy.txt' in archive.namelist()
    assert 'assets/legal/GPL-3.0.txt' in archive.namelist()
    for name in archive.namelist():
        if name.endswith('.so') and ('/arm64-v8a/' in name or '/x86_64/' in name):
            data = archive.read(name)
            assert data[:4] == b'\x7fELF' and data[4] == 2, name
            endian = '<' if data[5] == 1 else '>'
            offset = struct.unpack_from(endian+'Q', data, 32)[0]
            entry_size, count = struct.unpack_from(endian+'HH', data, 54)
            for index in range(count):
                entry = offset + index*entry_size
                if struct.unpack_from(endian+'I', data, entry)[0] == 1:
                    alignment = struct.unpack_from(endian+'Q', data, entry+48)[0]
                    assert alignment >= 16384, f'{name}: LOAD alignment {alignment}'
    print('PASS: signature, production manifest, bundled privacy/license, 16-KB ZIP and 64-bit ELF LOAD alignment')

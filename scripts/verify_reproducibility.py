#!/usr/bin/env python3
"""Verify an independent source APK using F-Droid's signature-copy algorithm."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

import apksigcopier


def sha256(path: Path) -> bytes:
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').digest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('signed', type=Path)
    parser.add_argument('unsigned', type=Path)
    parser.add_argument('--apksigner', type=Path, required=True)
    args = parser.parse_args()
    for path in (args.signed, args.unsigned, args.apksigner):
        if not path.is_file():
            parser.error(f'File does not exist: {path}')
    with tempfile.TemporaryDirectory(dir=args.signed.parent, prefix='repro-check-') as directory:
        copied = Path(directory) / 'reproduced.apk'
        apksigcopier.do_copy(str(args.signed), str(args.unsigned), str(copied))
        subprocess.run([str(args.apksigner), 'verify', str(copied)], check=True)
        if sha256(copied) != sha256(args.signed):
            raise SystemExit('FAIL: Signed APK differs from independently reproduced bytes')
    print('PASS: Independent source APK plus copied developer signature is byte-identical and cryptographically valid')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Verify independent unsigned APK through F-Droid's standard signature-copy algorithm."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile
import apksigcopier
parser=argparse.ArgumentParser()
parser.add_argument('signed',type=Path)
parser.add_argument('unsigned',type=Path)
parser.add_argument('--apksigner',type=Path,required=True)
args=parser.parse_args()
with tempfile.TemporaryDirectory(dir=args.signed.parent,prefix='repro-check-') as directory:
    copied=Path(directory)/'reproduced.apk'
    apksigcopier.do_copy(str(args.signed),str(args.unsigned),str(copied))
    subprocess.run([str(args.apksigner),'verify',str(copied)],check=True)
    assert hashlib.sha256(copied.read_bytes()).digest()==hashlib.sha256(args.signed.read_bytes()).digest(), 'Signed APK differs from independently reproduced bytes'
print('PASS: independent source APK plus copied developer signature is byte-identical and cryptographically valid')

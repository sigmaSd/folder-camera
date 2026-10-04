#!/usr/bin/env python3
"""Build locally with keys outside the repository; never print secret values."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--credentials', type=Path, default=Path.home()/'.local/share/folder-camera/signing/credentials.json')
parser.add_argument('--output', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
credentials = json.loads(args.credentials.read_text())
version = next(line.split('"')[1] for line in (root/'android/app/build.gradle.kts').read_text().splitlines() if 'versionName =' in line)
out = args.output or root/'.work/release-artifacts'
out.mkdir(parents=True, exist_ok=True)
for role, task, artifact in [('app', ':app:assembleRelease', 'apk/release/app-release.apk'), ('upload', ':app:bundleRelease', 'bundle/release/app-release.aab')]:
    key = credentials[role]
    environment = {**os.environ,
        'FOLDER_CAMERA_KEYSTORE': key['keystore'],
        'FOLDER_CAMERA_STORE_PASSWORD': key['password'],
        'FOLDER_CAMERA_KEY_ALIAS': key['alias'],
        'FOLDER_CAMERA_KEY_PASSWORD': key['password']}
    subprocess.run([str(root/'android/gradlew'), '-p', str(root/'android'), task, '--no-daemon'], env=environment, check=True)
    source = root/'android/app/build/outputs'/artifact
    target = out/f'folder-camera-{version}{source.suffix}'
    shutil.copyfile(source, target)
    print(f'Built {target}')

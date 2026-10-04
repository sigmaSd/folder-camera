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
parser.add_argument('--sdk', type=Path)
parser.add_argument('--unsigned-apk', type=Path, help='Use an independently validated canonical unsigned APK for F-Droid parity')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
credentials = json.loads(args.credentials.read_text())
version = next(line.split('"')[1] for line in (root/'android/app/build.gradle.kts').read_text().splitlines() if 'versionName =' in line)
out = args.output or root/'.work/release-artifacts'
out.mkdir(parents=True, exist_ok=True)
sdk = args.sdk or (Path(os.environ['ANDROID_HOME']) if os.environ.get('ANDROID_HOME') else Path(next(line.split('=',1)[1] for line in (root/'android/local.properties').read_text().splitlines() if line.startswith('sdk.dir='))))
build_tools = sorted((sdk/'build-tools').iterdir())[-1]
for role, task, artifact in [('app', ':app:assembleRelease', 'apk/release/app-release-unsigned.apk'), ('upload', ':app:bundleRelease', 'bundle/release/app-release.aab')]:
    key = credentials[role]
    environment = {**os.environ,
        'FOLDER_CAMERA_KEYSTORE': key['keystore'],
        'FOLDER_CAMERA_STORE_PASSWORD': key['password'],
        'FOLDER_CAMERA_KEY_ALIAS': key['alias'],
        'FOLDER_CAMERA_KEY_PASSWORD': key['password']}
    build_environment = environment if role == 'upload' else {k:v for k,v in environment.items() if not k.startswith('FOLDER_CAMERA_')}
    if role != 'app' or args.unsigned_apk is None:
        subprocess.run([str(root/'android/gradlew'), '-p', str(root/'android'), task, '--no-daemon'], env=build_environment, check=True)
    source = args.unsigned_apk if role == 'app' and args.unsigned_apk is not None else root/'android/app/build/outputs'/artifact
    target = out/f'folder-camera-{version}{source.suffix}'
    if role == 'app':
        subprocess.run([str(build_tools/'apksigner'), 'sign', '--ks', key['keystore'], '--ks-key-alias', key['alias'], '--ks-pass', 'env:FOLDER_CAMERA_STORE_PASSWORD', '--key-pass', 'env:FOLDER_CAMERA_KEY_PASSWORD', '--alignment-preserved', 'true', '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', str(target), str(source)], env=environment, check=True)
    else:
        shutil.copyfile(source, target)
    print(f'Built {target}')

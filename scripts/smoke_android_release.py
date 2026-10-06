#!/usr/bin/env python3
"""Exercise the optimized APK as a black box on an isolated emulator only."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'io.github.sigmasd.foldercamera'
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial', default='emulator-5554')
args = p.parse_args()
sdk = Path(os.environ['ANDROID_HOME'])
adb = sdk / 'platform-tools/adb'
work = ROOT / '.work/emulator/release-smoke'
work.mkdir(parents=True, exist_ok=True)
temp = ROOT / '.work/tmp'
temp.mkdir(parents=True, exist_ok=True)
env = {**os.environ, 'TMPDIR': str(temp)}

def command(*values, binary=False):
    return subprocess.check_output([str(adb), '-s', args.serial, *values], env=env, text=not binary, timeout=30)

fingerprint = command('shell', 'getprop', 'ro.build.fingerprint')
model = command('shell', 'getprop', 'ro.product.model')
if not args.serial.startswith('emulator-') or not ('generic' in fingerprint or 'sdk' in model.lower()):
    raise SystemExit('Release smoke refuses physical devices')

# The CI key is generated solely for this disposable emulator, never published.
key = work / 'test-only.p12'
if not key.exists():
    subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12', '-storepass', 'test-only', '-keypass', 'test-only', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=Ephemeral release smoke'], env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
apk = work / 'optimized.apk'
tools = sdk / 'build-tools/36.0.0'
subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-pass', 'pass:test-only', '--out', str(apk), str(ROOT / 'android/app/build/outputs/apk/release/app-release-unsigned.apk')], env=env, check=True)
command('install', '-r', str(apk))
command('shell', 'pm', 'grant', PACKAGE, 'android.permission.CAMERA')

def nodes():
    command('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/folder-camera-release-ui.xml')
    xml = command('shell', 'cat', '/sdcard/folder-camera-release-ui.xml')
    (work / 'ui.xml').write_text(xml)
    return list(ET.fromstring(xml).iter('node'))

def find(label, contains=False, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for node in nodes():
            if node.get('enabled') == 'false':
                continue
            values = [node.get('text', ''), node.get('content-desc', '')]
            if any((label.casefold() in value.casefold() if contains else value.casefold() == label.casefold()) for value in values):
                return node
        time.sleep(.5)
    raise AssertionError('Release UI missing: ' + label)

def tap_node(node):
    bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
    assert len(bounds) == 4, node.attrib
    command('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))

def tap(label, contains=False):
    tap_node(find(label, contains))

def fill(value):
    edit = next(node for node in nodes() if node.get('class') == 'android.widget.EditText')
    tap_node(edit)
    command('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(len(edit.get('text', ''))):
        command('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    command('shell', 'input', 'text', value)
    command('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def launch():
    command('shell', 'monkey', '-p', PACKAGE, '-c', 'android.intent.category.LAUNCHER', '1')
    find('Choose a folder')

try:
    launch()
    tap('Settings')
    tap('Choose or regrant base directory')
    tap('Show roots')
    tap('Downloads')
    tap('More options')
    tap('New folder')
    fill('FolderCameraReleaseQA')
    tap('OK')
    tap('USE THIS FOLDER')
    tap('ALLOW')
    find('Choose a folder')
    fill('Projects/Release')
    tap('Start camera')
    tap('Take photo')
    find('1 saved locally', timeout=60)
    (work / 'camera.png').write_bytes(command('exec-out', 'screencap', '-p', binary=True))
    paths = command('shell', 'find', '/sdcard/Download/FolderCameraReleaseQA', '-type', 'f').splitlines()
    photos = [path for path in paths if path.endswith('.jpg')]
    assert len(photos) == 1, photos
    jpeg = command('exec-out', 'cat', photos[0], binary=True)
    assert len(jpeg) > 100 and jpeg.startswith(b'\xff\xd8'), 'No saved JPEG'
    command('shell', 'am', 'force-stop', PACKAGE)
    launch()
    tap('Folders')
    find('Projects')
    tap('Settings')
    find('FolderCameraReleaseQA')
    assert command('exec-out', 'cat', photos[0], binary=True) == jpeg
    print('PASS: optimized release initializes Room/WorkManager, selects a SAF folder, captures a real emulator JPEG, and retains settings/photos across restart')
finally:
    (work / 'logcat.log').write_text(command('logcat', '-d'))

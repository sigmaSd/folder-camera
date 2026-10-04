#!/usr/bin/env python3
"""Run native tests after Android framework services are ready, and retain diagnostics."""
import argparse
import os
from pathlib import Path
import subprocess
import time
parser = argparse.ArgumentParser()
parser.add_argument('--api', required=True)
parser.add_argument('--target', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
work = root/'.work/emulator'
work.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ['ANDROID_HOME'])
manager = sdk/'cmdline-tools/latest/bin/sdkmanager'
avdmanager = sdk/'cmdline-tools/latest/bin/avdmanager'
adb = sdk/'platform-tools/adb'
image = f'system-images;android-{args.api};{args.target};x86_64'
subprocess.run([str(manager), image, 'emulator', 'platform-tools'], check=True)
avd_home = work/'avds'
android_home = work/'android-home'
avd_home.mkdir(exist_ok=True); android_home.mkdir(exist_ok=True)
environment = {**os.environ, 'ANDROID_AVD_HOME': str(avd_home), 'ANDROID_USER_HOME': str(android_home)}
avd_path = avd_home/'folder-camera-tests.avd'
subprocess.run([str(avdmanager), 'create', 'avd', '--force', '--name', 'folder-camera-tests', '--package', image, '--device', 'pixel_7', '--path', str(avd_path)], input='no\n', text=True, check=True, env=environment)
avd = avd_path/'config.ini'
with avd.open('a') as out:
    out.write('\nhw.lcd.width=720\nhw.lcd.height=1640\nhw.lcd.density=320\nhw.ramSize=3072\nhw.camera.back=emulated\n')
subprocess.run([str(adb), 'start-server'], check=True)
log = (work/'emulator.log').open('w')
process = subprocess.Popen([str(sdk/'emulator/emulator'), '-avd', 'folder-camera-tests', '-no-window', '-gpu', 'swiftshader_indirect', '-no-snapshot', '-noaudio', '-no-boot-anim', '-camera-back', 'emulated', '-memory', '3072', '-cores', '2'], stdout=log, stderr=subprocess.STDOUT, env=environment)
def shell(*arguments):
    return subprocess.run([str(adb), '-s', 'emulator-5554', 'shell', *arguments], capture_output=True, text=True)
try:
    deadline = time.monotonic()+600
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Emulator exited before boot with {process.returncode}')
        boot = shell('getprop', 'sys.boot_completed')
        package = shell('service', 'check', 'package')
        input_service = shell('service', 'check', 'input')
        settings = shell('service', 'check', 'settings')
        if boot.stdout.strip() == '1' and all('found' in r.stdout and 'not found' not in r.stdout for r in [package, input_service, settings]):
            print('Android boot property AND package/input/settings services are ready.', flush=True)
            break
        time.sleep(2)
    else:
        raise RuntimeError('Android framework services did not become ready within ten minutes')
    shell('input', 'keyevent', '82')
    for name in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
        shell('settings', 'put', 'global', name, '0')
    shell('settings', 'put', 'system', 'screen_off_timeout', '2147483647')
    result = subprocess.run([str(root/'android/gradlew'), '-p', str(root/'android'), ':app:connectedDebugAndroidTest', '--no-daemon'])
    screenshot_dir = root/'.work/screenshots'
    screenshot_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(adb), 'pull', '/sdcard/Android/data/io.github.sigmasd.foldercamera.debug/files/store-screenshots', str(screenshot_dir)], check=False)
    if result.returncode:
        raise RuntimeError('Native instrumentation tests failed; see uploaded reports')
finally:
    for name, arguments in [('logcat.log',['logcat','-d']),('services.log',['shell','service','list']),('properties.log',['shell','getprop'])]:
        with (work/name).open('w') as out:
            subprocess.run([str(adb), '-s', 'emulator-5554', *arguments], stdout=out, stderr=subprocess.STDOUT)
    subprocess.run([str(adb), '-s', 'emulator-5554', 'emu', 'kill'], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        process.kill()
    log.close()

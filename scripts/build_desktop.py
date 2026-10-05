#!/usr/bin/env python3
"""Build a self-contained receiver package using the locked Deno desktop runtime."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ['x86_64-unknown-linux-gnu', 'aarch64-unknown-linux-gnu', 'x86_64-pc-windows-msvc', 'aarch64-pc-windows-msvc', 'x86_64-apple-darwin', 'aarch64-apple-darwin']
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--target', choices=TARGETS)
p.add_argument('--format', choices=['directory', 'AppImage', 'rpm', 'deb', 'msi', 'app', 'dmg'], default='directory')
p.add_argument('--output', type=Path)
p.add_argument('--backend', choices=['webview','cef'], default='webview')
a = p.parse_args()
if not a.target:
    a.target = subprocess.check_output(['deno','eval','console.log(Deno.build.target)'],text=True).strip()
output = a.output or ROOT/'.work/desktop-dist'/('FolderCameraReceiver-' + a.target + ('.' + a.format if a.format != 'directory' else ''))
output.parent.mkdir(parents=True,exist_ok=True)
work_tmp = ROOT/'.work/tmp'; work_tmp.mkdir(parents=True,exist_ok=True)
environment={**os.environ,'TMPDIR':str(work_tmp)}
commands = 'powershell.exe,explorer.exe,reg.exe' if 'windows' in a.target else 'osascript,open' if 'apple' in a.target else 'zenity,kdialog,xdg-open'
subprocess.run(['deno','desktop','--config',str(ROOT/'receiver/deno.json'),'--frozen','--backend',a.backend,'--target',a.target,'--output',str(output),'--include',str(ROOT/'receiver/desktop/ui'),'--icon',str(ROOT/'receiver/desktop/ui/icon.png'),'--allow-env=HOME,XDG_STATE_HOME,XDG_CONFIG_HOME,LOCALAPPDATA,APPDATA,APPIMAGE,SystemRoot,DENO_SERVE_ADDRESS','--allow-sys=networkInterfaces,homedir','--allow-net','--allow-read','--allow-write','--allow-ffi','--allow-run='+commands,str(ROOT/'receiver/desktop/main.ts')],env=environment,check=True,cwd=ROOT)
print(output)

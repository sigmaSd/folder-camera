#!/usr/bin/env python3
"""Launch the application extracted from its actual installer in an isolated profile."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('installer',type=Path);args=p.parse_args()
installer=args.installer.resolve(); work=ROOT/'.work/installer-smoke';work.mkdir(parents=True,exist_ok=True)
environment={**os.environ,'TMPDIR':str(ROOT/'.work/tmp')}
if installer.suffix=='.AppImage':
    installer.chmod(0o755);environment['APPIMAGE_EXTRACT_AND_RUN']='1'
    subprocess.run([sys.executable,str(ROOT/'scripts/smoke_desktop.py'),str(installer)],env=environment,check=True)
elif installer.suffix=='.msi':
    # The upstream MSI has no AdminExecuteSequence: /a succeeds without files.
    # Exercise a real installation at its normal Program Files destination.
    log=ROOT/'.work/desktop-msi-install.log'
    subprocess.run(['msiexec.exe','/i',str(installer),'/qn','/norestart','/l*v',str(log)],check=True,timeout=60)
    try:
        installed=Path(os.environ['ProgramW6432'])/installer.stem
        launcher=installed/(installer.stem+'.exe')
        if not launcher.is_file():raise SystemExit('Installed MSI launcher missing: '+str(launcher))
        # Hosted runners have administrator write access. Make the app directory
        # read-only to catch renderer caches incorrectly written beside the exe.
        subprocess.run(['icacls.exe',str(installed),'/deny','*S-1-5-11:(OI)(CI)(WD,AD,WEA,WA)'],check=True,timeout=30)
        try:
            subprocess.run([sys.executable,str(ROOT/'scripts/smoke_desktop.py'),str(launcher)],env=environment,check=True)
        finally:
            subprocess.run(['icacls.exe',str(installed),'/remove:d','*S-1-5-11'],check=True,timeout=30)
    finally:
        subprocess.run(['msiexec.exe','/x',str(installer),'/qn','/norestart','/l*v',str(ROOT/'.work/desktop-msi-uninstall.log')],check=True,timeout=60)
elif installer.suffix=='.dmg':
    mount=work/'mount';mount.mkdir(exist_ok=True)
    subprocess.run(['hdiutil','attach','-nobrowse','-readonly','-mountpoint',str(mount),str(installer)],check=True,timeout=60)
    try:
        apps=list(mount.glob('*.app'))
        if len(apps)!=1:raise SystemExit('Could not identify DMG application')
        subprocess.run([sys.executable,str(ROOT/'scripts/smoke_desktop.py'),str(apps[0])],env=environment,check=True)
    finally:subprocess.run(['hdiutil','detach',str(mount)],check=False,timeout=60)
else:raise SystemExit('Unsupported installer format')
print('PASS: application from installer rendered Ready and quit cleanly')

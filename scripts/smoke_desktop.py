#!/usr/bin/env python3
"""Start the packaged GUI in an isolated fixture profile and require its native/TLS smoke result."""
import argparse
import os
from pathlib import Path
import subprocess
import uuid
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('application',type=Path);a=p.parse_args()
app=a.application.resolve()
if not app.exists() and Path(str(app)+'.app').exists(): app=Path(str(app)+'.app')
if app.is_dir():
    candidates=list(app.glob('Contents/MacOS/*')) if (app/'Contents/MacOS').exists() else list(app.glob('*.exe')) if os.name=='nt' else [app/app.name]
    candidates=[path for path in candidates if path.is_file() and path.suffix not in ('.so','.dll','.png')]
    if len(candidates)!=1: raise SystemExit('Cannot identify the native application launcher')
    app=candidates[0]
work=ROOT/'.work/desktop-smoke'/str(uuid.uuid4());work.mkdir(parents=True)
temp=ROOT/'.work/tmp';temp.mkdir(parents=True,exist_ok=True)
command=[str(app),'--state',str(work/'state'),'--root',str(work/'photos'),'--bind','127.0.0.1','--port','19445','--smoke']
result=subprocess.run(command,env={**os.environ,'TMPDIR':str(temp)},stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=60)
log=ROOT/'.work/desktop-native-smoke.log';log.write_text(result.stdout)
if result.returncode or 'DESKTOP SMOKE PASS:' not in result.stdout: raise SystemExit('Packaged GUI smoke failed; see '+str(log)+'\n'+result.stdout[-6000:])
print('PASS: packaged native window/bindings and independent TLS receiver startup')

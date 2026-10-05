#!/usr/bin/env python3
"""Require a packaged native GUI to render Ready and shut down cleanly in a fixture profile."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('application', type=Path)
args = parser.parse_args()
app = args.application.resolve()
if not app.exists() and Path(str(app) + '.app').exists():
    app = Path(str(app) + '.app')
if app.is_dir():
    if (app / 'Contents/MacOS').exists():
        candidates = list(app.glob('Contents/MacOS/*'))
    elif os.name == 'nt':
        candidates = list(app.glob('*.exe'))
    else:
        candidates = [app / app.name]
    candidates = [path for path in candidates if path.is_file() and path.suffix not in ('.so', '.dll', '.dylib', '.png')]
    if len(candidates) != 1:
        raise SystemExit('Cannot identify native launcher: ' + ', '.join(str(path) for path in candidates))
    app = candidates[0]
work = ROOT / '.work/desktop-smoke' / str(uuid.uuid4())
work.mkdir(parents=True)
temp = ROOT / '.work/tmp'
temp.mkdir(parents=True, exist_ok=True)
command = [str(app), '--state', str(work / 'state'), '--root', str(work / 'photos'), '--bind', '127.0.0.1', '--port', '19445', '--smoke']
log = ROOT / '.work/desktop-native-smoke.log'
try:
    result = subprocess.run(command, env={**os.environ, 'TMPDIR': str(temp)}, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
except subprocess.TimeoutExpired as error:
    data = error.output or b''
    output = data.decode(errors='replace') if isinstance(data, bytes) else data
    log.write_text(output)
    raise SystemExit('Native GUI startup timed out; retained diagnostics:\n' + output[-6000:])
log.write_text(result.stdout)
if result.returncode or 'DESKTOP SMOKE PASS:' not in result.stdout:
    if sys.platform == 'darwin':
        reports = Path.home() / 'Library/Logs/DiagnosticReports'
        for report in sorted(reports.glob('*.ips'), key=lambda path: path.stat().st_mtime, reverse=True)[:4]:
            if 'gui-directory' in report.name.lower() or 'folder' in report.name.lower():
                shutil.copy2(report, ROOT / '.work' / report.name)
    raise SystemExit(f'Packaged GUI smoke failed (exit {result.returncode}); see {log}\n' + result.stdout[-6000:])
print('PASS: packaged native GUI rendered Ready, TLS receiver started, and process exited cleanly')

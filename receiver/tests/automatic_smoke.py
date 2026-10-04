"""Actual automatic LAN startup, first-use QR and busy-port fallback; dummy JPEG only."""
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import urllib.request
import uuid

base = Path(__file__).resolve().parents[2]
work_parent = base / '.work'
work_parent.mkdir(exist_ok=True)
work = Path(tempfile.mkdtemp(prefix='automatic-receiver-', dir=work_parent))
(work / 'tmp').mkdir()
state = work / 'state-home' / 'folder-camera'
state.mkdir(parents=True)
photos = work / 'photos'
env = {**os.environ, 'TMPDIR': str(work / 'tmp'), 'XDG_STATE_HOME': str(state.parent)}
lan_module = (base / 'receiver/src/lan.ts').as_uri()
info = json.loads(subprocess.check_output(['deno', 'eval', '--cached-only',
    f'import {{ detectLanAddress }} from {json.dumps(lan_module)}; console.log(JSON.stringify(await detectLanAddress()));'],
    cwd=base / 'receiver', env=env, text=True))
assert ipaddress.ip_address(info['address']).is_private
assert info['address'] not in ('127.0.0.1', '::1')
blocker = socket.socket(socket.AF_INET6 if ':' in info['address'] else socket.AF_INET)
blocker.bind((info['address'], 0))
blocker.listen()
occupied = blocker.getsockname()[1]
(state / 'receiver-config.json').write_text(json.dumps({'version': 1, 'root': str(photos), 'port': occupied, 'bind': 'auto'}))
cmd = ['deno', 'run', '--cached-only', '--frozen', '--allow-env=HOME,XDG_STATE_HOME,LOCALAPPDATA',
       '--allow-sys=networkInterfaces,homedir', '--allow-net', '--allow-read', '--allow-write', '--allow-run=openssl',
       str(base / 'receiver/src/main.ts')]

def start(first=False):
    proc = subprocess.Popen(cmd, cwd=base / 'receiver', env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    address = None
    payload = None
    while True:
        line = proc.stdout.readline()
        if not line:
            raise RuntimeError('Receiver failed to start: ' + proc.stderr.read())
        if line.startswith('Address: '):
            address = line.split()[1]
        if first and line.startswith('{"version"'):
            payload = json.loads(line)
            break
        if not first and line.startswith('Pairing: '):
            break
    threading.Thread(target=lambda: list(proc.stdout), daemon=True).start()
    return proc, address, payload

def stop(proc):
    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
    assert proc.returncode in (0, -15), proc.stderr.read()

def request(address, method, path, body=None, token=None, metadata=None):
    ctx = ssl.create_default_context(cafile=str(state / 'certificate.pem'))
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ctx))
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if metadata:
        import base64
        headers['Content-Type'] = 'image/jpeg'
        headers['X-FolderCamera-Metadata'] = base64.urlsafe_b64encode(json.dumps(metadata, ensure_ascii=False).encode()).decode().rstrip('=')
    elif body:
        headers['Content-Type'] = 'application/json'
    with opener.open(urllib.request.Request(address + path, data=body, headers=headers, method=method), timeout=10) as response:
        return json.load(response)

proc = None
try:
    proc, address, pairing = start(first=True)
    assert pairing['endpoint'] == address
    assert pairing['endpoint'].rsplit(':', 1)[1] != str(occupied)
    assert info['address'] in address
    saved = json.loads((state / 'receiver-config.json').read_text())
    assert saved['bind'] == 'auto' and saved['root'] == str(photos)
    assert saved['port'] == int(address.rsplit(':', 1)[1])
    credential = request(address, 'POST', '/v1/pair', json.dumps({**pairing, 'deviceName': 'Automatic CLI test'}).encode())
    photo_id = str(uuid.uuid4())
    photo = (base / 'receiver/tests/fixtures/photo.jpg').read_bytes()
    metadata = {'version': 1, 'photoId': photo_id, 'relativePath': 'Automation/صور', 'filename': photo_id + '.jpg',
                'mimeType': 'image/jpeg', 'byteSize': len(photo), 'sha256': hashlib.sha256(photo).hexdigest()}
    receipt = request(address, 'PUT', '/v1/photos/' + photo_id, photo, credential['token'], metadata)
    assert (photos / metadata['relativePath'] / metadata['filename']).read_bytes() == photo
    stop(proc)
    proc = None
    blocker.close()
    proc, restarted_address, payload = start()
    assert restarted_address == address and payload is None
    assert request(address, 'GET', '/v1/photos/' + photo_id, token=credential['token']) == receipt
finally:
    blocker.close()
    if proc is not None:
        stop(proc)
print('PASS: actual automatic LAN detection, no startup flags, first-use QR, occupied-port fallback, remembered destination/port, verified HTTPS upload and restart. Dummy JPEG only.')

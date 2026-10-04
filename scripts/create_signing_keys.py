#!/usr/bin/env python3
"""Create durable local signing credentials once; never transmit or print private values."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

parser = argparse.ArgumentParser()
parser.add_argument('--directory', type=Path, default=Path.home()/'.local/share/folder-camera/signing')
args = parser.parse_args()
folder = args.directory
folder.mkdir(parents=True, exist_ok=True, mode=0o700)
folder.chmod(0o700)
credentials_path = folder/'credentials.json'
if credentials_path.exists():
    print('Existing signing credentials retained:', credentials_path)
    raise SystemExit(0)
if any(folder.glob('*.p12')):
    raise SystemExit('Signing keys already exist without credentials metadata; restore metadata instead of generating replacements.')
credentials = {}
for role in ['app', 'upload']:
    password = secrets.token_urlsafe(48)
    keystore = folder/f'{role}.p12'
    environment = {**os.environ, 'FOLDER_CAMERA_GENERATION_PASSWORD': password}
    result = subprocess.run(['keytool', '-genkeypair', '-storetype', 'PKCS12', '-keystore', str(keystore), '-storepass:env', 'FOLDER_CAMERA_GENERATION_PASSWORD', '-keypass:env', 'FOLDER_CAMERA_GENERATION_PASSWORD', '-alias', role, '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA', '-validity', '10000', '-dname', f'CN=Folder Camera {role} signing, O=sigmasd, C=TN', '-noprompt'], env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise SystemExit(f'keytool failed generating {role}; inspect local toolchain, no credential values printed')
    keystore.chmod(0o600)
    certificate = folder/f'{role}-certificate.pem'
    subprocess.run(['keytool', '-exportcert', '-rfc', '-keystore', str(keystore), '-storepass:env', 'FOLDER_CAMERA_GENERATION_PASSWORD', '-alias', role, '-file', str(certificate)], env=environment, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    credentials[role] = {'keystore': str(keystore), 'password': password, 'alias': role}
fd = os.open(credentials_path, os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as out:
    json.dump(credentials, out, indent=2)
recovery_password = secrets.token_urlsafe(48)
salt = secrets.token_bytes(16)
nonce = secrets.token_bytes(12)
key = Scrypt(salt=salt, length=32, n=2**15, r=8, p=1).derive(recovery_password.encode())
payload = json.dumps({'credentials': credentials, 'keystores': {role: base64.b64encode(Path(item['keystore']).read_bytes()).decode() for role, item in credentials.items()}}).encode()
cipher = AESGCM(key).encrypt(nonce, payload, b'folder-camera-signing-backup-v1')
assert AESGCM(key).decrypt(nonce, cipher, b'folder-camera-signing-backup-v1') == payload
for path, content in [
    (folder/'recovery-password.txt', recovery_password),
    (folder/'signing-backup.fcbackup', json.dumps({'version': 1, 'salt': base64.b64encode(salt).decode(), 'nonce': base64.b64encode(nonce).decode(), 'ciphertext': base64.b64encode(cipher).decode()}))]:
    fd = os.open(path, os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(content+'\n')
print('Created app and upload keys outside the repository:', folder)
print('AES-GCM encrypted recovery backup was decrypted and verified in memory.')
print('Owner must copy the encrypted backup and recovery password to separate safe offline locations.')

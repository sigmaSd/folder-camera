# Development fixture only. First run deno cache --frozen src/main.ts.
import os,subprocess,json,ssl,urllib.request,urllib.error,base64,hashlib,threading,time
from pathlib import Path
base=Path(__file__).resolve().parents[2]
work=base/'.work/cli-smoke'; work.mkdir(exist_ok=True)
(base/'.work/tmp').mkdir(parents=True,exist_ok=True)
state=work/'state-home'/'folder-camera'
env={**os.environ,'TMPDIR':str(base/'.work/tmp'),'XDG_STATE_HOME':str(work/'state-home')}
cmd=['deno','run','--cached-only','--frozen','--allow-env=HOME,XDG_STATE_HOME,LOCALAPPDATA','--allow-sys=networkInterfaces,homedir','--allow-net=127.0.0.1','--allow-read','--allow-write','--allow-run=openssl',str(base/'receiver/src/main.ts'),'--root',str(work/'photos'),'--bind','127.0.0.1','--port','19443']
def start(pair=False, remembered=False):
 startup=cmd[:cmd.index(str(base/'receiver/src/main.ts'))+1] if remembered else cmd
 p=subprocess.Popen(startup+(['--pair'] if pair else []),cwd=base/'receiver',env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 payload=None
 while True:
  line=p.stdout.readline()
  if not line: raise RuntimeError('CLI did not start: '+p.stderr.read())
  if pair and line.startswith('{"version"'): payload=json.loads(line); break
  if not pair and line.startswith('Pairing:'): break
 threading.Thread(target=lambda:list(p.stdout),daemon=True).start()
 return p,payload
def stop(p):
 p.terminate()
 try:p.wait(timeout=10)
 except subprocess.TimeoutExpired:p.kill();p.wait()
 assert p.returncode in (0,-15),p.stderr.read()
def request(method,path,data=None,token=None,meta=None):
 ctx=ssl.create_default_context(cafile=str(state/'certificate.pem'))
 headers={}
 if token:headers['Authorization']='Bearer '+token
 if meta:headers.update({'Content-Type':'image/jpeg','X-FolderCamera-Metadata':base64.urlsafe_b64encode(json.dumps(meta,ensure_ascii=False).encode()).decode().rstrip('=')})
 elif data:headers['Content-Type']='application/json'
 req=urllib.request.Request('https://127.0.0.1:19443'+path,data=data,headers=headers,method=method)
 with urllib.request.urlopen(req,context=ctx,timeout=10) as r:return json.load(r)
p,payload=start(True)
try:
 pair=request('POST','/v1/pair',json.dumps({**payload,'deviceName':'CLI smoke'}).encode())
 token=pair['token'];assert request('GET','/v1/health',token=token)['receiverId']==payload['receiverId']
 import uuid
 photo=(base/'receiver/tests/fixtures/photo.jpg').read_bytes();id=str(uuid.uuid4())
 meta={'version':1,'photoId':id,'relativePath':'Été/صور/Job A','filename':id+'.jpg','mimeType':'image/jpeg','byteSize':len(photo),'sha256':hashlib.sha256(photo).hexdigest()}
 a=request('PUT','/v1/photos/'+id,photo,token,meta);b=request('PUT','/v1/photos/'+id,photo,token,meta);assert a==b
 assert (work/'photos'/meta['relativePath']/meta['filename']).read_bytes()==photo
 management=subprocess.run(['deno','run','--cached-only','--frozen','--allow-env=HOME,XDG_STATE_HOME,LOCALAPPDATA','--allow-read','--allow-write',str(base/'receiver/src/main.ts'),'--state',str(state),'--list-devices'],cwd=base/'receiver',env=env,capture_output=True,text=True)
 assert management.returncode==1 and 'in use' in management.stderr
finally:stop(p)
p,_=start(remembered=True)
try:assert request('GET','/v1/photos/'+id,token=token)==a
finally:stop(p)
revoke=subprocess.run(['deno','run','--cached-only','--frozen','--allow-env=HOME,XDG_STATE_HOME,LOCALAPPDATA','--allow-read','--allow-write',str(base/'receiver/src/main.ts'),'--revoke',pair['deviceId']],cwd=base/'receiver',env=env,capture_output=True,text=True)
assert revoke.returncode==0,revoke.stderr
p,_=start(remembered=True)
try:
 try:request('GET','/v1/health',token=token);raise AssertionError('revocation failed')
 except urllib.error.HTTPError as e:assert e.code==401
finally:stop(p)
# Legacy profiles did not record the root; guessing must not redirect their uploaded photos.
config_path=state/'receiver-config.json'
saved_configuration=config_path.read_text()
config_path.unlink()
try:
 legacy=subprocess.run(cmd[:cmd.index(str(base/'receiver/src/main.ts'))+1],cwd=base/'receiver',env=env,capture_output=True,text=True,timeout=10)
 assert legacy.returncode==1 and 'Start once with --root' in legacy.stderr
 assert not config_path.exists()
 assert (work/'photos'/meta['relativePath']/meta['filename']).read_bytes()==photo
finally:
 config_path.write_text(saved_configuration)
 config_path.chmod(0o600)
print('CLI smoke PASS: remembered root/bind/port with no flags, default XDG state, offline cached startup, QR generation, HTTPS pairing, Unicode upload, idempotent retry, durable restart, state lock, revocation, safe legacy-root migration. Only development fixture used.')

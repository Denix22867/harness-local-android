#!/usr/bin/env python3
"""Bundle official Ubuntu ARM64 SSH clients and only their missing libraries."""
from pathlib import Path
import lzma, subprocess, hashlib, json, zipfile, shutil
from concurrent.futures import ThreadPoolExecutor
BASE=Path(__file__).resolve().parents[1];downloads=BASE/'downloads/ssh';downloads.mkdir(parents=True,exist_ok=True)
packages={}
for f in ['noble-main-arm64.xz','noble-universe-arm64.xz','noble-updates-main-arm64.xz']:
 for paragraph in lzma.open(BASE/'downloads'/f,'rt').read().split('\n\n'):
  row=dict(line.split(': ',1) for line in paragraph.splitlines() if ': ' in line and not line.startswith(' '))
  if row.get('Package'):packages[row['Package']]=row
names=['openssh-client','libedit2','libbsd0','libfido2-1','libcbor0.10']
# sshpass enables noninteractive password authentication without passwords in argv.
url='https://ports.ubuntu.com/ubuntu-ports/'
def fetch(name):
 row=packages[name];dest=downloads/Path(row['Filename']).name
 subprocess.run(['curl','-sS','--fail','--max-time','60',url+row['Filename'],'-o',str(dest)],check=True)
 digest=hashlib.sha256(dest.read_bytes()).hexdigest()
 if row.get('SHA256'):assert digest==row['SHA256'],name
 row['SHA256']=digest;return dest
with ThreadPoolExecutor(max_workers=6) as pool:debs=list(pool.map(fetch,names+['sshpass']))
stage=BASE/'build/ssh-payload';shutil.rmtree(stage,ignore_errors=True);stage.mkdir(parents=True)
for deb in debs:subprocess.run(['dpkg-deb','-x',str(deb),str(stage)],check=True)
assets=BASE/'app/src/main/assets'
with zipfile.ZipFile(assets/'ssh-tools-v1.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
 for f in sorted(stage.rglob('*')):
  if f.is_dir():continue
  name=f.relative_to(stage).as_posix()
  if name.startswith(('usr/bin/','usr/lib/aarch64-linux-gnu/')) or name=='etc/ssh/ssh_config' or name.endswith('/copyright'):
   info=zipfile.ZipInfo(name);info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=((0o100755 if name.startswith('usr/bin/') else 0o100644)<<16)
   z.writestr(info,f.read_bytes())
(assets/'ssh-tools-packages.json').write_text(json.dumps([{'name':n,'version':packages[n]['Version'],'url':url+packages[n]['Filename'],'sha256':packages[n]['SHA256']} for n in names+['sshpass']],indent=2))
print('SSH payload bytes:',(assets/'ssh-tools-v1.zip').stat().st_size)

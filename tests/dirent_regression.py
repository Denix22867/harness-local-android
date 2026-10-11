import os, stat, subprocess, json
from pathlib import Path
root=Path(os.environ['DIRENT_TEST_ROOT']);root.mkdir(parents=True,exist_ok=True)
for name in ['aaa_bash.txt','ccc_edit.txt','ddd_cp.txt']:(root/name).write_text('dirent-regression-marker\n')
for name in ['bbb_write.txt','eee_renamed.txt','tools_check.py']:
 target=root/name;temporary=root/(name+'.stage')
 for p in [target,temporary]:
  if p.exists() or p.is_symlink():p.unlink()
 temporary.write_text('dirent-regression-marker\n');os.link(temporary,target);temporary.unlink()
os.rename(root/'eee_renamed.txt',root/'eee_before_rename.txt');os.rename(root/'eee_before_rename.txt',root/'eee_renamed.txt')
external=root.parent/'outside';external.mkdir(exist_ok=True);(external/'outside.txt').write_text('dirent-regression-marker\n')
for name,target in [('real-file-link.txt',external/'outside.txt'),('real-dir-link',external),('loop-link',root),('broken-link',root/'absent')]:
 p=root/name
 if p.exists() or p.is_symlink():p.unlink()
 p.symlink_to(target)
rows=[{'name':e.name,'is_symlink':e.is_symlink(),'lstat_regular':stat.S_ISREG(os.lstat(e.path).st_mode)} for e in os.scandir(root)]
files=subprocess.check_output(['rg','--files','--glob=*','--sort=modified','--no-ignore','--hidden',str(root)],text=True).splitlines()
matches=subprocess.check_output(['rg','--files-with-matches','--no-ignore','--hidden','dirent-regression-marker',str(root)],text=True).splitlines()
print(json.dumps({'dirents':rows,'glob':sorted(Path(p).name for p in files),'grep':sorted(Path(p).name for p in matches)},indent=2))
expected={'aaa_bash.txt','ccc_edit.txt','ddd_cp.txt','bbb_write.txt','eee_renamed.txt','tools_check.py'}
if os.environ.get('EXPECT_FIXED')=='1':
 assert {Path(p).name for p in files}==expected
 assert {Path(p).name for p in matches}==expected
 for row in rows:
  assert row['is_symlink']==(row['name'] not in expected),row
 print('PASS: glob, directory grep, renamed writes, genuine/broken/cyclic symlinks')

#!/usr/bin/env python3
"""Rebuild Termux PRoot with the directory-entry hardlink fix (Android NDK r27d)."""
from pathlib import Path
import os, subprocess, tarfile, shutil
BASE=Path(__file__).resolve().parents[1]
ndk=Path(os.environ.get('DSH_ANDROID_NDK',str(BASE/'tools/android-ndk-r27d')))
llvm=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
termux=Path(os.environ.get('DSH_TERMUX_PREFIX', str(BASE/'native-deps/termux')))
source=BASE/'build/proot-android-source'
if source.exists():shutil.rmtree(source)
source.mkdir(parents=True)
with tarfile.open(BASE/'third-party-sources/proot-v5.1.107.96.tar.gz') as archive:archive.extractall(source,filter='data')
root=source/'proot-5.1.107.96'
subprocess.run(['patch','-p1','--batch','-i',str(BASE/'third-party-sources/proot-dirent-types.patch')],cwd=root,check=True)
# GNU awk is optional; calculate the loader offset from ELF symbols using Python.
(root/'src/loader/loader-info.py').write_text("import subprocess,sys\nrows=subprocess.check_output(['readelf','-sW',sys.argv[1]],text=True).splitlines()\nsyms={r.split()[-1]:int(r.split()[1],16) for r in rows if len(r.split())>=8 and r.split()[-1] in ['_start','pokedata_workaround']}\nprint('#include <unistd.h>')\nprint('const ssize_t offset_to_pokedata_workaround='+str(syms['pokedata_workaround']-syms['_start'])+';')\n")
makefile=root/'src/GNUmakefile'
makefile.write_text(makefile.read_text().replace('readelf -s $< | awk -f loader/loader-info.awk > $@','python3 loader/loader-info.py $< > $@'))
wrapper=BASE/'build/ndk-proot-cc.py' 
wrapper.write_text("#!/usr/bin/env python3\nimport os,sys\nfrom pathlib import Path\na=sys.argv[1:]\narch='armv7a-linux-androideabi28-clang' if '-m32' in a else 'aarch64-linux-android28-clang'\na=[v for v in a if v!='-m32']\nif any(v.endswith('.c') and '/loader/' not in v for v in a):a=['-include','string.h']+a\nif any('/loader/' in v and v.endswith('.c') for v in a):a+=['-fno-stack-protector']\nif '-static' in a:a+=['-Wl,-z,max-page-size=16384']\ncc="+repr(str(llvm))+"+'/'+arch\nos.execv(cc,[cc]+a)\n")
wrapper.chmod(0o755)
subprocess.run(['make','-C',str(root/'src'),'-j3','CC='+str(wrapper),
 'STRIP='+str(llvm/'llvm-strip'),'OBJCOPY='+str(llvm/'llvm-objcopy'),'OBJDUMP='+str(llvm/'llvm-objdump'),
 'PROOT_WITH_LIBANDROID_SHMEM=true','PROOT_UNBUNDLE_LOADER=/data/data/com.termux/files/usr/libexec/proot',
 'CPPFLAGS= -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -DARG_MAX=131072 -DVERSION=\\\"5.1.107.96-android-dirent1\\\" -I. -I'+str(termux/'include'),
 'CFLAGS=-Wall -Wextra -O2 -fPIE -fstack-protector-strong -DWITH_LIBANDROID_SHMEM -DPROOT_UNBUNDLE_LOADER=\\\"/data/data/com.termux/files/usr/libexec/proot\\\"',
 'LDFLAGS=-L'+str(termux/'lib')+' -ltalloc -landroid-shmem -Wl,-z,noexecstack -Wl,-z,max-page-size=16384 -Wl,-z,relro -Wl,-z,now'],check=True)
output=BASE/'app/src/main/jniLibs/arm64-v8a/libproot.so'
shutil.copy2(root/'src/proot',output)
subprocess.run([str(llvm/'llvm-strip'),'--strip-unneeded',str(output)],check=True)
loader=output.parent/'libproot-loader.so'
shutil.copy2(root/'src/loader/loader',loader)
subprocess.run([str(llvm/'llvm-strip'),'--strip-unneeded',str(loader)],check=True)
print('Patched Android PRoot:',output)

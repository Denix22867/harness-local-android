#!/usr/bin/env python3
"""Build an isolated emulator carrier with browser RPC but no ARM64 runtime."""
from pathlib import Path
import os, shutil, subprocess, sys

base = Path(__file__).resolve().parents[1]
test = base / 'build/browser-ui-test-project'
shutil.rmtree(test, ignore_errors=True)
shutil.copytree(base / 'app', test / 'app', ignore=shutil.ignore_patterns('jniLibs', 'runtime.zip'))
(test / 'scripts').mkdir(parents=True)
shutil.copy2(base / 'scripts/build_apk.py', test / 'scripts/build_apk.py')
source = test / 'app/src/main/java/org/dsh/mobile/RuntimeService.java'
text = source.read_text()
guard = 'if (!Arrays.asList(android.os.Build.SUPPORTED_ABIS).contains("arm64-v8a")) throw new IOException("Для локального ядра нужен телефон ARM64. ABI: " + Arrays.toString(android.os.Build.SUPPORTED_ABIS));'
assert text.count(guard) == 1, 'Production ABI guard changed; review the test carrier'
source.write_text(text.replace(guard, 'if (!Arrays.asList(android.os.Build.SUPPORTED_ABIS).contains("arm64-v8a")) { status = "Проверка браузера на эмуляторе"; return; }'))
env = dict(os.environ)
env['DSH_SIGNING_KEYSTORE'] = env.get('DSH_BROWSER_TEST_KEYSTORE', str(base / 'tools/browser-test.keystore'))
subprocess.run([sys.executable, str(test / 'scripts/build_apk.py')], env=env, check=True)
print('TEST ONLY: browser carrier without the Linux runtime:', test / 'dist/Harness-Local-Mobile.apk')

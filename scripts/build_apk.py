#!/usr/bin/env python3
"""Build the Android carrier with Android SDK 35, ECJ and D8; no Gradle required."""
from pathlib import Path
import subprocess
import zipfile
import hashlib
import os
import shutil

BASE = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('DSH_ANDROID_SDK', str(BASE / 'tools/sdk')))
TOOLS = SDK / 'build-tools/35.0.0'
ANDROID = SDK / 'platforms/android-35/android.jar'
BUILD = BASE / 'build'
APP = BASE / 'app/src/main'
OUT = BASE / 'dist'
for generated in [BUILD / 'classes', BUILD / 'dex']:
    shutil.rmtree(generated, ignore_errors=True)
for path in [BUILD / 'classes', BUILD / 'dex', OUT]: path.mkdir(parents=True, exist_ok=True)

import json
manifest = json.loads((APP / 'assets/runtime-manifest.json').read_text())
spec = json.loads((APP / 'assets/runtime-download.json').read_text())
assert len(spec['sha256']) == 64 and spec['url'].startswith('https://')
if (APP / 'assets/runtime.zip').exists():
    with zipfile.ZipFile(APP / 'assets/runtime.zip') as runtime:
        assert runtime.testzip() is None, 'Runtime archive has a damaged entry'
        archived = set(runtime.namelist())
        expected = {entry['path'] for entry in manifest if 'link' not in entry}
        assert {name.rstrip('/') for name in archived} == expected, 'Runtime manifest does not match archive'
        assert not any(name.startswith('root/.dsh/') for name in archived), 'Test profile leaked into runtime'
        with runtime.open('usr/local/bin/node') as executable:
            header = executable.read(64)
            assert header[:4] == b'\x7fELF' and int.from_bytes(header[18:20], 'little') == 183, 'Node must be ARM64 ELF'
    print('Verified runtime ZIP, manifest, clean home and ARM64 Node', flush=True)

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

run(TOOLS / 'aapt2', 'compile', '--dir', APP / 'res', '-o', BUILD / 'resources.zip')
run(TOOLS / 'aapt2', 'link', '-o', BUILD / 'resources.apk', '--manifest', APP / 'AndroidManifest.xml',
    '-I', ANDROID, '--java', BUILD / 'generated', '--version-code', '13', '--version-name', '0.4.7-private-browser', BUILD / 'resources.zip')
sources = sorted((APP / 'java').rglob('*.java')) + sorted((BUILD / 'generated').rglob('*.java'))
run('java', '-jar', Path(os.environ.get('DSH_ECJ_JAR', str(BASE / 'tools/ecj.jar'))), '-1.8', '-nowarn', '-classpath', ANDROID, '-d', BUILD / 'classes', *sources)
run(TOOLS / 'd8', '--min-api', '28', '--lib', ANDROID, '--output', BUILD / 'dex', *sorted((BUILD / 'classes').rglob('*.class')))
unsigned = BUILD / 'unsigned.apk'
with zipfile.ZipFile(BUILD / 'resources.apk') as source, zipfile.ZipFile(unsigned, 'w') as target:
    for entry in source.infolist(): target.writestr(entry, source.read(entry.filename))
    for path in (BUILD / 'dex').glob('*.dex'): target.write(path, path.name, compress_type=zipfile.ZIP_DEFLATED)
    for path in (APP / 'assets').iterdir():
        if path.name != 'runtime.zip': target.write(path, 'assets/' + path.name, compress_type=zipfile.ZIP_DEFLATED)
    for path in (APP / 'jniLibs/arm64-v8a').glob('*.so'): target.write(path, 'lib/arm64-v8a/' + path.name, compress_type=zipfile.ZIP_DEFLATED)
aligned = BUILD / 'aligned.apk'
run(TOOLS / 'zipalign', '-f', '4', unsigned, aligned)
key = Path(os.environ.get('DSH_SIGNING_KEYSTORE', str(BASE / 'tools/preview.keystore')))
key.parent.mkdir(parents=True, exist_ok=True)
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'preview', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Harness Local Preview')
apk = OUT / 'Harness-Local-Mobile.apk'
run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--out', apk, aligned)
run(TOOLS / 'apksigner', 'verify', '--verbose', apk)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(OUT / 'SHA256SUMS').write_text(digest + '  ' + apk.name + '\n')
print(f'APK: {apk}\nBytes: {apk.stat().st_size}\nSHA256: {digest}')

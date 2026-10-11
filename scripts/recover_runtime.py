#!/usr/bin/env python3
"""Recover the exact binary runtime and native files from an existing APK."""
from pathlib import Path
import sys
import zipfile

base = Path(__file__).resolve().parents[1]
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in ['assets/runtime.zip', 'assets/runtime-manifest.json', 'assets/third-party-sources.zip', 'assets/THIRD_PARTY.md', 'assets/runtime-download.json']:
        if name not in apk.namelist(): continue
        dest = base / 'app/src/main' / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(apk.read(name))
    for entry in apk.namelist():
        if entry.startswith('lib/arm64-v8a/') and entry.endswith('.so'):
            dest = base / 'app/src/main/jniLibs/arm64-v8a' / Path(entry).name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(apk.read(entry))
print('Recovered available assets and native libraries. The bootstrap downloads its runtime. Run scripts/build_apk.py after installing the SDK and ECJ.')

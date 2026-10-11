#!/usr/bin/env python3
"""Pack regular files separately from symlinks for safe Android installation."""
from pathlib import Path
import json
import os
import stat
import zipfile

BASE = Path(__file__).resolve().parents[1]
ROOT = BASE / 'runtime/rootfs'
ASSETS = BASE / 'app/src/main/assets'
ASSETS.mkdir(parents=True, exist_ok=True)

entries = []
total = 0
with zipfile.ZipFile(ASSETS / 'runtime.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as archive:
    for parent, dirs, files in os.walk(ROOT, followlinks=False):
        for name in sorted(dirs + files):
            path = Path(parent) / name
            relative = path.relative_to(ROOT).as_posix()
            info = path.lstat()
            mode = stat.S_IMODE(info.st_mode) & 0o777
            if stat.S_ISLNK(info.st_mode):
                entries.append({'path': relative, 'link': os.readlink(path)})
            elif stat.S_ISDIR(info.st_mode):
                archive.write(path, relative + '/')
                entries.append({'path': relative, 'mode': mode | 0o700})
            elif stat.S_ISREG(info.st_mode):
                archive.write(path, relative)
                entries.append({'path': relative, 'mode': mode | 0o600})
                total += info.st_size
            else:
                raise RuntimeError(f'Unsupported runtime entry: {relative}')
            if len(entries) % 10000 == 0:
                print(f'Packed {len(entries)} entries', flush=True)
(ASSETS / 'runtime-manifest.json').write_text(json.dumps(entries, separators=(',', ':')))
print(json.dumps({'entries': len(entries), 'expanded_bytes': total, 'zip_bytes': (ASSETS / 'runtime.zip').stat().st_size}))

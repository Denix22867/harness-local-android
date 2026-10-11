#!/usr/bin/env python3
"""Restore the published core for development; do not execute it."""
from pathlib import Path
import hashlib
import json
import shutil
import tempfile
import urllib.request
import zipfile

base = Path(__file__).resolve().parents[1]
assets = base / 'app/src/main/assets'
spec = json.loads((assets / 'runtime-download.json').read_text())
downloads = base / 'downloads'
downloads.mkdir(exist_ok=True)
archive = downloads / 'published-runtime.zip'
def valid():
    if not archive.exists() or archive.stat().st_size != spec['bytes']:
        return False
    digest = hashlib.sha256()
    with archive.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest() == spec['sha256']
if not valid():
    partial = archive.with_suffix('.part')
    with urllib.request.urlopen(spec['url'], timeout=60) as response, partial.open('wb') as output:
        shutil.copyfileobj(response, output)
    partial.replace(archive)
if not valid():
    raise RuntimeError('Runtime size or SHA-256 mismatch')
with tempfile.TemporaryDirectory(dir=downloads) as directory:
    current = archive
    for index, member in enumerate(spec.get('nested', [])):
        target = Path(directory) / str(index)
        with zipfile.ZipFile(current) as layer, layer.open(member) as source, target.open('wb') as output:
            shutil.copyfileobj(source, output)
        current = target
    root = base / 'runtime/rootfs'
    if root.exists():
        raise RuntimeError('runtime/rootfs already exists; move it aside before restoring')
    root.mkdir(parents=True)
    with zipfile.ZipFile(current) as payload:
        for entry in payload.infolist():
            name = Path(entry.filename)
            if name.is_absolute() or '..' in name.parts:
                raise RuntimeError('Unsafe runtime member')
        payload.extractall(root)
    for entry in json.loads((assets / 'runtime-manifest.json').read_text()):
        path = root / entry['path']
        if 'link' in entry:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.symlink_to(entry['link'])
        else:
            path.chmod(entry['mode'])
print('Restored verified runtime:', root)


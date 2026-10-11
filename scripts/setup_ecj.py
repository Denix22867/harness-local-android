#!/usr/bin/env python3
"""Download the pinned public compiler used by build_apk.py."""
from pathlib import Path
import hashlib
import urllib.request

base = Path(__file__).resolve().parents[1]
target = base / 'tools/ecj.jar'
expected = '29f6d3918ee02db4400c103bc25dd90a22491c3a395867d9393070cb96a7dd29'
url = 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.42.0/ecj-3.42.0.jar'
if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
    target.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise RuntimeError('ECJ SHA-256 mismatch')
    target.write_bytes(data)
print('Verified ECJ 3.42.0:', target)


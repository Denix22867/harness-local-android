const fs = require('fs');
const { execFileSync } = require('child_process');
const { createRequire } = require('module');
const req = createRequire('/opt/dsh/package.json');
const assert = require('assert');

(async () => {
  assert.equal(process.arch, 'arm64');
  assert.equal(process.platform, 'linux');
  const file = '/workspace/.android-smoke';
  fs.writeFileSync(file, 'local files work\n');
  assert.equal(fs.readFileSync(file, 'utf8'), 'local files work\n');
  const { tryLockExclusive } = await import(req.resolve('@deepseek-ai/node-addon-system/flock'));
  const fd = fs.openSync(file, 'r+');
  await tryLockExclusive(fd);
  fs.closeSync(fd);
  fs.unlinkSync(file);
  console.log('PASS ARM64 file write/read and native flock');
  for (const [command, args] of [['bash', ['-lc', 'printf bash-ok']], ['git', ['--version']], ['python3', ['--version']], ['rg', ['--version']], ['pnpm', ['--version']]]) {
    console.log('PASS', command, execFileSync(command, args, { encoding: 'utf8', timeout: 60000 }).split('\n')[0]);
  }
  const koffi = req('koffi');
  assert.ok(koffi.load);
  console.log('PASS ARM64 koffi native module');
  const pty = req('node-pty');
  await new Promise((resolve, reject) => {
    const terminal = pty.spawn('/bin/bash', ['-lc', 'printf pty-ok'], { name: 'xterm', cwd: '/workspace', env: process.env });
    let data = '';
    const timeout = setTimeout(() => { terminal.kill(); reject(new Error('PTY timed out')); }, 15000);
    terminal.onData(chunk => { data += chunk; });
    terminal.onExit(({ exitCode }) => {
      clearTimeout(timeout);
      try { assert.equal(exitCode, 0); assert.ok(data.includes('pty-ok')); console.log('PASS ARM64 PTY bash execution'); resolve(); }
      catch (error) { reject(error); }
    });
  });
})().catch(error => { console.error(error.stack); process.exitCode = 1; });

const { chromium } = require('../tools/browser/node_modules/playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const base = path.resolve(__dirname, '..');
  const match = fs.readFileSync(path.join(base, 'build/guest-web.log'), 'utf8').match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s]+/);
  if (!match) throw new Error('Guest web server is not ready');
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const context = await browser.newContext({ viewport: { width: 412, height: 892 }, isMobile: true, hasTouch: true });
  const page = await context.newPage();
  const errors = [];
  const failures = [];
  const remoteErrors = [];
  page.on('websocket', socket => socket.on('framereceived', frame => {
    const data = String(frame.payload);
    if (data.includes('"error"')) remoteErrors.push(data.slice(0, 2500));
  }));
  page.on('pageerror', error => errors.push(error.message));
  page.on('requestfailed', request => failures.push({ url: request.url().replace(/token=[^&]+/, 'token=[redacted]'), error: request.failure().errorText }));
  await page.goto(match[0], { waitUntil: 'networkidle', timeout: 60000 });
  const notice = page.getByRole('button', { name: 'Continue', exact: true });
  if (await notice.count()) await notice.click();
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(base, 'build/mobile-web.png'), fullPage: true });
  const text = await page.locator('body').innerText();
  const report = { title: await page.title(), body: text.slice(0, 5000), errors, failures, remoteErrors, horizontalOverflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth) };
  fs.writeFileSync(path.join(base, 'build/browser-smoke.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
  if (errors.length || failures.length) process.exitCode = 1;
})().catch(error => { console.error(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')); process.exitCode = 1; });

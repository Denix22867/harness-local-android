const {chromium}=require('../tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
const url=fs.readFileSync(require('path').join(__dirname, '..', 'build/account-web.log'),'utf8').match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s]+/)[0];
const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:412,height:892},isMobile:true,hasTouch:true});
const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(url,{waitUntil:'networkidle',timeout:60000});
for(const name of ['Continue','Configure later']){const button=page.getByRole('button',{name,exact:true});if(await button.count())await button.click();}
await page.waitForTimeout(1000);
console.log((await page.locator('body').innerText()).slice(-1600));
console.log('BUTTONS',await page.getByRole('button').evaluateAll(bs=>bs.map(b=>[b.getAttribute('aria-label'),b.textContent.trim()]).filter(x=>x[0]||x[1])));
await page.screenshot({path:require('path').join(__dirname, '..', 'build/account-mobile.png')});
console.log('ERRORS',errors);await browser.close();if(errors.length)throw Error('UI errors');
})().catch(e=>{console.error(e.message.replace(/token=[^\s]+/g,'token=[hidden]'));process.exitCode=1});

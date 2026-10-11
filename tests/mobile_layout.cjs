const {chromium}=require('../tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 const url=fs.readFileSync(require('path').join(__dirname, '..', 'build/mobile-v6-web.log'),'utf8').match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s]+/)[0];
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:360,height:740},isMobile:true,hasTouch:true,colorScheme:'dark'});
 await page.goto(url,{waitUntil:'networkidle'});await page.addScriptTag({content:fs.readFileSync(require('path').join(__dirname, '..', 'app/src/main/assets/mobile-ui.js'),'utf8')});
 await page.evaluate(()=>document.dispatchEvent(new Event('dsh-mobile-toggle-sidebar')));await page.waitForTimeout(200);
 await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-sidebar.png')});
 console.log('SIDEBAR',await page.locator('.pI_x6G_sidebarCol').isVisible());
 await page.evaluate(()=>document.dispatchEvent(new Event('dsh-mobile-open-models')));await page.waitForTimeout(200);
 await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-settings.png')});
 console.log('SETTINGS DIMENSIONS',await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth})));
 await browser.close();
})().catch(e=>{console.error(e.message.replace(/token=[^\s]+/g,'token=[hidden]'));process.exitCode=1});

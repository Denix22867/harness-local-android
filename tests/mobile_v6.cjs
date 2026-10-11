const {chromium}=require('../tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 const url=fs.readFileSync(require('path').join(__dirname, '..', 'build/mobile-v6-web.log'),'utf8').match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s]+/)[0];
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:360,height:740},isMobile:true,hasTouch:true,colorScheme:"dark"});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(url,{waitUntil:'networkidle',timeout:60000});await page.addScriptTag({content:fs.readFileSync(require('path').join(__dirname, '..', 'app/src/main/assets/mobile-ui.js'),'utf8')});
 for(const name of ['Continue','Configure later','Close']){let b=page.getByRole('button',{name,exact:true});if(await b.count()&&await b.first().isVisible())await b.first().click();}
 await page.waitForTimeout(900);await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-chat.png')});
 await page.evaluate(()=>document.dispatchEvent(new Event('dsh-mobile-open-models')));
 await page.waitForTimeout(600);
 console.log('SETTINGS', (await page.locator('body').innerText()).slice(-2200));
 console.log('BUTTONS',await page.getByRole('button').evaluateAll(bs=>bs.map(b=>[b.getAttribute('aria-label'),b.textContent.trim()]).filter(x=>x[0]||x[1]).slice(-35)));
 await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-models.png')});
 
 await page.getByRole('button',{name:'Add model provider',exact:true}).click();
 const custom=page.getByRole('tab',{name:'Custom model API',exact:true});
 if(await custom.count())await custom.click();
 else{const c=page.getByRole('button',{name:'Custom model API',exact:true});if(await c.count())await c.click();}
 await page.getByRole('button',{name:'Gemini',exact:true}).click();
 await page.waitForTimeout(300);
 await page.locator('.mobile-api-presets').scrollIntoViewIfNeeded();
 await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-gemini.png')});
 console.log('FORM',(await page.locator('body').innerText()).slice(-1800));
 console.log('INPUTS',await page.locator('input').evaluateAll(bs=>bs.map(b=>({label:b.getAttribute('aria-label'),type:b.type,value:b.type==='password'?'[masked]':b.value,placeholder:b.placeholder}))));
 console.log('WIDTH',await page.evaluate(()=>({w:innerWidth,scroll:document.documentElement.scrollWidth})));
 
 await page.getByRole('textbox',{name:'Display name',exact:true}).fill('Mock Gemini');
 await page.getByRole('textbox',{name:'Base URL',exact:true}).last().fill('http://127.0.0.1:3999/v1');
 await page.locator('input[aria-label="API key"]').last().fill('mobile-test-key');
 await page.getByRole('textbox',{name:'Model ID 1',exact:true}).last().fill('mock-gemini');
 const createdRoute=await page.locator('input[aria-label="Provider ID"]').inputValue();
 await page.getByRole('button',{name:'Create provider',exact:true}).click();
 await page.getByText(createdRoute,{exact:true}).waitFor({state:'visible',timeout:90000});
 await page.waitForTimeout(1000);
 console.log('SAVED',(await page.locator('body').innerText()).slice(-1600));
 await page.getByRole('button',{name:'Close',exact:true}).click();
 await page.getByRole('button',{name:/Select model, current/}).click();
 await page.waitForTimeout(300);
 console.log('PICKER',(await page.locator('body').innerText()).slice(-1500));
 console.log('OPTIONS',await page.getByRole('option').evaluateAll(bs=>bs.map(b=>[b.getAttribute('aria-label'),b.textContent.trim()])));
 console.log('ERRORS',errors);await browser.close();if(errors.length)throw Error('UI errors');
})().catch(e=>{console.error(e.message.replace(/token=[^\s]+/g,'token=[hidden]'));process.exitCode=1});

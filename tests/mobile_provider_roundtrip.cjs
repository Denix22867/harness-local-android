const {chromium}=require('../tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 const url=fs.readFileSync(require('path').join(__dirname, '..', 'build/mobile-v6-web.log'),'utf8').match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s]+/)[0];
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:360,height:740},isMobile:true,hasTouch:true,colorScheme:"dark"});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(url,{waitUntil:'networkidle',timeout:60000});await page.addScriptTag({content:fs.readFileSync(require('path').join(__dirname, '..', 'app/src/main/assets/mobile-ui.js'),'utf8')});
 for(const name of ['Continue','Configure later','Close']){let b=page.getByRole('button',{name,exact:true});if(await b.count()&&await b.first().isVisible())await b.first().click();}
 await page.waitForTimeout(900);await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-chat.png')});
 await page.getByRole('button',{name:/Select model, current/}).click();
 await page.waitForTimeout(300);
 console.log('MENUS',await page.locator('[role]').evaluateAll(es=>es.filter(e=>['menuitem','option','listbox','menu'].includes(e.getAttribute('role'))).map(e=>({role:e.getAttribute('role'),text:e.textContent.trim()}))));
 console.log('PICKER',(await page.locator('body').innerText()).slice(-2400));
 console.log('BUTTONS',await page.getByRole('button').evaluateAll(bs=>bs.map(b=>[b.getAttribute('aria-label'),b.textContent.trim()]).filter(x=>x[0]||x[1]).slice(-40)));
 console.log('INPUTS',await page.locator('input').evaluateAll(bs=>bs.map(b=>({label:b.getAttribute('aria-label'),type:b.type,placeholder:b.placeholder}))));
 await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-picker.png')});
 await page.getByRole('menuitem',{name:/^Model/}).click();
 await page.waitForTimeout(500);console.log('SUBMENU',(await page.locator('body').innerText()).slice(-2600));
 await page.getByRole('menuitemradio',{name:'mock-gemini',exact:true}).click();
 await page.locator('[contenteditable=true]').fill('Read mobile-tool-proof.txt, replace its content with tool calling works, and confirm the result.');
 await page.getByRole('button',{name:'Send message',exact:true}).click();
 await page.getByText('Проверка: запись через инструмент выполнена.',{exact:true}).waitFor({timeout:90000});
 await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-tools.png')});
 for(const width of [360,412]){
  await page.setViewportSize({width,height:820});await page.waitForTimeout(150);
  const dimensions=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
  if(dimensions.scroll>dimensions.width)throw Error('Horizontal overflow at '+width);
  await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-chat-')+width+'.png'});
  console.log('PHONE WIDTH',dimensions);
 }
 console.log('TOOL PROOF',fs.readFileSync(require('path').join(__dirname, '..', 'build/test-workspace/deepseek-harness/default-workspace/mobile-tool-proof.txt'),'utf8'));
 const report=JSON.parse(fs.readFileSync(require('path').join(__dirname, '..', 'build/mock-openai-report.json')));
 console.log('API',{calls:report.calls,toolsSeen:report.toolsSeen,toolResultSeen:report.toolResultSeen,model:report.model});
 if(!report.toolsSeen||!report.toolResultSeen||!fs.readFileSync(require('path').join(__dirname, '..', 'build/test-workspace/deepseek-harness/default-workspace/mobile-tool-proof.txt'),'utf8').includes('tool calling works'))throw Error('Tool roundtrip failed');
 console.log('BROWSER ERRORS',errors);if(errors.length)throw Error('Browser errors');
 await browser.close();
})().catch(e=>{console.error(e.message.replace(/token=[^\s]+/g,'token=[hidden]'));process.exitCode=1});

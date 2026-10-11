const {chromium}=require('../tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 const url=fs.readFileSync(require('path').join(__dirname, '..', 'build/mobile-v8-web.log'),'utf8').match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s]+/)[0];
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:360,height:740},isMobile:true,hasTouch:true,colorScheme:"dark"});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(url,{waitUntil:'networkidle',timeout:60000});await page.addScriptTag({content:fs.readFileSync(require('path').join(__dirname, '..', 'app/src/main/assets/mobile-ui.js'),'utf8')});
 for(const name of ['Continue','Configure later','Close']){let b=page.getByRole('button',{name,exact:true});if(await b.count()&&await b.first().isVisible())await b.first().click();}
 await page.waitForTimeout(900);await page.screenshot({path:require('path').join(__dirname, '..', 'build/mobile-v6-chat.png')});
 await page.getByRole('button',{name:/Select model, current/}).click();
 await page.getByRole('menuitem',{name:/^Model/}).click();
 await page.getByRole('menuitemradio',{name:'mock-gemini',exact:true}).click();
 // This isolated fixture uses the UI's explicit permission choice; shipped defaults are unchanged.
 const access=page.getByRole('button',{name:/Access mode, current/});
 if(!(await access.getAttribute('aria-label')).includes('Full access')){
  await access.click();
  console.log('PERMISSION OPTIONS',(await page.locator('body').innerText()).slice(-1800));
  await page.getByText('Full access',{exact:true}).click();
  console.log('PERMISSION CONFIRMATION',(await page.locator('body').innerText()).slice(-1300));
  await page.getByRole('checkbox').check();
  await page.getByRole('button',{name:'Enable Full access',exact:true}).click();
 }
 await page.locator('[contenteditable=true]').fill('Run the SSH fixture command on my authorized test server at 127.0.0.1:4022, user mobile-test, using /root/.ssh/test_client_key and /root/.ssh/test_known_hosts. Command: printf ssh-test-ok.');
 await page.getByRole('button',{name:'Send message',exact:true}).click();
 await page.getByText('SSH: ssh-test-ok',{exact:true}).waitFor({timeout:90000});
 const report=JSON.parse(fs.readFileSync(require('path').join(__dirname, '..', 'build/ssh-tool-report.json')));
 console.log(report);
 if(!report.sshInstructions||!report.bashAvailable||!String(report.toolResult).includes('ssh-test-ok'))throw Error('SSH tool test failed');
 await browser.close();
})().catch(e=>{console.error(e.message.replace(/token=[^\s]+/g,'token=[hidden]'));process.exitCode=1});

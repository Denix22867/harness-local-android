const {chromium}=require('../tools/browser/node_modules/playwright');
const {spawn}=require('child_process');
const path=require('path');
const fs=require('fs');
(async()=>{
 const process=spawn('java',['-cp',path.join(__dirname,'../build/html-test'),'org.dsh.mobile.HtmlFilesCheck','--browser']);
 let browser;
 try{
  const url=await new Promise((resolve,reject)=>{
   let log='';process.stdout.on('data',chunk=>{log+=chunk;const match=log.match(/PREVIEW_URL=(\S+)/);if(match)resolve(match[1])});
   process.stderr.on('data',chunk=>console.error(String(chunk)));
   process.on('exit',code=>reject(new Error('Fixture exited: '+code)));
  });
  browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:360,height:740},isMobile:true,hasTouch:true});
  await page.goto(url,{waitUntil:'networkidle'});
  if(await page.locator('h1').evaluate(el=>getComputedStyle(el).color)!=='rgb(12, 34, 56)')throw Error('CSS not loaded');
  await page.getByRole('button',{name:'Run JS'}).click();
  await page.getByText('JS and fetch work',{exact:true}).waitFor();
  if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Mobile overflow');
  await page.screenshot({path:path.join(__dirname,'../build/html-preview-360.png')});
  await page.goto(new URL('write.html',url).href);
  await page.getByRole('heading',{name:'Written via PRoot'}).waitFor();
  await page.setContent('<li data-files-entry="file" data-files-path="/root/my project/index.html"><button id="html">index.html</button></li><button id="chat" title="/root/my project/index.html">Open from chat</button><li data-files-entry="file" data-files-path="/root/my project/code.py"><button id="code">code.py</button></li>');
  await page.evaluate(()=>{window.normalFileClicks=0;document.addEventListener('click',()=>window.normalFileClicks++)});
  // Stand in for Android's custom-scheme navigation; keep the click handler unchanged.
  const clickScript=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/mobile-ui.js'),'utf8').replace('window.location.href=','window.__previewNavigation=');
  await page.addScriptTag({content:clickScript});
  await page.evaluate(()=>document.querySelector('#html').click());
  await page.evaluate(()=>document.querySelector('#chat').click());
  if(await page.evaluate(()=>new URL(window.__previewNavigation).searchParams.get('path'))!=='/root/my project/index.html')throw Error('Wrong native preview path');
  if(await page.evaluate(()=>window.normalFileClicks)!==0)throw Error('HTML click reached the source viewer');
  await page.evaluate(()=>document.querySelector('#code').click());
  if(await page.evaluate(()=>window.normalFileClicks)!==1)throw Error('Non-HTML file behavior changed');
  console.log('PASS rendered HTML, JS click, relative CSS/JS/fetch, PRoot write HTML, 360px viewport');
  console.log('PASS HTML tree/chat clicks intercepted; other files retain their behavior');
 }finally{if(browser)await browser.close();process.stdin.end();process.kill()}
})().catch(error=>{console.error(error);process.exitCode=1});

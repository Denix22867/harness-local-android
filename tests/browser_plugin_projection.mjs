import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {randomUUID,createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';

// Uses the real upstream MCP image admission/projection helper and real emulator RPC.
const root=path.resolve(import.meta.dirname,'..');
const runtime=process.env.DSH_RUNTIME_DIR||path.join(root,'runtime/rootfs/opt/dsh');
const moduleFile=path.join(runtime,'browser-projection-'+randomUUID()+'.mjs');
const home=await fs.mkdtemp(path.join(os.tmpdir(),'browser-projection-'));
const previousHome=process.env.HOME;
try{
 await fs.copyFile(path.join(root,'app/src/main/assets/android-browser.mjs'),moduleFile);
 await fs.copyFile(path.join(root,'build/browser-test-endpoint.json'),path.join(home,'.harness-browser-bridge.json'));
 process.env.HOME=home;
 let definition,vision=true,saved=0;
 const attachments={async saveImages(images){
  saved+=images.length;
  return Promise.all(images.map(async image=>{
   const digest=createHash('sha256').update(image.data).digest('hex');
   await fs.writeFile(path.join(home,digest+'.png'),image.data);
   return {attachmentId:'sha256:'+digest,mediaType:image.mediaType,bytes:image.data.length,width:Buffer.from(image.data).readUInt32BE(16),height:Buffer.from(image.data).readUInt32BE(20)};
  }));
 }};
 const ctx={tools:{register(value){definition=value}},get(name){
  if(name==='attachments')return attachments;
  if(name==='llm')return {async resolveModelInfo(){return {inputModalities:vision?['text','image']:['text']}}};
 }};
 const plugin=await import(pathToFileURL(moduleFile));plugin.apply(ctx);
 assert.equal(definition.name,'browser');
 const exec={signal:new AbortController().signal,agent:{session:{id:'projection-fixture',requestHeader(){return {config:{provider:'fixture',model:'fixture-model'}}}},options:{provider:'fixture',model:'fixture-model'}}};
 async function call(args){
  const value=await definition.execute(args,exec);
  const content=definition.output.render(args,value);
  return {value,projected:definition.projectContent(exec,{value,content,isError:false})};
 }
 await call({action:'open',path:'/root/browser-fixture/index.html'});
 const screenshot=await call({action:'screenshot'});
 assert.equal(saved,1);
 const image=screenshot.projected.find(block=>block.type==='image');
 assert.ok(image);assert.equal(image.attachment.width,360);assert.equal(image.attachment.height,640);
 assert.ok((await fs.readFile(path.join(home,image.attachment.attachmentId.slice(7)+'.png'))).length>0);
 const state=JSON.parse(screenshot.value.content[0].text);
 assert.ok((await fs.readFile(state.screenshotPath)).subarray(0,4).equals(Buffer.from([137,80,78,71])));
 console.log('PASS real Android screenshot -> upstream MCP image admission -> durable model image reference');
 vision=false;
 const textOnly=await call({action:'screenshot'});
 assert.equal(saved,1);assert.ok(!textOnly.projected.some(block=>block.type==='image'));
 assert.ok(textOnly.projected.some(block=>block.text?.includes('does not declare image input')));
 console.log('PASS text-only model gets a clear image limitation and saved screenshot path');
 await call({action:'close'});
}finally{
 if(previousHome===undefined)delete process.env.HOME;else process.env.HOME=previousHome;
 await fs.rm(moduleFile,{force:true});await fs.rm(home,{recursive:true,force:true});
}

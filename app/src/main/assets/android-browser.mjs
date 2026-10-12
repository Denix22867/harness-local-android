import fs from 'node:fs/promises';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {createMcpToolDefinition} from '@deepseek-ai/dsh-mcp-client';

export const name='android-browser';
export const inject=['tools'];
export function apply(ctx){
 ctx.tools.register(createMcpToolDefinition(ctx,{
  name:'browser',rawName:'browser',
  description:'Control a real Android WebView. Open a local HTML file using an absolute /root or /workspace path, or an HTTP(S) URL. Returns visible text, element selectors/positions and console errors. Screenshot returns the rendered viewport and separate canvas pixel readbacks; Android can omit an accelerated canvas from the viewport image, so inspect its supplied canvas image too. Animation can advance between captures. Cross-origin tainted canvases cannot be read back; WebGL without a preserved drawing buffer may read back blank; image viewing requires a vision-capable model. The browser renders on a private virtual display and never covers the chat. Keep Harness running during checks. A session owns one browser until it is closed. Evaluate runs JavaScript only inside the page; return your result explicitly.',
  inputSchema:{type:'object',additionalProperties:false,required:['action'],properties:{
   action:{type:'string',enum:['open','snapshot','screenshot','click','type','key','evaluate','wait','close']},
   path:{type:'string',description:'Absolute local HTML path for open; use either path or url.'},
   url:{type:'string',description:'HTTP(S) URL for open.'},
   width:{type:'integer',minimum:240,maximum:1280,description:'Viewport CSS width for open; default 360.'},
   height:{type:'integer',minimum:240,maximum:1600,description:'Viewport CSS height for open; default 640.'},
   selector:{type:'string',description:'CSS selector for click/type. Snapshot supplies stable selectors.'},
   x:{type:'number',description:'Viewport CSS x for click when selector is omitted.'},
   y:{type:'number',description:'Viewport CSS y for click when selector is omitted.'},
   text:{type:'string',description:'Text to fill for type.'},
   key:{type:'string',description:'ArrowLeft/Right/Up/Down, Enter, Escape, Space, or a letter/digit.'},
   javascript:{type:'string',description:'Function body for evaluate, e.g. return document.querySelector("canvas").width;'},
   ms:{type:'integer',minimum:0,maximum:5000,description:'Delay for wait; default 500 ms.'},
  }},
  async call(args,exec){
   if(args.action==='open'&&Boolean(args.path)===Boolean(args.url))throw Error('Use exactly one of path or url for open');
   if(args.path&&!/^\/(root|workspace)\//.test(args.path))throw Error('Use an absolute /root or /workspace HTML path');
   const home=process.env.HOME||'/root';
   let endpoint;
   try{endpoint=JSON.parse(await fs.readFile(path.join(home,'.harness-browser-bridge.json'),'utf8'))}
   catch{throw Error('Android browser bridge is unavailable. Start Harness Local on the phone.')}
   const url=new URL(endpoint.url);
   if(url.protocol!=='http:'||url.hostname!=='127.0.0.1'||url.pathname!=='/rpc'||!url.port||typeof endpoint.token!=='string'||endpoint.token.length<32)throw Error('Invalid Android browser endpoint');
   const owner=String(exec.agent?.session?.id||'local-browser');
   const signal=exec.signal?AbortSignal.any([exec.signal,AbortSignal.timeout(45000)]):AbortSignal.timeout(45000);
   const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+endpoint.token},body:JSON.stringify({...args,owner}),signal});
   if(!response.ok)throw Error('Android browser request failed (HTTP '+response.status+')');
   const result=await response.json();if(result.error)throw Error(result.error);
   const image=result.image;delete result.image;
   const layers=result.canvasImages||[];delete result.canvasImages;
   const images=[];
   result.canvasCaptures=[];
   const directory=path.join(home,'.harness-browser/screenshots');
   async function save(data){
    await fs.mkdir(directory,{recursive:true,mode:0o700});
    const screenshot=path.join(directory,randomUUID()+'.png');await fs.writeFile(screenshot,Buffer.from(data,'base64'),{flag:'wx',mode:0o600});
    images.push({type:'image',mimeType:'image/png',data});return screenshot;
   }
   if(image)result.screenshotPath=await save(image);
   for(const layer of layers){
    const data=layer.image;delete layer.image;
    layer.screenshotPath=await save(data);result.canvasCaptures.push(layer);
   }
   return {content:[{type:'text',text:JSON.stringify(result)},...images]};
  },
 }));
}

import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
const req=createRequire('/opt/dsh/package.json');
const yaml=req('yaml');
const home=process.env.HOME||'/root';
const dshHome=process.env.DSH_HOME||path.join(home,'.dsh');
async function readJSON(file,fallback){try{return JSON.parse(await fs.readFile(file,'utf8'));}catch(error){if(error.code==='ENOENT')return fallback;throw error;}}
async function atomic(file,text){await fs.mkdir(path.dirname(file),{recursive:true});const temp=file+'.mobile-next';await fs.writeFile(temp,text,{mode:0o600});await fs.chmod(temp,0o600);await fs.rename(temp,file);}
const setup=await readJSON(path.join(home,'.harness-mobile-providers.json'),{providers:[]});
const managedFile=path.join(dshHome,'mobile-managed-providers.json');
const previous=await readJSON(managedFile,[]);
const file=path.join(dshHome,'profiles/web/cordis.patch.yml');
if(setup.providers.length||previous.length){
 let text='';try{text=await fs.readFile(file,'utf8');}catch(error){if(error.code!=='ENOENT')throw error;}
 const rows=yaml.parse(text)||[];if(!Array.isArray(rows))throw Error('Invalid profile settings document');
 let entry=rows.find(row=>row.id==='llm-pi-ai');
 if(!entry){entry={id:'llm-pi-ai',name:'@deepseek-ai/dsh-llm-pi-ai',config:{providers:{}}};rows.push(entry);}
 entry.config??={};entry.config.providers??={};
 const current=new Set(setup.providers.map(provider=>provider.id));
 for(const id of previous)if(!current.has(id)&&/^mobile-[a-z0-9-]+$/.test(id))delete entry.config.providers[id];
 for(const p of setup.providers){
  if(!/^mobile-[a-z0-9-]+$/.test(p.id))throw Error('Invalid mobile provider ID');
  const url=new URL(p.baseURL);if(!['https:','http:'].includes(url.protocol)||url.username||url.password)throw Error('Invalid mobile provider URL');
  if(!p.model?.trim())throw Error('Mobile provider requires a model ID');
  const ref=p.id.replaceAll('-','_').toUpperCase()+'_API_KEY';
  if(p.token)process.env[ref]=p.token;
  entry.config.providers[p.id]={displayName:p.name,api:'openai-completions',baseURL:p.baseURL,
   ...(p.token?{apiKeyEnv:ref}:{}),
   compat:{supportsDeveloperRole:false,supportsStore:false,supportsStrictMode:false,supportsUsageInStreaming:false,maxTokensField:'max_tokens'},
   models:[{id:p.model,name:p.model,contextWindow:p.contextWindow||262144,maxTokens:p.maxTokens||8192,reasoningEfforts:false}]};
 }
 // Applied before Loader starts: no live reconciliation and no loss of unrelated settings.
 const next=yaml.stringify(rows);if(next!==text)await atomic(file,next);
 await atomic(managedFile,JSON.stringify([...current]));
 console.log('Android: '+setup.providers.length+' compatible API provider(s) configured');
}
const {runCli}=await import('/opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js');
await runCli();

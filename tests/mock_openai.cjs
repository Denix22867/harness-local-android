const http=require('http'),fs=require('fs');
let calls=0,toolsSeen=false,toolResultSeen=false;
http.createServer(async(req,res)=>{
 if(req.url==='/v1/models'){res.writeHead(200,{'content-type':'application/json'});return res.end(JSON.stringify({data:[{id:'mock-gemini',object:'model'}]}));}
 let raw='';for await(const chunk of req)raw+=chunk;
 if(req.url!=='/v1/chat/completions'){res.writeHead(404);return res.end();}
 const body=JSON.parse(raw);calls++;
 if(req.headers.authorization!=='Bearer mobile-test-key'){res.writeHead(401);return res.end();}
 toolsSeen||=Array.isArray(body.tools)&&body.tools.length>0;
 toolResultSeen||=body.messages.some(m=>m.role==='tool');
 fs.appendFileSync(require('path').join(__dirname, '..', 'build/mock-api-trace.jsonl'),JSON.stringify({roles:body.messages.map(m=>m.role),results:body.messages.filter(m=>m.role==='tool'),hasTools:!!body.tools?.length})+'\n');
 const tools=(body.tools||[]).map(t=>({name:t.function.name,parameters:t.function.parameters}));
 fs.writeFileSync(require('path').join(__dirname, '..', 'build/mock-openai-report.json'),JSON.stringify({calls,toolsSeen,toolResultSeen,model:body.model,tools},null,2));
 res.writeHead(200,{'content-type':'text/event-stream','cache-control':'no-cache'});
 const event=(delta,finish=null)=>res.write('data: '+JSON.stringify({id:'mock-chat-'+calls,object:'chat.completion.chunk',created:1,model:body.model,choices:[{index:0,delta,finish_reason:finish}]})+'\n\n');
 event({role:'assistant'});
 const results=body.messages.filter(m=>m.role==='tool').length;
 if(results<2){
  const tool=tools.find(t=>t.name===(results===0?'read':'write'));
  if(!tool){event({content:'No write tool found'},'stop');res.end('data: [DONE]\n\n');return;}
  const props=tool.parameters.properties||{};const args={};
  for(const key of Object.keys(props)){
   if(/^(file_path|path|filename)$/.test(key))args[key]='/workspace/deepseek-harness/default-workspace/mobile-tool-proof.txt';
   if(/^(content|text)$/.test(key))args[key]='tool calling works\n';
  }
  event({tool_calls:[{index:0,id:'call_mobile_'+results,type:'function',function:{name:tool.name,arguments:JSON.stringify(args)}}]});event({},'tool_calls');
 }else{event({content:'Проверка: запись через инструмент выполнена.'});event({},'stop');}
 res.end('data: [DONE]\n\n');
}).listen(3999,'127.0.0.1',()=>console.log('Mock OpenAI server ready'));

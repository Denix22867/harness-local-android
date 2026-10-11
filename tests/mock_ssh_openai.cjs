const http=require('http'),fs=require('fs');let calls=0;
http.createServer(async(req,res)=>{
 let raw='';for await(const part of req)raw+=part;
 if(req.url!='/v1/chat/completions'){res.writeHead(404);return res.end();}
 const body=JSON.parse(raw);calls++;
 if(req.headers.authorization!=='Bearer mobile-test-key'){res.writeHead(401);return res.end();}
 const prompt=body.messages.filter(m=>m.role==='system').map(m=>m.content).join('\n');
 const tools=body.tools||[];const result=body.messages.filter(m=>m.role==='tool').at(-1);
 if(tools.length)fs.writeFileSync(require('path').join(__dirname, '..', 'build/ssh-tool-report.json'),JSON.stringify({calls,sshInstructions:prompt.includes('OpenSSH clients ssh'),bashAvailable:tools.some(t=>t.function.name==='bash'),toolResult:result?.content||null},null,2));
 res.writeHead(200,{'content-type':'text/event-stream'});
 const send=(delta,finish=null)=>res.write('data: '+JSON.stringify({id:'ssh-test-'+calls,object:'chat.completion.chunk',model:body.model,created:1,choices:[{index:0,delta,finish_reason:finish}]})+'\n\n');send({role:'assistant'});
 if(!tools.length){send({content:'SSH test'},'stop');}
 else if(!result){
  const command='ssh -T -p 4022 -i /root/.ssh/test_client_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=/root/.ssh/test_known_hosts mobile-test@127.0.0.1 "printf ssh-test-ok"';
  send({tool_calls:[{index:0,id:'ssh-call-1',type:'function',function:{name:'bash',arguments:JSON.stringify({command,description:'Проверить SSH на локальном тестовом сервере',workdir:'/workspace/deepseek-harness/default-workspace'})}}]});send({},'tool_calls');
 }else{send({content:String(result.content).includes('ssh-test-ok')?'SSH: ssh-test-ok':'SSH test failed: '+result.content},'stop');}
 res.end('data: [DONE]\n\n');
}).listen(3999,'127.0.0.1',()=>console.log('SSH mock API ready'));

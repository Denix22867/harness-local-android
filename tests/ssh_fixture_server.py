import paramiko, socket, threading, json, subprocess, time
from pathlib import Path
base=Path('/workspace/harness-local-android/build/ssh-fixture');host_key=paramiko.RSAKey.generate(2048)
(base/'known_hosts').write_text('[127.0.0.1]:4022 '+host_key.get_name()+' '+host_key.get_base64()+'\n')
client_key=(base/'client_key.pub').read_text().split()[1]
report={'connections':0,'keyLogins':0,'passwordLogins':0,'commands':[]}
lock=threading.Lock()
def record():
 with lock:(base/'report.json').write_text(json.dumps(report,indent=2))
class Server(paramiko.ServerInterface):
 def get_allowed_auths(self,username):return 'publickey,password'
 def check_auth_publickey(self,username,key):
  if username=='mobile-test' and key.get_base64()==client_key:
   report['keyLogins']+=1;record();return paramiko.AUTH_SUCCESSFUL
  return paramiko.AUTH_FAILED
 def check_auth_password(self,username,password):
  if username=='mobile-test' and password=='mobile-password-fixture':
   report['passwordLogins']+=1;record();return paramiko.AUTH_SUCCESSFUL
  return paramiko.AUTH_FAILED
 def check_channel_request(self,kind,chanid):return paramiko.OPEN_SUCCEEDED if kind=='session' else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
 def check_channel_exec_request(self,channel,command):
  command=command.decode();report['commands'].append(command);record()
  def run():
   if command=='printf ssh-test-ok':
    output=subprocess.check_output(['printf','ssh-test-ok']);channel.send(output);channel.send_exit_status(0)
   else:channel.send_stderr(b'Unexpected test command');channel.send_exit_status(1)
   channel.shutdown_write();channel.close()
  threading.Thread(target=run,daemon=True).start();return True
 def check_channel_pty_request(self,*args):return False
 def check_channel_shell_request(self,*args):return False
def handle(sock):
 transport=paramiko.Transport(sock)
 try:
  transport.add_server_key(host_key);transport.start_server(server=Server())
  while transport.is_active():time.sleep(.05)
 except Exception as e:print(type(e).__name__,flush=True)
 finally:transport.close()
listener=socket.socket();listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);listener.bind(('127.0.0.1',4022));listener.listen(8)
print('Local SSH fixture ready',flush=True)
while True:
 sock,_=listener.accept();report['connections']+=1;record();threading.Thread(target=handle,args=(sock,),daemon=True).start()

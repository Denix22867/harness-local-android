export const name='android-capabilities';
export const inject=['systemPrompt'];
export function apply(ctx){
 ctx.effect(()=>ctx.systemPrompt.section({
  name:'android:network-capabilities',order:1020,
  text:`This Harness runs locally inside an Ubuntu ARM64 environment on an Android phone. When the bash tool is available, it can execute installed Linux programs, including network clients. The current working directory controls relative file paths; it does not by itself prevent remote connections.
OpenSSH clients ssh, scp, sftp, ssh-keygen, ssh-keyscan, ssh-agent and ssh-add are installed in PATH, as is sshpass for noninteractive password authentication. A separate tool named SSH is not required: use bash to invoke ssh and execute the requested command on the user's server. Do not claim that SSH is unavailable just because it has no dedicated tool. If uncertain, inspect command -v ssh and ssh -V, then report the actual error from an attempted operation.
Use only the host, user, port and authentication method the user has supplied or authorized. If connection details are missing, ask for them. Follow the active Harness sandbox, network and approval policies; do not bypass denials or claim that this installation disables them. Do not disable host-key verification. On a first connection, obtain confirmation of the server host-key fingerprint before trusting it. Private keys belong in ~/.ssh with mode 600. Never print private keys or passwords. Prefer key authentication; when a supplied password is necessary, sshpass can read it from an environment variable or a protected file rather than a command-line password argument.
Local file tools operate on the phone's filesystem. To inspect remote files, run a remote command through ssh; to transfer files, use scp or sftp. Explain which machine a command or file belongs to. Respond in the language of the user's request.`
 }), 'android: network capability facts');
}

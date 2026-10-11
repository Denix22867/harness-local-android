from pathlib import Path
import json
b=Path(__file__).resolve().parents[1]
mods=b/'runtime/rootfs/opt/dsh/node_modules/@deepseek-ai'

def patch(package, marker, changes, asset):
 p=mods/package/'lib/client.js';s=p.read_text()
 # Always start from immutable runtime when regenerating a patch.
 import zipfile
 with zipfile.ZipFile(b/'app/src/main/assets/runtime.zip') as z:s=z.read('opt/dsh/node_modules/@deepseek-ai/'+package+'/lib/client.js').decode()
 for old,new in changes:
  assert s.count(old)==1,(package,old,s.count(old));s=s.replace(old,new)
 p.write_text(s)
 (b/'app/src/main/assets'/asset).write_text(json.dumps({'marker':marker,'replacements':[{'old':x,'new':y} for x,y in changes]}))

old='const [protocol, setProtocol] = (0, react.useState)(protocols[0] ?? "");'
new='const [protocol, setProtocol] = (0, react.useState)(protocols.includes("openai-completions") ? "openai-completions" : protocols[0] ?? ""); // Android mobile providers v6.'
marker='// Android mobile providers v6.'
changes=[(old,new)]
needle='const [openedAt] = (0, react.useState)(() => props.revision);'
changes.append((needle,needle+'''
            const uniqueRoute = (base) => { let value=base, n=2; while(taken.includes(value)) value=base+"-"+n++; return value; };'''))
needle='const [route, setRoute] = (0, react.useState)("");'
changes.append((needle,'const [route, setRoute] = (0, react.useState)(() => uniqueRoute("custom-openai"));'))
needle='const [models, setModels] = (0, react.useState)([]);'
changes.append((needle,'const [models, setModels] = (0, react.useState)([{id:"", name:"", contextWindow:262144, maxTokens:8192, reasoningEfforts:false}]);'))
needle='api: protocol,\n\t\t\t\t\t\tbaseURL: normalizedBaseURL,'
changes.append((needle,'''api: protocol,
                        ...(protocol === "openai-completions" ? {compat:{supportsDeveloperRole:false,supportsStore:false,supportsStrictMode:false,supportsUsageInStreaming:false,maxTokensField:"max_tokens"}} : {}),
                        baseURL: normalizedBaseURL,'''))
start='''return (0, react_jsx_runtime.jsxs)("div", {
\t\t\t\tclassName: ModelsSection_module_css_default["editor"],
\t\t\t\tchildren: ['''
# This exact fragment appears in multiple editors; scope the replacement with preceding create closure.
needle='''props.onClose(true);
\t\t\t\t} finally {
\t\t\t\t\tsetBusy(false);
\t\t\t\t}
\t\t\t};
\t\t\t'''+start
newneedle=needle+'''
                    (0, react_jsx_runtime.jsxs)("div", {className:"mobile-api-presets",children:[
                        (0, react_jsx_runtime.jsx)("button", {type:"button",disabled:profileDisabled,onClick:()=>{
                            setRoute(uniqueRoute("gemini-openai"));setDisplayName("Gemini");
                            setBaseURL("https://generativelanguage.googleapis.com/v1beta/openai/");setProtocol("openai-completions");
                            setModels([{id:"gemini-2.5-flash",name:"Gemini 2.5 Flash",contextWindow:1048576,maxTokens:8192,reasoningEfforts:false}]);
                        },children:"Gemini"}),
                        (0, react_jsx_runtime.jsx)("button", {type:"button",disabled:profileDisabled,onClick:()=>{
                            setRoute(uniqueRoute("custom-openai"));setDisplayName("");setBaseURL("");setProtocol("openai-completions");
                            setModels([{id:"",name:"",contextWindow:262144,maxTokens:8192,reasoningEfforts:false}]);
                        },children:"Свой API"})
                    ]}),
                    (0, react_jsx_runtime.jsx)("p",{className:"mobile-api-note",children:"Инструменты Harness передаются через tool calling. Сервер и модель должны поддерживать вызовы функций. Токен хранится отдельно от настроек."}),'''
changes.append((needle,newneedle))
patch('dsh-client-ui-settings-models',marker,changes,'models-ui-patch.json')
needle='const shellInstance = shellHandle.create();'
new=needle+'''
                // Android models shortcut v6.
                const openMobileModels = () => shellInstance.actions.openSection("models");
                document.addEventListener("dsh-mobile-open-models", openMobileModels);
                const toggleMobileSidebar = () => ctx.get("layout")?.toggleSidebar();
                document.addEventListener("dsh-mobile-toggle-sidebar", toggleMobileSidebar);
                ctx.effect(() => () => document.removeEventListener("dsh-mobile-toggle-sidebar", toggleMobileSidebar), "mobile: sidebar shortcut");
                ctx.effect(() => () => document.removeEventListener("dsh-mobile-open-models", openMobileModels), "mobile: models shortcut");'''
patch('dsh-client-ui-settings-general','// Android models shortcut v6.',[(needle,new)],'settings-ui-patch.json')

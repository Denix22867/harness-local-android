from pathlib import Path
b=Path(__file__).resolve().parents[1]
p=b/'runtime/rootfs/opt/dsh/node_modules/@deepseek-ai/dsh-client-ui-settings-account/lib/client.js'
s=p.read_text()
guard='if (!("dshDesktop" in globalThis)) return;'
assert s.count(guard)==1
changes=[{'old':guard,'new':'// Android uses the upstream account service and loopback callback.'}]
s=s.replace(guard,changes[-1]['new'])
needle='const authorizeUrl = attempt?.authorizeUrl;'
assert s.count(needle)==1
changes.append({'old':needle,'new':needle+'''
            const openedAttempt = (0, react.useRef)(null);
            (0, react.useEffect)(() => {
                if (attempt?.phase !== "waiting-browser" || !authorizeUrl || openedAttempt.current === attempt.id) return;
                openedAttempt.current = attempt.id;
                window.open(authorizeUrlWithTheme(authorizeUrl, colorScheme), "_blank", "noopener,noreferrer");
            }, [attempt?.id, attempt?.phase, authorizeUrl, colorScheme]);'''})
s=s.replace(needle,changes[-1]['new'])
needle='t("browserPrompt"),'
assert s.count(needle)==1
changes.append({'old':needle,'new':needle+'''
                        authorizeUrl && (0, react_jsx_runtime.jsx)("a", {
                            href: authorizeUrlWithTheme(authorizeUrl, colorScheme), target: "_blank", rel: "noopener noreferrer",
                            children: "Открыть DeepSeek в браузере"
                        }),'''})
s=s.replace(needle,changes[-1]['new'])
import json
(b/'app/src/main/assets/account-ui-patch.json').write_text(json.dumps({'replacements':changes}))
p.write_text(s)

"""Runs on a rooted *test emulator* with the browser-only carrier; no paid API calls."""
import base64, io, json, os, subprocess, time, urllib.request
from urllib.parse import urlparse
from pathlib import Path
from PIL import Image

BASE = Path(__file__).resolve().parents[1]
ADB = os.environ.get('DSH_ADB', str(BASE / 'tools/sdk/platform-tools/adb'))
def adb(*args):
    return subprocess.check_output([ADB, *args], timeout=40)

assert adb('shell', 'getprop', 'ro.kernel.qemu').decode().strip() == '1', 'Use a test Android emulator, not a personal phone'

endpoint = None
for attempt in range(20):
    try:
        endpoint = json.loads(adb('exec-out', 'cat', '/data/user/0/org.dsh.mobile/files/home/.harness-browser-bridge.json'))
        break
    except (subprocess.CalledProcessError, json.JSONDecodeError):
        time.sleep(.5)
assert endpoint, 'Browser bridge did not start'
port = urlparse(endpoint['url']).port
adb('forward', f'tcp:{port}', f'tcp:{port}')
adb('shell', 'mkdir', '-p', '/data/user/0/org.dsh.mobile/files/home/browser-fixture')
adb('push', str(BASE / 'tests/browser-fixture.html'), '/data/user/0/org.dsh.mobile/files/home/browser-fixture/index.html')
# Preserve the app's UID on the copied emulator fixture.
uid = adb('shell', 'stat', '-c', '%u', '/data/user/0/org.dsh.mobile/files').decode().strip()
adb('shell', 'chown', '-R', f'{uid}:{uid}', '/data/user/0/org.dsh.mobile/files/home/browser-fixture')
adb('shell', 'restorecon', '-R', '/data/user/0/org.dsh.mobile/files/home/browser-fixture')

def call(action, owner='browser-test', **args):
    request = urllib.request.Request(endpoint['url'], json.dumps({'action':action,'owner':owner,**args}).encode(),
        headers={'Content-Type':'application/json','Authorization':'Bearer '+endpoint['token']})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)

opened = call('open', path='/root/browser-fixture/index.html', width=360, height=640)
assert 'error' not in opened, opened
assert opened['viewport'] == {'width':360, 'height':640}, opened
assert 'Browser tool fixture' in opened['text']
assert 'error' in call('snapshot', owner='other-session')
assert 'org.dsh.mobile/.MainActivity' in adb('shell','dumpsys','activity','activities').decode().split('mResumedActivity:')[-1].split('\n')[0], 'Browser covered the chat'
print('PASS real WebView open, viewport, DOM, ownership and chat remains foreground', flush=True)
for mode, color in [('classic',(220,20,20)), ('fast',(20,220,20)), ('maze',(20,20,220))]:
    clicked = call('click', selector='#'+mode)
    assert 'error' not in clicked, clicked
    state = call('evaluate', javascript='return currentMode;')
    assert state['value'] == mode, state
    screenshot = call('screenshot')
    assert 'error' not in screenshot, screenshot
    image = Image.open(io.BytesIO(base64.b64decode(screenshot['image']))).convert('RGB')
    assert image.size == (360,640)
    assert dict((pixel,count) for count,pixel in image.getcolors(image.width*image.height)).get(color,0) > 40000, 'Canvas not captured correctly'
    image.save(BASE / 'build' / ('browser-'+mode+'.png'))
print('PASS native click and screenshots of three actual canvas modes', flush=True)
call('evaluate', javascript="""
var c=document.querySelector('canvas');c.width=2800;c.height=1800;
c.style.width='320px';c.style.height='220px';c.style.transform='translateZ(0)';
window.animationFrames=0;
function animate(){var x=c.getContext('2d');x.fillStyle='rgb(17,123,45)';x.fillRect(0,0,c.width,c.height);
x.fillStyle='rgb(230,30,70)';x.fillRect(0,0,c.width/2,c.height);
x.fillStyle='white';x.fillRect((window.animationFrames++%100)*20,1700,20,20);
window.animationHandle=requestAnimationFrame(animate)}animate();return true;
""")
call('wait',ms=700)
assert call('evaluate',javascript='return window.animationFrames;')['value']>2
large=call('screenshot')
assert 'error' not in large,large
assert large['canvasImages'], 'Canvas readback not supplied'
large_image=Image.open(io.BytesIO(base64.b64decode(large['canvasImages'][0]['image']))).convert('RGB')
colors=dict((pixel,count) for count,pixel in large_image.getcolors(large_image.width*large_image.height))
large_image.save(BASE/'build/browser-large-animated.png')
assert colors.get((17,123,45),0)>25000 and colors.get((230,30,70),0)>25000, ('Large animated canvas pixels missing from automatic readback', sorted(colors.items(),key=lambda item:item[1],reverse=True)[:8])
call('evaluate',javascript='cancelAnimationFrame(window.animationHandle);return true;')
assert 'org.dsh.mobile/.MainActivity' in adb('shell','dumpsys','activity','activities').decode().split('mResumedActivity:')[-1].split('\n')[0], 'Browser covered the chat'
assert 'MainActivity' in adb('shell','dumpsys','window','windows').decode().split('mCurrentFocus=')[-1].split('\n')[0], 'Hidden browser stole input focus'
print('PASS large 2800x1800 animated canvas readback captured while chat retains display and input focus',flush=True)
desktop = call('open', path='/root/browser-fixture/index.html', width=1280, height=800)
assert desktop['viewport'] == {'width':1280, 'height':800}, desktop
call('evaluate', javascript="document.body.insertAdjacentHTML('beforeend','<div style=\"position:fixed;right:0;top:0;width:128px;height:128px;background:rgb(111,222,33)\"></div>'); return true;")
full = call('screenshot')
assert 'error' not in full,full
full_image = Image.open(io.BytesIO(base64.b64decode(full['image']))).convert('RGB')
assert full_image.size == (1280,800)
assert full_image.getpixel((1270,10)) == (111,222,33), 'Offscreen viewport area not captured'
print('PASS desktop viewport screenshot beyond the physical phone width', flush=True)
assert 'error' not in call('type', selector='#name', text='Android browser')
assert call('evaluate', javascript="return document.querySelector('#name').value;")['value'] == 'Android browser'
call('click', selector='canvas')
assert 'error' not in call('key', key='ArrowRight')
assert call('evaluate', javascript='return keyCount;')['value'] > 0
errors = call('click', selector='#error')
assert any('fixture-console-error' in row for row in errors['console']), errors
print('PASS input, actual key event and JavaScript console errors', flush=True)
assert 'error' in call('open', url='file:///etc/passwd')
assert 'error' in call('open', url='http://127.0.0.1:3080/')
assert call('close')['closed']
print('PASS blocked unsafe navigation and browser close', flush=True)
# Write only the temporary test endpoint for the local Node projection check.
(BASE / 'build' / 'browser-test-endpoint.json').write_text(json.dumps(endpoint))
os.chmod(BASE / 'build' / 'browser-test-endpoint.json', 0o600)

#!/usr/bin/env python3
"""Capture synthetic Textr screens on a disposable API 33+ emulator.
Requires installed debug AND androidTest APKs. Resets Textr app data explicitly.
No screenshot pixels are edited. See docs/screenshots.md.
"""
import argparse
import base64
import os
from pathlib import Path
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--reset-app-data', action='store_true', required=True)
parser.add_argument('--output', default='docs/images')
args = parser.parse_args()
adb = str(Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))) / 'platform-tools/adb')
pkg = 'de.utrost.textr'
out = Path(args.output); out.mkdir(parents=True, exist_ok=True)

def call(*argv, data=None, binary=False):
    result = subprocess.run([adb, '-s', args.serial, *argv], input=data, capture_output=True, text=not binary, check=True, timeout=40)
    return result.stdout

def shell(*argv, data=None):
    return call('shell', shlex.join(str(a) for a in argv), data=data)

def nodes():
    for _ in range(5):
        try:
            shell('rm', '-f', '/data/local/tmp/textr-screen.xml')
            shell('uiautomator', 'dump', '/data/local/tmp/textr-screen.xml')
            return list(ET.fromstring(shell('cat', '/data/local/tmp/textr-screen.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(1)
    raise RuntimeError('Could not read the visible UI hierarchy')

def click(value):
    for node in nodes():
        if value in (node.get('text'), node.get('content-desc')):
            import re
            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
            shell('input','tap',(x1+x2)//2,(y1+y2)//2); time.sleep(.5); return
    raise RuntimeError('Missing action '+value)

def capture(name):
    time.sleep(.5)
    (out / name).write_bytes(call('exec-out','screencap','-p',binary=True))
    print(name, flush=True)

def prefs(**entries):
    shell('am','force-stop',pkg)
    body='<map>'+''.join(f'<boolean name="{k}" value="{str(v).lower()}"/>' if isinstance(v,bool)
        else f'<string name="{k}">{v}</string>' for k,v in entries.items())+'</map>'
    shell('run-as',pkg,'mkdir','-p','shared_prefs')
    shell('run-as',pkg,'sh','-c','cat > shared_prefs/reading.xml',data=body)

def launch(name=None, text=None):
    if name:
        uri='content://de.utrost.textr.test.documents/'+name
        if text is not None:
            encoded = base64.b64encode(text.encode()).decode()
            result = shell('content','call','--uri',uri,'--method','replace-base64','--arg',uri,'--extra','text:s:'+encoded)
            if '[ERROR]' in result or 'Result:' not in result: raise RuntimeError(result)
            if shell('content','read','--uri',uri) != text: raise RuntimeError('Fixture bytes differ')
        shell('am','start','-W','-n',pkg+'/.MainActivity','-a','android.intent.action.VIEW','-d',uri)
        for _ in range(20):
            if any(n.get('resource-id') == pkg+':id/reading_progress' for n in nodes()): break
            time.sleep(.5)
        else: raise RuntimeError('Reader did not finish')
    else: shell('am','start','-W','-n',pkg+'/.MainActivity')

shell('pm','clear',pkg)
try:
    shell('wm','size','1080x1920'); shell('wm','density','420')
    shell('settings','put','system','font_scale','1.0')
    shell('settings','put','global','window_animation_scale','0')
    shell('settings','put','global','transition_animation_scale','0')
    shell('settings','put','global','animator_duration_scale','0')
    shell('cmd','locale','set-app-locales',pkg,'--locales','en')
    shell('settings','put','global','sysui_demo_allowed','1')
    shell('am','broadcast','-a','com.android.systemui.demo','--es','command','clock','--es','hhmm','1000')
    prefs(theme='light'); launch(); capture('reading-home.png')
    markdown='# A quieter reading space\n\nOpen a file. Read what matters.\n\n## Weekend notes\n\n- Walk by the river\n- Bring a notebook\n- Leave room for a detour\n\n> Small files, simple tools.\n\n## Packing list\n\n| Item | Ready |\n| --- | --- |\n| Notebook | Yes |\n| Water | Yes |\n| Camera | Later |\n\n## Example\n\n```json\n{ "mode": "read", "offline": true }\n```\n'
    launch('Weekend.md',markdown); capture('reading-markdown.png')
    launch('welcome.js','// A small example, read without editing.\n\nconst reader = {\n  name: "Textr",\n  offline: true,\n  maxFiles: 5\n};\n\nfunction welcome(name) {\n  return `Hello, ${name}!`;\n}\n\nfor (const file of ["notes.md", "app.json"]) {\n  console.log(welcome(file));\n}\n'); capture('reading-javascript.png')
    shell('wm','size','1600x1200'); shell('wm','density','240')
    prefs(theme='light', fileSidebar=True, sidebarHintDismissed=True)
    launch('Weekend.md',markdown); capture('reading-wide.png')
    shell('wm','size','1080x1920'); shell('wm','density','420')
    prefs(theme='light')
    shell('cmd','locale','set-app-locales',pkg,'--locales','de')
    launch(); capture('reading-german.png')
    click('Weitere Optionen'); click('Leseeinstellungen'); time.sleep(1); capture('reading-settings-de.png')
    shell('input','keyevent','4')
    prefs(theme='dark', sidebarHintDismissed=True)
    shell('cmd','locale','set-app-locales',pkg,'--locales','en')
    launch('Weekend.md',markdown); capture('reading-dark.png')
finally:
    shell('am','force-stop',pkg)
    shell('cmd','locale','set-app-locales',pkg)
    shell('wm','size','reset'); shell('wm','density','reset')
    shell('settings','put','system','font_scale','1.0')
    shell('am','broadcast','-a','com.android.systemui.demo','--es','command','exit')

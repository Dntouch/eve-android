"""ADB UI smoke tests against tests/mock_eve.py, never against production EVE."""
import subprocess as sp
import time
import xml.etree.ElementTree as ET
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ADB=str(ROOT/'.tooling/sdk/platform-tools/adb')
def adb(*args,raw=False):
    r=sp.run([ADB,*args],check=True,stdout=sp.PIPE,stderr=sp.PIPE)
    return r.stdout if raw else r.stdout.decode(errors='replace')
def dump():
    adb('shell','uiautomator','dump','/sdcard/eve-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/eve-ui.xml'))
def nodes():return list(dump().iter('node'))
def find(text=None,desc=None,klass=None):
    for n in nodes():
        if text is not None and n.get('text','').casefold()!=text.casefold():continue
        if desc is not None and n.get('content-desc')!=desc:continue
        if klass is not None and n.get('class')!=klass:continue
        return n
    raise AssertionError(f'UI node missing: {text or desc or klass}')
def click(text=None,desc=None,klass=None):
    n=find(text,desc,klass);x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.25)
def set_edit(index,value,clear=80):
    edits=[n for n in nodes() if n.get('class')=='android.widget.EditText']
    n=edits[index];x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));adb('shell','input','keycombination','113','29');adb('shell','input','keyevent','67');adb('shell','input','text',value.replace(' ','%s'))
def shot(name):
    path=ROOT/'build'/f'{name}.png';path.write_bytes(adb('exec-out','screencap','-p',raw=True));return path

def configure():
    if not any(n.get('text')=='Mit EVE verbinden' for n in nodes()):
        click(desc='EVE-Menü');click(text='Verbindung und Darstellung')
    set_edit(0,'http://10.0.2.2:18790');adb('shell','input','keyevent','4');click(text='Verbinden');time.sleep(1)
if __name__=='__main__':
    if any(n.get('content-desc')=='Arbeitsraum schließen' for n in nodes()):click(desc='Arbeitsraum schließen')
    configure();shot('eve-chat')
    assert any(n.get('text')=='Ein guter Anfang' for n in nodes())
    click(desc='Gespräche, Zeitpläne und Aufgabenlisten');time.sleep(.8);shot('eve-conversations')
    for text in ['Heute','Gestern','Letzte 7 Tage']:find(text=text.upper())
    adb('shell','input','swipe','540','1650','540','1000','300');find(text='ÄLTER')
    click(text='Zeitpläne');time.sleep(.8);shot('eve-schedules');find(text='Morgenüberblick')
    click(desc='Zeitplan pausieren');time.sleep(.8);find(desc='Zeitplan fortsetzen')
    click(desc='Zeitplan fortsetzen');time.sleep(.8);find(desc='Zeitplan pausieren')
    click(text='Aufgaben');time.sleep(.8);shot('eve-lists');click(text='Zuhause');time.sleep(.8);shot('eve-tasks');find(text='Pflanzen gießen')
    click(desc='Aufgabe erledigen');time.sleep(.8)
    click(text='Erledigte anzeigen (2)');time.sleep(.4);find(text='Pflanzen gießen')
    click(desc='Arbeitsraum schließen');shot('eve-chat')
    print('PASS: structured conversations, schedule PATCH pause/resume, work lists and task completion on Android')

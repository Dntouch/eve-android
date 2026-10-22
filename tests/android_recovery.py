"""Background, process recreation and forced stream-abort regression coverage."""
import json,time
from android_smoke import adb,nodes,find,click,set_edit,configure,ROOT
LOG=ROOT/'.tooling/mock-requests.jsonl'
def rows():return [json.loads(x) for x in LOG.read_text().splitlines()]
def count(text):return sum(r.get('method')=='POST' and r.get('path')=='/api/message/stream' and r.get('text')==text for r in rows())
def start(text):set_edit(0,text);click(desc='Nachricht senden');time.sleep(.5)
def reopen():adb('shell','am','start','-n','de.eve.mobile/.MainActivity');time.sleep(1)
def assert_active():
    texts=[n.get('text','') for n in nodes()]
    assert any(t in ('EVE denkt nach','Werkzeug · Home Assistant') for t in texts),texts
    assert not any('HTTP 404' in t or 'abort' in t or 'unterbrochen' in t for t in texts),texts
if __name__=='__main__':
    configure()
    start('Resume Hintergrund');adb('shell','input','keyevent','3');time.sleep(1);reopen();assert_active();time.sleep(14)
    assert count('Resume Hintergrund')==1
    assert any(r['path'].startswith('/api/message/stream/turn-') and r['method']=='GET' for r in rows()),'No read-only observer reattachment'
    start('Resume Prozess');adb('shell','am','force-stop','de.eve.mobile');reopen();assert_active();time.sleep(14)
    assert count('Resume Prozess')==1
    start('Drop Verbindung');time.sleep(1);assert_active();time.sleep(14)
    assert count('Drop Verbindung')==1
    assert sum(r.get('method')=='GET' and r.get('path','').startswith('/api/message/stream/turn-') for r in rows())>=3
    print('PASS: minimize/reopen, process recreation and forced stream abort recover via GET; exactly one submission per turn')

"""Local EVE contract fixture. No production connections or real device actions."""
import base64
import io
import json
import struct
import time
import wave
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs
from pathlib import Path
from threading import Lock

LOG=Path('.tooling/mock-requests.jsonl')
NOW=datetime.now(timezone.utc)
STAMP=NOW.isoformat()
MD='''## Ein guter Anfang

**Hoi.** Deine EVE ist jetzt auch unterwegs bei dir.

- **Chatten** und Gedanken mitverfolgen
- Zeitpläne im Blick behalten
- Aufgaben Schritt für Schritt erledigen

> Ein Gedanke. Eine Frage. Ein Anfang.

| Bereich | Status |
| --- | --- |
| Verbindung | Bereit |
| Aufgaben | 3 offen |

```python
print("Hoi, EVE!")
```

Ein [Link zu Android](https://developer.android.com), `Code` und ~~Durchgestrichenes~~.
'''
CONVERSATIONS=[{'id':'mobile-test','title':'Ein guter Anfang','message_count':4,'updated_at':STAMP},
 {'id':'yesterday','title':'Ideen für unser Zuhause','message_count':12,'updated_at':(NOW-timedelta(days=1)).isoformat()},
 {'id':'older','title':'EVE für unterwegs','message_count':8,'updated_at':(NOW-timedelta(days=4)).isoformat()},
 {'id':'archive','title':'Projekt Werkstatt','message_count':24,'updated_at':(NOW-timedelta(days=25)).isoformat()}]
HISTORY=[{'role':'user','content':'Hoi EVE. Was nehmen wir uns heute vor?'},{'role':'assistant','content':MD}]
LISTS=[{'id':'home','name':'Zuhause','description':'Kleine Dinge, die den Alltag leichter machen.','open_count':2},
 {'id':'eve','name':'EVE für unterwegs','description':'Unsere Ideen für die Android-App.','open_count':1}]
ITEMS=[{'id':'i1','list_id':'home','title':'Pflanzen gießen','description':'Auch an die **Monstera** im Wohnzimmer denken.','status':'open'},
 {'id':'i2','list_id':'home','title':'Einkauf planen','description':'- Kaffee\n- Hafermilch\n- Frisches Gemüse','status':'open'},
 {'id':'i3','list_id':'home','title':'Wäsche aufhängen','description':'','status':'completed'},
 {'id':'i4','list_id':'eve','title':'Die neue App ausprobieren','description':'Chat, Aufgaben und Zeitpläne testen.','status':'open'}]
SCHEDULES=[{'id':'s1','name':'Morgenüberblick','status':'active','schedule_type':'daily','schedule':{'hour':8,'minute':0},'timezone':'Europe/Berlin','next_run_at':(NOW+timedelta(hours=10)).isoformat(),'action':{'request':'Morgens einen kurzen Überblick über Wetter und anstehende Aufgaben geben.'}},
 {'id':'s2','name':'Zeit für die Pflanzen','status':'paused','schedule_type':'weekly','schedule':{'hour':18,'minute':0,'weekdays':[1,5]},'timezone':'Europe/Berlin','next_run_at':(NOW+timedelta(days=2)).isoformat(),'action':{'request':'An die Pflanzen im Wohnzimmer erinnern.'}}]
LIVE={'state':'idle','detail':''}
LOCK=Lock()
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def json(self,value,status=200):
        body=json.dumps(value,ensure_ascii=False).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def do_GET(self):
        path=urlsplit(self.path).path;query=parse_qs(urlsplit(self.path).query)
        if path=='/api/state':self.log(path,state=LIVE['state']);self.json(LIVE)
        elif path=='/api/conversations':self.json({'conversations':CONVERSATIONS,'active_id':'mobile-test'})
        elif path.startswith('/api/conversations/'):
            conv=next((c for c in CONVERSATIONS if c['id']==path.rsplit('/',1)[-1]),CONVERSATIONS[0]);self.json({'conversation':conv,'messages':HISTORY,'has_more':False})
        elif path=='/api/tasks':self.json({'tasks':SCHEDULES})
        elif path=='/api/work-lists':
            for l in LISTS:l['open_count']=sum(i['list_id']==l['id'] and i['status']=='open' for i in ITEMS)
            self.json({'lists':LISTS})
        elif path=='/api/work-items':self.json({'items':[i for i in ITEMS if i['list_id']==query.get('list_id',[''])[0]]})
        else:self.json({'error':'Nicht gefunden'},404)
    def log(self,path,**data):
        with LOCK:
            with LOG.open('a') as f:f.write(json.dumps({'method':self.command,'path':path,**data})+'\n')
    def do_POST(self):
        body=self.rfile.read(int(self.headers.get('Content-Length',0)));path=urlsplit(self.path).path
        if path=='/api/work-lists':
            data=json.loads(body);entry={'id':'l'+str(time.time_ns()),**data,'open_count':0};LISTS.append(entry);self.log(path,body=data);self.json({'list':entry},201);return
        if path=='/api/work-items':
            data=json.loads(body);entry={'id':'i'+str(time.time_ns()),**data,'status':'open'};ITEMS.append(entry);self.log(path,body=data);self.json({'item':entry},201);return
        if path=='/api/conversations':self.json({'conversation':CONVERSATIONS[0]},201);return
        if path.endswith('/cancel'):LIVE.update(state='idle',detail='');self.log(path);self.json({'cancel_requested':True});return
        if path not in ('/api/message/stream','/api/turn/stream'):self.json({'error':'Nicht gefunden'},404);return
        if path=='/api/message/stream':
            data=json.loads(body);text=data['text'];assert data['resume_conversation'] is True;count=len(data['images'])
            for image in data['images']:assert base64.b64decode(image.split(',')[1]).startswith(b'\xff\xd8\xff')
        else:assert body[:4]==b'RIFF' and body[8:12]==b'WAVE';text='Das ist eine Sprachnachricht.';count=0
        self.log(path,text=text,images=count,bytes=len(body))
        self.send_response(200);self.send_header('Content-Type','application/x-ndjson');self.end_headers()
        def event(kind,**kw):self.wfile.write((json.dumps({'type':kind,**kw},ensure_ascii=False)+'\n').encode());self.wfile.flush()
        LIVE.update(state='thinking',detail='Ich denke nach')
        event('accepted',turn_id='test-turn',conversation_id='mobile-test',cancellable=True);event('input',content=text)
        event('reasoning_delta',content='Ich ordne die offenen Punkte und schaue nach, was als Nächstes ansteht. Ein kleiner Schritt nach dem anderen.')
        time.sleep(6)
        LIVE.update(state='tool:home_assistant',detail='Home Assistant');time.sleep(6)
        LIVE.update(state='thinking',detail='Ich fasse das zusammen');event('preview_delta',content='Vorläufig');event('preview_reset');time.sleep(1)
        event('delta',content=MD[:60]);event('delta',content=MD[60:])
        audio=io.BytesIO()
        with wave.open(audio,'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(16000);w.writeframes(struct.pack('<h',0)*3200)
        event('audio',audio_wav_base64=base64.b64encode(audio.getvalue()).decode());event('complete',result={'answer':MD,'conversation_id':'mobile-test'});LIVE.update(state='idle',detail='')
        HISTORY.extend([{'role':'user','content':text},{'role':'assistant','content':MD}])
    def do_PATCH(self):
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))));path=urlsplit(self.path).path;self.log(path,body=body)
        rows=SCHEDULES if path.startswith('/api/tasks/') else ITEMS
        item=next((i for i in rows if i['id']==path.rsplit('/',1)[-1]),None)
        if item is None:self.json({'error':'Nicht gefunden'},404);return
        item.update(body);self.json({'task' if rows is SCHEDULES else 'item':item})
    def do_DELETE(self):
        path=urlsplit(self.path).path;self.log(path);rows=SCHEDULES if path.startswith('/api/tasks/') else LISTS if path.startswith('/api/work-lists/') else ITEMS;rows[:]=[i for i in rows if i['id']!=path.rsplit('/',1)[-1]];self.json({'deleted':True})
if __name__=='__main__':
    LOG.write_text('')
    ThreadingHTTPServer(('0.0.0.0',18790),Handler).serve_forever()

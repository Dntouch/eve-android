"""Durable test turns: closing an observer never stops or resubmits the work."""
import json,time,threading,socket
from urllib.parse import urlsplit
from http.server import ThreadingHTTPServer
import mock_eve as fixture

TURNS={}
class Turn:
    def __init__(self,text):
        self.id='turn-'+str(time.time_ns());self.text=text;self.status='running';self.answer='';self.events=[];self.condition=threading.Condition()
        fixture.HISTORY.append({'role':'user','content':text,'turn_id':self.id})
        self.emit('accepted',turn_id=self.id,conversation_id='mobile-test',cancellable=True)
        self.emit('input',content=text)
        self.emit('reasoning_delta',content='Ich prüfe den laufenden Auftrag und bleibe mit ihm verbunden.')
        threading.Thread(target=self.work,daemon=True).start()
    def emit(self,kind,**data):
        with self.condition:self.events.append({'type':kind,**data});self.condition.notify_all()
    def work(self):
        fixture.LIVE.update(state='thinking',detail='Ich denke nach');time.sleep(8)
        fixture.LIVE.update(state='tool:home_assistant',detail='Home Assistant');time.sleep(8)
        self.answer='**Fertig.** Der bestehende Auftrag wurde genau einmal ausgeführt.'
        self.emit('delta',content=self.answer)
        fixture.HISTORY.append({'role':'assistant','content':self.answer,'turn_id':self.id})
        self.status='completed';self.emit('complete',result={'answer':self.answer,'conversation_id':'mobile-test'})
        fixture.LIVE.update(state='idle',detail='')
    def snapshot(self):return {'turn_id':self.id,'conversation_id':'mobile-test','input_text':self.text,'status':self.status,'answer':self.answer}
class Handler(fixture.Handler):
    def observe(self,turn,drop=False):
        self.send_response(200);self.send_header('Content-Type','application/x-ndjson');self.end_headers();index=0
        try:
            while True:
                with turn.condition:
                    if index>=len(turn.events):turn.condition.wait(1)
                    events=turn.events[index:];index=len(turn.events)
                for event in events:
                    self.wfile.write((json.dumps(event)+'\n').encode());self.wfile.flush()
                    if drop and event['type']=='accepted':
                        self.connection.shutdown(socket.SHUT_RDWR);self.connection.close();return
                    if event['type']=='complete':return
        except (BrokenPipeError,ConnectionResetError,OSError):pass
    def do_GET(self):
        path=urlsplit(self.path).path
        if path.startswith('/api/backend-turns/') or path.startswith('/api/message/stream/'):
            self.log(path);turn=TURNS.get(path.rsplit('/',1)[-1])
            if turn is None:self.json({'error':'Turn nicht gefunden'},404);return
            if path.startswith('/api/backend-turns/'):self.json(turn.snapshot())
            else:self.observe(turn)
        else:super().do_GET()
    def do_POST(self):
        if urlsplit(self.path).path=='/api/message/stream':
            body=json.loads(self.rfile.read(int(self.headers['Content-Length'])));self.log('/api/message/stream',text=body['text'])
            turn=Turn(body['text']);TURNS[turn.id]=turn;self.observe(turn,drop=body['text'].startswith('Drop'))
        else:super().do_POST()
if __name__=='__main__':
    fixture.LOG.write_text('');ThreadingHTTPServer(('0.0.0.0',18790),Handler).serve_forever()

"""Protected image fixtures for user and EVE messages; no production access."""
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.parse import urlsplit
import mock_eve as fixture

fixture.HISTORY[:]=[
 {'id':'u-image','role':'user','content':'Mein Bild','attachments':[{'id':'user-picture','media_type':'image/png','url':'/api/conversation-assets/user-picture'}]},
 {'id':'a-image','role':'assistant','content':'Hier ist das Bild von EVE.','attachments':[{'id':'eve-picture','media_type':'image/png','url':'/api/conversation-assets/eve-picture'}]}
]
class Handler(fixture.Handler):
 def do_GET(self):
  path=urlsplit(self.path).path
  if path.startswith('/api/conversation-assets/'):
   authenticated=self.headers.get('X-EVE-Token')=='image-test';self.log(path,authenticated=authenticated)
   if not authenticated:self.json({'error':'Token fehlt'},401);return
   body=Path('app/src/main/res/drawable-nodpi/eve_logo.png').read_bytes()
   self.send_response(200);self.send_header('Content-Type','image/png');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
  else:super().do_GET()
if __name__=='__main__':
 fixture.LOG.write_text('');ThreadingHTTPServer(('0.0.0.0',18790),Handler).serve_forever()

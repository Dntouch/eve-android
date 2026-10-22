import time,json
from android_smoke import adb,nodes,click,set_edit,ROOT,shot
if __name__=='__main__':
 click(desc='EVE-Menü');click(text='Verbindung und Darstellung');set_edit(0,'http://10.0.2.2:18790');set_edit(1,'image-test');adb('shell','input','keyevent','4');click(text='Verbinden');time.sleep(2)
 click(desc='Bild im Chat · antippen zum Vergrößern');time.sleep(1)
 assert any(n.get('text')=='Bild' for n in nodes()),'Image viewer missing'
 shot('eve-image-viewer');click(desc='Bild schließen')
 adb('shell','input','swipe','540','600','540','1400','400');time.sleep(2);shot('eve-chat-images')
 logs=[json.loads(l) for l in (ROOT/'.tooling/mock-requests.jsonl').read_text().splitlines()]
 for path in ['/api/conversation-assets/user-picture','/api/conversation-assets/eve-picture']:
  assert any(r['path']==path and r.get('authenticated') for r in logs),path
 assert not any('Bild nicht geladen' in n.get('text','') for n in nodes())
 adb('shell','am','force-stop','de.eve.mobile');adb('shell','am','start','-n','de.eve.mobile/.MainActivity');time.sleep(2)
 assert any(n.get('content-desc')=='Bild im Chat · antippen zum Vergrößern' for n in nodes())
 print('PASS: protected user/EVE images render, full-screen viewer opens, and history images survive process restart')

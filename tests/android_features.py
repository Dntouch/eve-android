"""Additional emulator coverage. Requires mock_eve.py and configured test URL."""
import time,json
from android_smoke import adb,nodes,find,click,set_edit,shot,ROOT

def open_workspace(tab):
    click(desc='Gespräche, Zeitpläne und Aufgabenlisten');time.sleep(.4)
    if tab!='Gespräche':click(text=tab);time.sleep(.5)

if __name__=='__main__':
    open_workspace('Zeitpläne');click(desc='Zeitplan bearbeiten');set_edit(0,'Ein kurzer Morgenueberblick');adb('shell','input','keyevent','4');click(text='Speichern');time.sleep(.7)
    click(text='Aufgaben');time.sleep(.5);click(desc='Neue Aufgabenliste');set_edit(0,'Android Testliste');set_edit(1,'Nur fuer den lokalen Test');adb('shell','input','keyevent','4');click(text='Anlegen');time.sleep(.7);find(text='Android Testliste');click(text='Android Testliste');time.sleep(.6)
    click(desc='Neue Aufgabe');set_edit(0,'Oberflaeche pruefen');set_edit(1,'Mit Markdown und Icons');adb('shell','input','keyevent','4');click(text='Speichern');time.sleep(.6);find(text='Oberflaeche pruefen')
    click(desc='Aufgabe bearbeiten');set_edit(0,'Oberflaeche ist geprueft');adb('shell','input','keyevent','4');click(text='Speichern');time.sleep(.7);find(text='Oberflaeche ist geprueft')
    click(desc='Aufgabe bearbeiten');click(text='Löschen');click(text='Löschen');time.sleep(.6);find(text='Hier ist alles erledigt')
    click(desc='Arbeitsraum schließen')
    set_edit(0,'Hallo EVE',clear=1);click(desc='Nachricht senden');time.sleep(.5);shot('eve-thinking');time.sleep(6);shot('eve-tool');time.sleep(7)
    # Turn request must be sent exactly once, even while the state monitor polls.
    rows=[json.loads(line) for line in (ROOT/'.tooling/mock-requests.jsonl').read_text().splitlines()]
    turns=[r for r in rows if r.get('path')=='/api/message/stream' and r.get('text')=='Hallo EVE']
    assert len(turns)==1,turns
    edits=[r for r in rows if r.get('method')=='PATCH' and r.get('path')=='/api/tasks/s1' and r.get('body',{}).get('request')=='Ein kurzer Morgenueberblick']
    assert len(edits)==1 and 'schedule' not in edits[0]['body'],edits
    assert any(r.get('state','').startswith('tool:') for r in rows), 'Tool activity was not polled'
    # Completed answers must not reserve space for a thought chip.
    adb('shell','input','swipe','540','650','540','1550','350');time.sleep(.2)
    assert not any(n.get('text','').startswith('Gedanken') for n in nodes()),'Completed answer still contains a thought chip'
    print('PASS: native schedule editor, list/task create-edit-delete, single streamed turn, live thinking/tool visuals and compact thoughts')

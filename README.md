# EVE für Android · 0.2.4

Native Android-Oberfläche für die vorhandene EVE. EVE, Modelle, Gedächtnis und
Haussteuerung laufen weiterhin auf dem bisherigen Rechner. Brainwatch ist nicht
Teil der App.

## Installieren oder aktualisieren

Die signierte APK liegt in `build/EVE-0.2.4.apk`. Android 8.0 oder neuer ist nötig.

1. APK aufs Handy kopieren und in der Dateien-App öffnen.
2. Als Update über die erste Version installieren; eine Deinstallation ist nicht nötig.
3. Bei der ersten Einrichtung: **⋯ → Verbindung und Darstellung → Verbinden**.

Die bestehende Adresse und Einstellungen bleiben bei einem Update mit derselben
Signatur erhalten. Bei einer Neuinstallation ist keine Serveradresse vorbelegt.
Trage die Adresse deines EVE-Servers und gegebenenfalls den Web-Zugriffstoken ein.
EVE muss vom Handy erreichbar sein, im Heimnetz oder beispielsweise über ein VPN.
Die separate Assist-API wird nicht verwendet.

Die öffentliche APK verwendet die Android-Systemzertifikate. Für einen Server mit
selbstsigniertem LAN-Zertifikat ist ein eigener Build mit lokalem Zertifikat nötig:
`EVE_LAN_CERT=/pfad/zum/oeffentlichen-zertifikat.crt ./scripts/build.sh`.
HTTPS- und Hostnamenprüfung bleiben aktiv. Private Schlüssel werden nicht eingebunden.

Mit USB-Debugging alternativ:

```bash
.tooling/sdk/platform-tools/adb install -r build/EVE-0.2.4.apk
```

## Oberfläche und Chat

- EVEs Original-Logo als adaptives App-Symbol, im Kopfbereich und im leeren Chat.
  Die Begrüßung ist neutral und leitet keinen Namen aus dem Gerät oder Benutzerpfad ab.
- EVEs dunkle Flächen, Cyan-Akzente, feine Konturen und Linien-Icons. Passende
  Sidebar-, Bearbeiten-, Löschen-, Listen- und Stimm-Icons übernehmen die EVE-Sprache.
- Kompakte Eingabekarte mit wachsendem Textfeld, Anhang-Plus links sowie Mikrofon
  und Senden rechts, orientiert an der Bedienung der ChatGPT-App.
- Bis zu vier Bilder, einzeln entfernbare Vorschaubilder, Kamera oder Galerie.
  JPEG-Übertragung mit EXIF-Ausrichtung und höchstens 2048 Pixel Kantenlänge.
- Native CommonMark-Darstellung: Überschriften, Fett/Kursiv, verschachtelte Listen,
  Zitate, Trennlinien, durchgestrichener Text, Links und Inline-Code. Codeblöcke
  mit Kopieren und horizontalem Scrollen; Tabellen mit horizontalem Scrollen.
  Rohes HTML wird nicht ausgeführt. Kein WebView.
- Antworten und Code lassen sich kopieren; Text bleibt auswählbar.

## Gedanken und Aktivität

Eine kleine Live-Karte zeigt einen Ausschnitt aus EVEs tatsächlich übermittelten
Gedanken, standardmäßig höchstens zwei Zeilen. Antippen klappt bis zu sieben
Zeilen auf. Es werden höchstens die letzten 240 Unicode-Zeichen angezeigt;
der Ausschnitt ist keine erfundene Zusammenfassung. Nach Abschluss verschwindet die Live-Karte; über fertigen Antworten gibt es
keine zusätzliche Gedanken-Zeile.

Pulsierende, wandernde Konturen visualisieren Aufnahme, Denken, Werkzeug-/
Gedächtnisaktivität und Wiedergabe. Werkzeugaktivität ist violett, Denken cyan,
Gedächtnis blau und Wiedergabe mint. Der zusätzliche Live-Status kommt lesend
von `/api/state`, nur während eines eigenen Turns und bei sichtbarer App.
Diese Schnittstelle zeigt den momentanen EVE-Gesamtstatus; sehr kurze Phasen
zwischen zwei Abfragen können unbemerkt bleiben. Es wird kein Brainwatch geladen.

Animationen lassen sich in den Einstellungen ausschalten und beachten außerdem
die Android-Systemeinstellung für Animationen. Sie stoppen im Hintergrund.

## Gespräche, Zeitpläne und Aufgabenlisten

Das Sidebar-Icon öffnet den Arbeitsraum mit drei Tabs; Zeitpläne und Aufgaben
sind zusätzlich über das Menü erreichbar.

- **Gespräche:** Karten mit Titel, Datum, Nachrichtenanzahl und Markierung des
  aktuellen Gesprächs; Gruppen Heute, Gestern, letzte sieben Tage und älter.
  Suche in geladenen Titeln und Nachladen älterer Gesprächsseiten.
- **Zeitpläne:** aktive und pausierte Aufträge, nächster Termin, Pausieren und
  Fortsetzen, Bearbeiten und Löschen mit Bestätigung. Der Editor unterstützt
  einmalig, Wartezeit, täglich, wöchentlich und Intervall. Uhrzeiten werden in der
  Zeitzone des Zeitplans bearbeitet. Eine reine Textänderung verschiebt den
  bestehenden Termin nicht. Neue Zeitpläne können EVE im Chat aufgetragen werden.
- **Aufgabenlisten:** Listen öffnen/anlegen/löschen; Aufgaben anlegen,
  bearbeiten/löschen, abhaken und wieder öffnen. Erledigtes ist separat einblendbar.
  Beschreibungen unterstützen Markdown. Vorhandene Gesprächsbezüge sind erreichbar.

Ein geöffnetes Gespräch lädt die letzten 200 Nachrichten. Nachladen älterer
Nachrichten ist noch nicht vorhanden. Die Zeitplanansicht lädt bis zu 200 Einträge,
eine Aufgabenliste bis zu 1.000 Aufgaben. Gespeicherte Bildanhänge von Nutzer und
EVE erscheinen als Vorschau im Verlauf; eigene Bilder sind direkt nach dem Senden
sichtbar. Antippen öffnet eine größere Ansicht mit Zoom und Verschieben.
Geschützte Anhänge werden über die EVE-Asset-API mit dem eingestellten Token geladen,
nur bei Bedarf heruntergeladen und in einem begrenzten Arbeitsspeicher-Cache gehalten.
Externe Markdown-Bilder und andere EVE-Artefakte werden nicht eingebettet.

## Sprache und Verbindungen

Mikrofon antippen, sprechen, erneut antippen zum Senden. WAV: 16 kHz, Mono,
16 Bit, höchstens zwei Minuten. Eine laufende Aufnahme wird im Hintergrund
verworfen. EVEs Audiosegmente werden auf dem Handy abgespielt; die Wiedergabe
lässt sich über die Live-Karte oder Einstellungen abschalten.

Beim Minimieren wird nur die Anzeige vom bestätigten Auftrag getrennt; EVE
arbeitet auf dem Rechner weiter. Beim Wiederöffnen stellt die App über die
gespeicherte Turn-ID Status, Gedanken und Antwort wieder her. Das gilt auch nach
einem beendeten App-Prozess. Ein im Vordergrund abgebrochener Antwortstream wird
einmal automatisch wieder angehängt. Dabei werden ausschließlich bestehende
Aufträge gelesen (`GET /api/backend-turns/{id}` und
`GET /api/message/stream/{id}`), niemals die Eingabe erneut abgeschickt.

Ist noch keine Turn-ID angekommen, wird kein Auftrag automatisch wiederholt.
Dann bitte den Verlauf prüfen. Die Wiederaufnahme setzt voraus, dass EVE den
Auftrag noch vorhält; nach einem Serverneustart oder dem Verwerfen alter Turns
kann nur noch der gespeicherte Verlauf verfügbar sein.

Adresse, optionaler Token und Gesprächs-ID liegen im privaten App-Speicher;
Android-Backups sind deaktiviert. Es gibt keine eigene Gesprächsdatenbank auf dem
Handy. Kamera und Audio verwenden den privaten Cache. HTTP bleibt für lokale
Testserver möglich, ist jedoch unverschlüsselt. Push und dauerhafter
Hintergrundbetrieb sind nicht Teil dieser Version.

## Entwicklung und Prüfung

### Git

Ins Repository gehören Quellcode, Ressourcen, Build- und Testskripte sowie die
Dokumentation. `.gitignore` schließt lokale Werkzeuge und SDKs, Build-Ausgaben,
Android-Studio-Einstellungen, Zugangsdaten und Signaturschlüssel aus.
Die fertigen APKs bleiben im lokalen Verzeichnis `build/`.

Nach einem frischen Klonen zuerst `./scripts/setup.sh` ausführen. Den bestehenden
Signaturschlüssel `.tooling/eve-development.jks` separat sichern und bei Bedarf
lokal wiederherstellen, damit Updates auf bereits installierte Apps möglich bleiben.
LAN-Zertifikate bleiben lokal unter `.tooling/` und werden nur bei expliziter
Angabe von `EVE_LAN_CERT` eingebunden. Private Schlüssel gehören nicht ins Git.

### Werkzeuge und Tests

Java 17, Android SDK 35 / Build Tools 35.0.0. Die einzigen Laufzeitbibliotheken
sind CommonMark 0.22.0 mit GFM-Tabellen und Durchstreichungen. Diese Version
vermeidet neuere Java-Collection-Aufrufe, die Android 8 ohne Desugaring fehlen.
Die Lizenz wird unter `assets/commonmark-LICENSE.txt` in der APK mitgeliefert.

```bash
./scripts/setup.sh       # lokale Werkzeuge, SDK-Lizenzen und Parser-JARs
./scripts/test.sh        # Transport-, Markdown-, Gedanken- und Workspace-Tests
./scripts/build.sh       # bauen, ausrichten, signieren, Signatur prüfen
```

Alle Werkzeuge liegen unter `.tooling/`; eigene Installationen sind über
`JAVA_HOME` und `ANDROID_SDK_ROOT` nutzbar. Alternativ kann das Gradle-Projekt in
Android Studio geöffnet werden. Dessen Standard-Debug-Signatur unterscheidet
sich von der lokal ausgelieferten Signatur.

**`.tooling/eve-development.jks` für weitere installierbare Updates aufbewahren.**
Das Passwort liegt lokal in `.tooling/signing-password`; alternativ lässt sich
über `EVE_SIGNING_PASSWORD_FILE` eine andere Passwortdatei angeben. Bei einem neuen
Schlüssel wird ein zufälliges Passwort erzeugt. Schlüssel und Passwortdatei werden
nicht eingecheckt und müssen beide separat gesichert werden. Eine andere Signatur
kann nicht über die bestehende App installiert werden.

Die Tests prüfen insbesondere:

- UTF-8, Tokenheader, Bilder, Gesprächsbindung, NDJSON und fehlende Wiederholungen;
- Markdown einschließlich Tabellen, Code, unvollständigen Streams und sicheren Links;
- Gesprächssuche und Zeitgruppen über Zeitzonengrenzen;
- unveränderte, kurze Gedanken-Ausschnitte ohne zerteilte Unicode-Zeichen;
- Aufgabenstatus, Listenbindung, Pagination und Pausieren ohne Terminänderung.

Für Android-Tests wird ausschließlich der getrennte Server
`python3 tests/mock_eve.py` benutzt; Emulator-Adresse `http://10.0.2.4:18790`.
`tests/android_smoke.py` prüft die Grundnavigation, Zeitpläne und Aufgaben.
`tests/android_features.py` ergänzt Editor, CRUD und Gedanken-/Werkzeugphasen.
Diese Tests senden keine Aufträge an die echte EVE.

Der Emulator läuft auf diesem Host mit `-gpu host -feature -Vulkan` und sichtbarem
Fenster. Softwaregrafik und Headless-Varianten stürzten beim Booten ab. Die
Installations- und Bedienungsprüfung findet unter Android 15 / API 35 statt.
Die erste APK wurde zusätzlich vom Nutzer erfolgreich auf seinem Handy gestartet.

`tests/recovery_eve.py` bildet langlebige Server-Aufträge unabhängig vom
Antwortstream ab. Mit `tests/android_recovery.py` werden Minimieren/Wiederöffnen,
Prozessneustart und ein erzwungener Stream-Abbruch geprüft. Das Request-Protokoll
muss jeweils genau eine ursprüngliche POST-Anfrage und nur GETs zur Wiederaufnahme
enthalten.

`tests/images_eve.py` und `tests/android_images.py` prüfen Bildanhänge beider
Rollen mit verpflichtendem Token, Großansicht und Wiederladen nach Prozessneustart.
Die Bilder werden ausschließlich vom lokalen Testserver abgerufen.

## Veröffentlichungen

APKs werden über [GitHub Releases](https://github.com/Dntouch/eve-android/releases)
bereitgestellt.

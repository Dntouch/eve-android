# EVE für Android · 0.2.4

Native Android-App für EVE mit Chat, Bildanhängen, Spracheingabe, Zeitplänen und
Aufgabenlisten. Die App verbindet sich mit einem bestehenden EVE-Server.

## Installation und Verbindung

Voraussetzung: Android 8.0 oder neuer und ein vom Handy erreichbarer EVE-Server,
beispielsweise im Heimnetz oder über ein VPN.

1. APK installieren. Downloads werden über [GitHub Releases](https://github.com/Dntouch/eve-android/releases) bereitgestellt.
2. **⋯ → Verbindung und Darstellung** öffnen.
3. Serveradresse und gegebenenfalls den EVE-Web-Zugriffstoken eintragen und **Verbinden** wählen.

Bei Updates mit derselben Signatur bleiben Einstellungen und Serveradresse erhalten.

Die App vertraut den Android-Systemzertifikaten. Für Server mit selbstsigniertem
Zertifikat ist ein eigener Build nötig; siehe [Entwicklung](#entwicklung).
HTTP-Verbindungen sind möglich, übertragen aber auch Tokens unverschlüsselt.

## Funktionen

- **Chat:** Markdown mit Tabellen und Codeblöcken, kopierbare Antworten und Code.
- **Bilder:** bis zu vier Anhänge aus Kamera oder Galerie, Vorschauen im Verlauf
  und Großansicht mit Zoom. Bilder werden vor dem Senden auf höchstens 2048 Pixel
  Kantenlänge verkleinert.
- **Sprache:** Mikrofon antippen, sprechen und erneut antippen zum Senden.
  Aufnahmen sind auf zwei Minuten begrenzt; EVEs Audioantworten lassen sich abspielen.
- **Live-Aktivität:** ausklappbarer Gedankenausschnitt und Anzeigen für Denken,
  Werkzeuge, Gedächtnis und Wiedergabe. Animationen lassen sich ausschalten.
- **Gespräche:** Verlauf, Suche in geladenen Titeln und Nachladen älterer Gesprächsseiten.
- **Zeitpläne:** anzeigen, bearbeiten, pausieren, fortsetzen und löschen.
  Neue Zeitpläne lassen sich EVE im Chat auftragen.
- **Aufgabenlisten:** Listen und Aufgaben anlegen, bearbeiten, löschen und abhaken.

Gespräche, Zeitpläne und Aufgabenlisten sind über die Seitenleiste erreichbar.

## Verhalten und Grenzen

Beim Wechsel in den Hintergrund arbeitet ein bestätigter Auftrag auf dem Server
weiter. Beim Wiederöffnen stellt die App den Status und die Antwort wieder her,
solange der Server den Auftrag noch vorhält. Eingaben werden dabei nicht erneut
abgeschickt. Ist noch keine Auftragsbestätigung angekommen, bitte den Verlauf prüfen.
Eine laufende Mikrofonaufnahme wird im Hintergrund verworfen.

Ein Gespräch lädt die letzten 200 Nachrichten; ältere Nachrichten lassen sich
innerhalb des Gesprächs noch nicht nachladen. Die Zeitplanansicht lädt bis zu
200 Einträge, eine Aufgabenliste bis zu 1.000 Aufgaben. Externe Markdown-Bilder
und andere EVE-Artefakte werden nicht eingebettet. Push-Benachrichtigungen werden
noch nicht unterstützt.

Serveradresse, optionaler Token und Gesprächs-ID liegen im privaten App-Speicher.
Android-Backups sind deaktiviert. Der Gesprächsverlauf bleibt auf dem EVE-Server;
Bild- und Audioinhalte werden auf dem Handy zwischengespeichert.

## Entwicklung

Voraussetzungen: Java 17, Android SDK 35, Build Tools 35.0.0, Python 3 und curl.

```bash
./scripts/setup.sh       # JDK, Android SDK und Abhängigkeiten einrichten
./scripts/test.sh        # Java-Tests ausführen
./scripts/build.sh       # APK bauen und signieren
```

Werkzeuge und Abhängigkeiten werden unter `.tooling/` abgelegt. Vorhandene
Installationen können über `JAVA_HOME` und `ANDROID_SDK_ROOT` verwendet werden.
Die fertige APK liegt in `build/EVE-0.2.4.apk`. Das Gradle-Projekt lässt sich
auch in Android Studio öffnen.

Für einen Build mit eigenem LAN-Zertifikat:

```bash
EVE_LAN_CERT=/pfad/zum/zertifikat.crt ./scripts/build.sh
```

Dabei das öffentliche Server- oder CA-Zertifikat verwenden. HTTPS- und
Hostnamenprüfung bleiben aktiv.

Der Build erzeugt beim ersten Aufruf einen lokalen Signaturschlüssel mit zufälligem
Passwort. Schlüssel `.tooling/eve-development.jks` und Passwortdatei
`.tooling/signing-password` separat sichern: Updates einer installierten App
müssen mit demselben Schlüssel signiert werden. Über `EVE_SIGNING_PASSWORD_FILE`
lässt sich eine andere Passwortdatei angeben. Android Studio verwendet standardmäßig
einen eigenen Debug-Schlüssel.

Die Java-Tests prüfen Transport, Markdown, Bildverweise, Gedankenanzeige und
Workspace-Funktionen. Die Android-Testskripte unter `tests/` verwenden lokale
Testserver für Navigation, Bearbeitung, Wiederaufnahme und Bildanzeige.

Drittanbieter-Lizenzen stehen in [THIRD_PARTY.md](THIRD_PARTY.md).

#!/usr/bin/env bash
# Dependency-free build using the official Android SDK and a JDK 17+.
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_ROOT="$PWD"
TASK_SDK="${ANDROID_SDK_ROOT:-$TASK_ROOT/.tooling/sdk}"
TASK_JAVA="${JAVA_HOME:-$(find "$TASK_ROOT/.tooling/jdk" -mindepth 1 -maxdepth 1 -type d | head -1)}"
TASK_BT="$TASK_SDK/build-tools/35.0.0"
TASK_ANDROID="$TASK_SDK/platforms/android-35/android.jar"
export JAVA_HOME="$TASK_JAVA"
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf build/classes build/dex build/generated
mkdir -p build/classes build/dex build/generated
python3 - <<'PYMANIFEST'
from pathlib import Path
s=Path("app/src/main/AndroidManifest.xml").read_text()
s=s.replace("<manifest ", '<manifest package="de.eve.mobile" ', 1)
Path("build/AndroidManifest.xml").write_text(s)
PYMANIFEST
# Public builds use system trust. A private LAN certificate is opt-in only.
rm -rf build/res
cp -R app/src/main/res build/res
if [[ -n "${EVE_LAN_CERT:-}" ]]; then
    mkdir -p build/res/raw
    cp "$EVE_LAN_CERT" build/res/raw/eve_lan.crt
    python3 - <<'PYCERT'
from pathlib import Path
p = Path('build/res/xml/network_security_config.xml')
p.write_text(p.read_text().replace('<certificates src="system"/>', '<certificates src="system"/><certificates src="@raw/eve_lan"/>'))
PYCERT
fi
"$TASK_BT/aapt2" compile --dir build/res -o build/resources.zip
"$TASK_BT/aapt2" link -o build/resources.apk -I "$TASK_ANDROID" --manifest build/AndroidManifest.xml --java build/generated -A app/src/main/assets build/resources.zip
find app/src/main/java build/generated -name '*.java' -print0 | xargs -0 "$JAVA_HOME/bin/javac" --release 17 -classpath "$TASK_ANDROID:.tooling/libs/*" -d build/classes
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
"$JAVA_HOME/bin/java" -cp "$TASK_BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$TASK_ANDROID" --min-api 26 --output build/dex build/classes.jar .tooling/libs/*.jar
python3 - <<'PY'
from pathlib import Path
import zipfile
Path('build/eve-unsigned.apk').write_bytes(Path('build/resources.apk').read_bytes())
with zipfile.ZipFile('build/eve-unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for p in Path('build/dex').glob('*.dex'):z.write(p,p.name)
PY
"$TASK_BT/zipalign" -f -p 4 build/eve-unsigned.apk build/eve-aligned.apk
# Keep the key and password file outside Git for future sideload updates.
TASK_PASSWORD_FILE="${EVE_SIGNING_PASSWORD_FILE:-$TASK_ROOT/.tooling/signing-password}"
if [[ ! -f .tooling/eve-development.jks ]]; then
    if [[ ! -f "$TASK_PASSWORD_FILE" ]]; then
        (umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$TASK_PASSWORD_FILE")
    fi
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore .tooling/eve-development.jks -storepass:file "$TASK_PASSWORD_FILE" -keypass:file "$TASK_PASSWORD_FILE" -alias eve -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=EVE Local Development'
fi
if [[ ! -s "$TASK_PASSWORD_FILE" ]]; then
    echo "Missing signing password file: $TASK_PASSWORD_FILE" >&2
    exit 1
fi
"$JAVA_HOME/bin/java" -jar "$TASK_BT/lib/apksigner.jar" sign --ks .tooling/eve-development.jks --ks-pass "file:$TASK_PASSWORD_FILE" --out build/EVE-0.2.4.apk build/eve-aligned.apk
"$JAVA_HOME/bin/java" -jar "$TASK_BT/lib/apksigner.jar" verify --verbose build/EVE-0.2.4.apk
"$TASK_BT/zipalign" -c -p 4 build/EVE-0.2.4.apk

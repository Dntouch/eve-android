#!/usr/bin/env bash
# Downloads are kept inside this project. Requires curl and Python 3.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tooling/downloads
if [[ ! -d .tooling/jdk ]]; then
    curl -fL --retry 2 -o .tooling/downloads/jdk.tar.gz 'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse'
    python3 - <<'PY'
import tarfile
with tarfile.open('.tooling/downloads/jdk.tar.gz') as t:t.extractall('.tooling/jdk',filter='data')
PY
fi
if [[ ! -d .tooling/sdk/cmdline-tools/latest ]]; then
    curl -fL --retry 2 -o .tooling/downloads/android-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
    python3 - <<'PY'
from pathlib import Path
import zipfile
p=Path('.tooling/sdk/cmdline-tools')
with zipfile.ZipFile('.tooling/downloads/android-tools.zip') as z:z.extractall(p)
(p/'cmdline-tools').rename(p/'latest')
PY
fi
TASK_JAVA_DIR="$(find "$PWD/.tooling/jdk" -mindepth 1 -maxdepth 1 -type d | head -1)"
TASK_CP="$PWD/.tooling/sdk/cmdline-tools/latest/lib/sdkmanager-classpath.jar"
echo 'Android SDK license agreements:'
"$TASK_JAVA_DIR/bin/java" -cp "$TASK_CP" com.android.sdklib.tool.sdkmanager.SdkManagerCli --sdk_root="$PWD/.tooling/sdk" --licenses
"$TASK_JAVA_DIR/bin/java" -cp "$TASK_CP" com.android.sdklib.tool.sdkmanager.SdkManagerCli --sdk_root="$PWD/.tooling/sdk" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if [[ ! -f .tooling/downloads/json.jar ]]; then
    curl -fL -o .tooling/downloads/json.jar https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar
fi

mkdir -p .tooling/libs
for artifact in commonmark commonmark-ext-gfm-tables commonmark-ext-gfm-strikethrough; do
    if [[ ! -f ".tooling/libs/$artifact.jar" ]]; then
        curl -fL -o ".tooling/libs/$artifact.jar" "https://repo.maven.apache.org/maven2/org/commonmark/$artifact/0.22.0/$artifact-0.22.0.jar"
    fi
done

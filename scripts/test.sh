#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_JAVA="${JAVA_HOME:-$(find "$PWD/.tooling/jdk" -mindepth 1 -maxdepth 1 -type d | head -1)}"
mkdir -p build/tests
"$TASK_JAVA/bin/javac" --release 17 -cp ".tooling/downloads/json.jar:.tooling/libs/*" -d build/tests app/src/main/java/de/eve/mobile/ChatImages.java app/src/main/java/de/eve/mobile/EveApi.java app/src/main/java/de/eve/mobile/MarkdownSource.java app/src/main/java/de/eve/mobile/ThoughtText.java app/src/main/java/de/eve/mobile/WorkspaceData.java app/src/main/java/de/eve/mobile/WorkspaceApi.java tests/TransportTest.java tests/ContentTest.java tests/WorkspaceTest.java
"$TASK_JAVA/bin/java" -cp "build/tests:.tooling/downloads/json.jar:.tooling/libs/*" de.eve.mobile.TransportTest

"$TASK_JAVA/bin/java" -cp "build/tests:.tooling/downloads/json.jar:.tooling/libs/*" de.eve.mobile.ContentTest

"$TASK_JAVA/bin/java" -cp "build/tests:.tooling/downloads/json.jar:.tooling/libs/*" de.eve.mobile.WorkspaceTest

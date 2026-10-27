#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
task_classes=$(mktemp -d)
trap 'rm -rf "$task_classes"' EXIT
java com.sun.tools.javac.Main -d "$task_classes" app/src/main/java/com/family/recorder/LoopStore.java tests/LoopStoreTest.java tests/ParseJava.java
java -cp "$task_classes" com.family.recorder.LoopStoreTest
java -cp "$task_classes" ParseJava app/src/main/java/com/family/recorder/*.java
python3 - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
for path in Path('app/src/main').rglob('*.xml'): ET.parse(path)
print('PASS: Android XML well formed')
PY

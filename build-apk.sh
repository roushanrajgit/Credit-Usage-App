#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then echo 'Set ANDROID_HOME to your Android SDK folder.' >&2; exit 1; fi
TOOLS="$SDK/build-tools/36.0.0"
ANDROID="$SDK/platforms/android-36/android.jar"
mkdir -p build/classes build/dex build/generated
"$TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
sed 's/<manifest /<manifest package="com.usagewidget" /' app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
"$TOOLS/aapt2" link -o build/base.apk -I "$ANDROID" --manifest build/AndroidManifest.xml --java build/generated --min-sdk-version 26 --target-sdk-version 36 --version-code 4 --version-name 0.3.1-preview build/resources.zip -A app/src/main/assets
find app/src/main/java build/generated -name '*.java' > build/sources.txt
if command -v javac >/dev/null; then
  javac -source 8 -target 8 -bootclasspath "$ANDROID" -classpath "$TOOLS/core-lambda-stubs.jar" -d build/classes @build/sources.txt
elif [[ -n "${ECJ_JAR:-}" ]]; then
  java -jar "$ECJ_JAR" -1.8 -nowarn -bootclasspath "$ANDROID" -classpath "$TOOLS/core-lambda-stubs.jar" -d build/classes @build/sources.txt
else echo 'Install JDK 17+ (or set ECJ_JAR to Eclipse compiler jar).' >&2; exit 1; fi
python3 - <<'ZIP_CLASSES'
from pathlib import Path
import zipfile
with zipfile.ZipFile('build/classes.jar','w') as archive:
    for f in Path('build/classes').rglob('*.class'):
        archive.write(f,f.relative_to('build/classes'))
ZIP_CLASSES
"$TOOLS/d8" --lib "$ANDROID" --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
KEY="${USAGE_WIDGET_KEYSTORE:?Set USAGE_WIDGET_KEYSTORE to your private signing keystore}"
: "${USAGE_WIDGET_STORE_PASSWORD:?Set USAGE_WIDGET_STORE_PASSWORD}"
: "${USAGE_WIDGET_KEY_PASSWORD:?Set USAGE_WIDGET_KEY_PASSWORD}"
: "${USAGE_WIDGET_KEY_ALIAS:?Set USAGE_WIDGET_KEY_ALIAS}"
export USAGE_WIDGET_STORE_PASSWORD USAGE_WIDGET_KEY_PASSWORD
"$TOOLS/apksigner" sign --ks "$KEY" --ks-key-alias "$USAGE_WIDGET_KEY_ALIAS" --ks-pass env:USAGE_WIDGET_STORE_PASSWORD --key-pass env:USAGE_WIDGET_KEY_PASSWORD --out build/UsageWidget-preview.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose build/UsageWidget-preview.apk

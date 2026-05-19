#!/usr/bin/env bash
set -euo pipefail

SDK="$HOME/Library/Android/sdk"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
PROJ="$(cd "$(dirname "$0")" && pwd)"
BUILD="$PROJ/build"
PKG="works.huse.picoxr"
PKG_PATH="${PKG//.//}"
APK_UNSIGNED="$BUILD/app-unsigned.apk"
APK_ALIGNED="$BUILD/app-aligned.apk"
APK_SIGNED="$BUILD/app.apk"
KEYSTORE="$PROJ/debug.keystore"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex"

echo "[1/5] Compile resources"
"$BT/aapt2" compile --dir "$PROJ/res" -o "$BUILD/res.zip"

echo "[2/5] Link resources -> generate R.java + base APK"
"$BT/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -o "$APK_UNSIGNED" \
  --java "$BUILD" \
  "$BUILD/res.zip"

echo "[3/5] javac"
javac -source 1.8 -target 1.8 \
  -bootclasspath "$PLATFORM" \
  -d "$BUILD/classes" \
  $(find "$PROJ/src" "$BUILD/$PKG_PATH" -name '*.java' 2>/dev/null)

echo "[4/5] d8 -> dex"
"$BT/d8" --lib "$PLATFORM" --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')

echo "[4b/5] add classes.dex to APK"
( cd "$BUILD/dex" && zip -q "$APK_UNSIGNED" classes.dex )

echo "[5/5] align + sign"
"$BT/zipalign" -f -p 4 "$APK_UNSIGNED" "$APK_ALIGNED"

if [ ! -f "$KEYSTORE" ]; then
  echo "Generating debug keystore..."
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$APK_SIGNED" "$APK_ALIGNED"

echo
echo "Built: $APK_SIGNED"

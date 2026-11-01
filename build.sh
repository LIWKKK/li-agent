#!/bin/bash
set -e
cd "$(dirname "$0")"
TC="$HOME/android-toolchain"
AJ=$TC/android-sdk/platforms/android-34/android.jar
BT=$TC/android-sdk/build-tools/34.0.0
export JAVA_HOME=$TC/jdk17
export PATH=$JAVA_HOME/bin:$BT:$PATH
rm -rf build gen && mkdir -p build/classes gen
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/unaligned.apk -I $AJ --manifest AndroidManifest.xml \
  --java gen --min-sdk-version 23 --target-sdk-version 30 --version-code 3 --version-name 0.3 build/res.zip
javac -encoding UTF-8 --release 8 -Xlint:-options -cp $AJ -d build/classes \
  src/site/liweike/watchai/*.java gen/site/liweike/watchai/R.java
$JAVA_HOME/bin/jar --create --file build/classes.jar -C build/classes .
$BT/d8 --release --min-api 23 --lib $AJ --output build build/classes.jar
cp build/unaligned.apk build/withdex.apk
$JAVA_HOME/bin/jar --update --file build/withdex.apk -C build classes.dex
$BT/zipalign -f 4 build/withdex.apk build/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias watchai -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=WatchAI" >/dev/null 2>&1
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias watchai --min-sdk-version 23 --out WatchAI.apk build/aligned.apk
$BT/apksigner verify WatchAI.apk && echo "OK: $(stat -c %s WatchAI.apk) bytes"

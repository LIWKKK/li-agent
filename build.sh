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
  --java gen --min-sdk-version 23 --target-sdk-version 30 --version-code 4 --version-name 0.4 build/res.zip
# 配置注入：仓库里的 Net.java 只有占位符，真实地址和令牌在构建时写入源码副本。
# 用法：LI_LAN=http://192.168.x.x:19490 LI_TOKEN=xxx bash build.sh
# 不设这两个变量时用占位符构建，产物只能看界面、连不上中继。
SRC=src/site/liweike/watchai
if [ -n "${LI_LAN:-}" ] || [ -n "${LI_TOKEN:-}" ]; then
  mkdir -p build/src
  cp $SRC/*.java build/src/
  SRC=build/src
  [ -n "${LI_LAN:-}" ]   && sed -i "s|http://192.168.1.100:19490|$LI_LAN|"   $SRC/Net.java
  [ -n "${LI_TOKEN:-}" ] && sed -i "s|\"RELAY_TOKEN\"|\"$LI_TOKEN\"|"        $SRC/Net.java
  [ -n "${LI_V6:-}" ]    && sed -i "s|2001:db8::relay|$LI_V6|"                $SRC/Net.java
  echo "注入配置：LAN=$LI_LAN"
fi

javac -encoding UTF-8 --release 8 -Xlint:-options -cp $AJ -d build/classes \
  $SRC/*.java gen/site/liweike/watchai/R.java
$JAVA_HOME/bin/jar --create --file build/classes.jar -C build/classes .
$BT/d8 --release --min-api 23 --lib $AJ --output build build/classes.jar
cp build/unaligned.apk build/withdex.apk
$JAVA_HOME/bin/jar --update --file build/withdex.apk -C build classes.dex
$BT/zipalign -f 4 build/withdex.apk build/aligned.apk
# 签名密钥固定在 ~/.li-agent-signing，不进仓库也不随 build 目录清掉。
# 换 key 会让已装的版本无法覆盖升级（signatures do not match），所以必须保持同一个。
KS="${LI_KEYSTORE:-$HOME/.li-agent-signing/li-agent.keystore}"
mkdir -p "$(dirname "$KS")"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
  -alias watchai -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=WatchAI" >/dev/null 2>&1
$BT/apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias watchai --min-sdk-version 23 --out WatchAI.apk build/aligned.apk
$BT/apksigner verify WatchAI.apk && echo "OK: $(stat -c %s WatchAI.apk) bytes"

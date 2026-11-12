#!/usr/bin/env bash
# 手工构建 Android APK（不依赖 Gradle/Android Studio）
# 依赖：Temurin JDK 17、Android SDK platform-34 + build-tools 34.0.0
# 注意：aapt2 在含中文的路径下会报 "failed to open directory"，
#       因此先把工程镜像到纯 ASCII 目录再构建，产物最后拷回项目。
set -euo pipefail

export MSYS_NO_PATHCONV=1
BASE="C:/Users/30786/AppData/Local/agri-android"
SDK="$BASE/sdk"
JDK="$BASE/jdk/jdk-17.0.21+9"
BT="$SDK/build-tools/34.0.0"
AJAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
PROJ="C:/Users/30786/Desktop/毕设农产品/frontend/app-shell"
SRC="$BASE/shell-src"
BUILD="$SRC/build"
KS="$BASE/agritrace.keystore"
KSPROPS="$BASE/keystore.properties"
OUTDIR="$PROJ/out"

APP_NAME="agritrace"
VER_NAME="1.0.0"
VER_CODE="2"  # 递增才能覆盖安装：签名不变、版本名不变，仅内部行为更新

rm -rf "$SRC"
mkdir -p "$SRC" "$OUTDIR"
cp -r "$PROJ/res" "$PROJ/src" "$PROJ/AndroidManifest.xml" "$SRC/"

# 版本号的唯一来源是上面的 VER_CODE / VER_NAME。
# 清单里若写了固定的 versionCode，会盖掉 aapt2 的 --version-code，
# 导致"改了脚本版本号、装上去还是旧版本号、无法覆盖安装"，所以构建时同步一次。
sed -i "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"$VER_CODE\"/; s/android:versionName=\"[^\"]*\"/android:versionName=\"$VER_NAME\"/" "$SRC/AndroidManifest.xml"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex"

echo "==> 1/7 编译资源"
"$BT/aapt2.exe" compile --dir "$SRC/res" -o "$BUILD/res.zip"

echo "==> 2/7 链接资源与清单"
"$BT/aapt2.exe" link \
  -o "$BUILD/base.apk" \
  -I "$AJAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  --min-sdk-version 23 \
  --target-sdk-version 34 \
  --version-code "$VER_CODE" \
  --version-name "$VER_NAME" \
  --auto-add-overlay

echo "==> 3/7 编译 Java"
"$JDK/bin/javac" -encoding UTF-8 -source 11 -target 11 \
  -classpath "$AJAR" \
  -d "$BUILD/classes" \
  "$SRC/src/com/agri/trace/MainActivity.java"

echo "==> 4/7 dex 转换"
"$BT/d8.bat" --release --lib "$AJAR" --min-api 23 --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class' | tr '\n' ' ')

echo "==> 5/7 合并 dex 入 APK"
python - "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$BUILD/merged.apk" <<'PY'
import shutil, sys, zipfile
src, dex, dst = sys.argv[1:4]
shutil.copyfile(src, dst)
with zipfile.ZipFile(dst, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print('    classes.dex 已写入')
PY

echo "==> 6/7 对齐"
"$BT/zipalign.exe" -f -p 4 "$BUILD/merged.apk" "$BUILD/aligned.apk"

echo "==> 7/7 签名"
if [ ! -f "$KS" ]; then
  PASS=$(python -c "import secrets,string;print(''.join(secrets.choice(string.ascii_letters+string.digits) for _ in range(20)))")
  "$JDK/bin/keytool" -genkeypair -v \
    -keystore "$KS" -alias agritrace -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=AgriTrace, OU=AgriTrace, O=AgriTrace, L=Beijing, ST=Beijing, C=CN" >/dev/null 2>&1
  printf 'storepass=%s\nkeypass=%s\nalias=agritrace\n' "$PASS" "$PASS" > "$KSPROPS"
  echo "    已生成签名证书：$KS"
fi
PASS=$(grep '^storepass=' "$KSPROPS" | cut -d= -f2)

FINAL="$OUTDIR/$APP_NAME-$VER_NAME.apk"
rm -f "$FINAL"
"$BT/apksigner.bat" sign \
  --ks "$KS" --ks-key-alias agritrace \
  --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --min-sdk-version 23 \
  --out "$BUILD/signed.apk" "$BUILD/aligned.apk"
cp "$BUILD/signed.apk" "$FINAL"

"$BT/apksigner.bat" verify --print-certs "$FINAL" | head -6
ls -lh "$FINAL"
echo "构建完成：$FINAL"

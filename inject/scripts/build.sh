#!/bin/bash
# 一体包构建：把 GM 面板 + Python 服务端 注入游戏 APK
# 环境变量: BASE_APK（原始游戏包路径）  OUT_APK（输出路径）  KS_* （签名）
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
WORK=${WORK:-/tmp/hlwbuild}
BASE_APK=${BASE_APK:?需要 BASE_APK}
OUT_APK=${OUT_APK:-$ROOT/out/hlw_onepkg.apk}
mkdir -p "$WORK" out
bash "$ROOT/scripts/setup_tools.sh"

echo "== 1. 反编译游戏 dex =="
python3 - "$BASE_APK" "$WORK/base.dex" <<'PY'
import zipfile, sys
open(sys.argv[2],'wb').write(zipfile.ZipFile(sys.argv[1]).read('classes.dex'))
PY
rm -rf "$WORK/base"; java -jar /tmp/tools/baksmali.jar d "$WORK/base.dex" -o "$WORK/base"

echo "== 2. 合入 Chaquopy 运行时类 =="
cp -r "$ROOT/prebuilt/smali/chaquo" "$WORK/base/com/"

echo "== 3. 编译 GM 面板 =="
rm -rf "$WORK/cls" "$WORK/gmdex"; mkdir -p "$WORK/cls" "$WORK/gmdex"
javac -source 8 -target 8 -bootclasspath /tmp/tools/android.jar -cp "$ROOT/stubs" \
      -d "$WORK/cls" $(find "$ROOT/stubs" -name '*.java') $(find "$ROOT/src" -name '*.java')
java -cp /tmp/tools/r8.jar com.android.tools.r8.D8 --min-api 21 \
     --lib /tmp/tools/android.jar --output "$WORK/gmdex" $(find "$WORK/cls/com/hlw" -name '*.class')
rm -rf "$WORK/gm"; java -jar /tmp/tools/baksmali.jar d "$WORK/gmdex/classes.dex" -o "$WORK/gm"
rm -rf "$WORK/base/com/hlw/gm"; mkdir -p "$WORK/base/com/hlw"
cp -r "$WORK/gm/com/hlw/gm" "$WORK/base/com/hlw/"

echo "== 4. 注入 EntryActivity 调用 =="
python3 - "$WORK/base/com/ada/ane/sdk/EntryActivity.smali" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
anchor = '    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n'
call = '\n    invoke-static {p0}, Lcom/hlw/gm/GmAuto;->start(Landroid/app/Activity;)V\n'
assert anchor in s, '找不到注入点'
if 'GmAuto' not in s:
    s = s.replace(anchor, anchor + call, 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('已注入')
else:
    print('已存在')
PY

echo "== 5. 汇编 dex =="
rm -f "$WORK/classes.dex"; java -jar /tmp/tools/smali.jar a "$WORK/base" -o "$WORK/classes.dex"

echo "== 6. 打包（替换 dex + 加 Python 运行时）=="
python3 - "$BASE_APK" "$ROOT/prebuilt" "$WORK/classes.dex" "$OUT_APK" <<'PY'
import zipfile, sys, os
base, pre, dex, out = sys.argv[1:5]
def zi(name):
    z = zipfile.ZipInfo(name, date_time=(2020,1,1,0,0,0)); z.compress_type = zipfile.ZIP_DEFLATED
    z.external_attr = 0o644 << 16; return z
zd = open(dex,'rb').read()
zg = zipfile.ZipFile(base); zo = zipfile.ZipFile(out, 'w')
exist = set(zg.namelist())
for it in zg.infolist():
    nm = it.filename
    if nm.startswith('META-INF/') and nm.upper().endswith(('.SF','.RSA','.DSA','.MF')): continue
    data = zd if nm == 'classes.dex' else zg.read(nm)
    z = zipfile.ZipInfo(nm, date_time=it.date_time); z.compress_type = it.compress_type
    z.external_attr = it.external_attr
    zo.writestr(z, data)
for root, _, files in os.walk(pre):
    for f in files:
        full = os.path.join(root, f)
        rel = os.path.relpath(full, pre)
        apkname = rel if rel.startswith(('lib/','assets/')) else None
        if rel.startswith('lib/'):   apkname = rel
        elif rel.startswith('assets/'): apkname = rel
        else: continue
        if apkname in exist: continue
        zo.writestr(zi(apkname), open(full,'rb').read())
zo.close(); zg.close()
print('打包完成', out, os.path.getsize(out))
PY

echo "== 7. 对齐与签名 =="
zipalign -p -f 4 "$OUT_APK" "${OUT_APK%.apk}_aligned.apk"
if [ -n "$KS_BASE64" ]; then
  echo "$KS_BASE64" | base64 -d > "$WORK/ks.jks"
  apksigner sign --ks "$WORK/ks.jks" --ks-pass "pass:${KS_PASS}" --key-pass "pass:${KS_PASS}" \
    --ks-key-alias "${KS_ALIAS:-huluwa}" --out "${OUT_APK%.apk}_signed.apk" "${OUT_APK%.apk}_aligned.apk"
  echo "签名完成: ${OUT_APK%.apk}_signed.apk"
else
  echo "未提供 KS_BASE64，跳过签名"
fi

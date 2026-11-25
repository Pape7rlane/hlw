#!/bin/bash
# 纯 Java 一体包构建：Java 服务端 + GM 面板注入游戏 APK（不依赖 Python 运行时）
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
WORK=${WORK:-/tmp/hlwjava}
BASE_APK=${BASE_APK:?需要 BASE_APK}
OUT_APK=${OUT_APK:-$ROOT/out/hlw_java.apk}
mkdir -p "$WORK" out

bash "$ROOT/inject/scripts/setup_tools.sh"

echo "== 1. 编译 Java 服务端与 GM 面板 =="
rm -rf "$WORK/cls" "$WORK/dex"; mkdir -p "$WORK/cls" "$WORK/dex"
javac -source 8 -target 8 -bootclasspath /tmp/tools/android.jar \
      -d "$WORK/cls" $(find "$ROOT/javasrv/src" -name '*.java')
java -cp /tmp/tools/r8.jar com.android.tools.r8.D8 --min-api 21 \
     --lib /tmp/tools/android.jar --output "$WORK/dex" $(find "$WORK/cls" -name '*.class')

echo "== 2. 反编译游戏 dex =="
python3 - "$BASE_APK" "$WORK/base.dex" <<'PY'
import zipfile, sys
open(sys.argv[2],'wb').write(zipfile.ZipFile(sys.argv[1]).read('classes.dex'))
PY
rm -rf "$WORK/base"; java -jar /tmp/tools/baksmali.jar d "$WORK/base.dex" -o "$WORK/base"

echo "== 3. 合入我们的类 =="
rm -rf "$WORK/ours"; java -jar /tmp/tools/baksmali.jar d "$WORK/dex/classes.dex" -o "$WORK/ours"
mkdir -p "$WORK/base/com/hlw"
rm -rf "$WORK/base/com/hlw/srv" "$WORK/base/com/hlw/gm"
cp -r "$WORK/ours/com/hlw/srv" "$WORK/base/com/hlw/"
cp -r "$WORK/ours/com/hlw/gm" "$WORK/base/com/hlw/"

echo "== 4. 注入 EntryActivity =="
python3 - "$WORK/base/com/ada/ane/sdk/EntryActivity.smali" <<'PY'
import sys, re
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
anchor = '    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n'
call = '\n    invoke-static {p0}, Lcom/hlw/gm/Gm;->start(Landroid/app/Activity;)V\n'
assert anchor in s
if 'Lcom/hlw/gm/Gm;' not in s:
    s = s.replace(anchor, anchor + call, 1)
    open(p, 'w', encoding='utf-8').write(s)
print('注入完成')
PY

echo "== 4b. 屏蔽广告与登录弹窗 =="
python3 - <<'PY2'
def stub(path, sig, body):
    s=open(path,encoding='utf-8').read(); L=s.split('\n'); out=[]; i=0
    while i<len(L):
        if L[i].startswith('.method') and sig in L[i]:
            out.append(L[i]); i+=1
            while i<len(L) and L[i].strip().startswith(('.registers','.locals')):
                out.append(L[i]); i+=1
            out.extend(body)
            while i<len(L) and not L[i].startswith('.end method'): i+=1
            continue
        out.append(L[i]); i+=1
    open(path,'w',encoding='utf-8').write('\n'.join(out))
b='$WORK/base/com/ada/ane/adapubfun/fun/'
try:
    stub(b+'adaAlarmReceiver.smali','onReceive(',['    return-void'])
    stub(b+'adaAlarmReceiver.smali','showNotification(',['    return-void'])
    stub(b+'adaAlertDialog.smali','call(',['    const/4 v0, 0x0','    return-object v0'])
    print('广告 SDK 已停用')
except Exception as e:
    print('跳过:', e)
PY2

echo "== 5. 汇编 + 打包 =="
java -jar /tmp/tools/smali.jar a "$WORK/base" -o "$WORK/classes.dex"
python3 - "$BASE_APK" "$WORK/classes.dex" "$OUT_APK" <<'PY'
import zipfile, sys, os
base, dex, out = sys.argv[1:4]
d = open(dex,'rb').read()
zg = zipfile.ZipFile(base); zo = zipfile.ZipFile(out, 'w')
for it in zg.infolist():
    nm = it.filename
    if nm.startswith('META-INF/') and nm.upper().endswith(('.SF','.RSA','.DSA','.MF')): continue
    data = d if nm == 'classes.dex' else zg.read(nm)
    z = zipfile.ZipInfo(nm, date_time=it.date_time)
    z.compress_type = it.compress_type; z.external_attr = it.external_attr
    zo.writestr(z, data)
z=zipfile.ZipInfo('assets/hlw/player_ud.json', date_time=(2020,1,1,0,0,0))
z.compress_type=zipfile.ZIP_DEFLATED
zo.writestr(z, open(os.path.join(os.path.dirname(__file__) if False else '/root/workspace/hlw_save.json','rb').read()) if False else open('javasrv/assets/player_ud.json','rb').read())
zo.close(); zg.close()
print('打包完成', out, os.path.getsize(out))
PY

echo "== 6. 对齐 + 签名 =="
zipalign -p -f 4 "$OUT_APK" "${OUT_APK%.apk}_aligned.apk"
if [ -n "$KS_BASE64" ]; then
  echo "$KS_BASE64" | base64 -d > "$WORK/ks.jks"
  apksigner sign --ks "$WORK/ks.jks" --ks-pass "pass:${KS_PASS}" --key-pass "pass:${KS_PASS}" \
    --ks-key-alias "${KS_ALIAS:-huluwa}" --out "${OUT_APK%.apk}_signed.apk" "${OUT_APK%.apk}_aligned.apk"
  echo "签名完成: ${OUT_APK%.apk}_signed.apk"
fi

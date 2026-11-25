#!/bin/bash
set -e
ROOT=/root/workspace/huluwa
GAME="$ROOT/游戏_当前安装版.apk"
W=$ROOT/build
mkdir -p "$W"; cd "$W"

echo "== 1. 编译 Java =="
rm -rf cls dex; mkdir -p cls dex
javac -source 8 -target 8 -bootclasspath /tmp/tools/android.jar -d cls \
      $(find "$ROOT/apk/javasrv/src" -name '*.java')
java -cp /tmp/tools/r8.jar com.android.tools.r8.D8 --min-api 21 --lib /tmp/tools/android.jar \
     --output dex $(find cls -name '*.class')

echo "== 2. 反编译原包 =="
rm -rf smali && python3 -c "
import zipfile;open('$W/orig.dex','wb').write(zipfile.ZipFile('$GAME').read('classes.dex'))"
baksmali d orig.dex -o smali

echo "== 3. 合入我们的类 =="
rm -rf ours && baksmali d dex/classes.dex -o ours
mkdir -p smali/com/hlw && cp -r ours/com/hlw/* smali/com/hlw/

echo "== 4. 注入 EntryActivity =="
python3 - <<'PY'
p='smali/com/ada/ane/sdk/EntryActivity.smali'
s=open(p,encoding='utf-8').read()
a='    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n'
c='\n    invoke-static {p0}, Lcom/hlw/gm/Gm;->start(Landroid/app/Activity;)V\n'
assert a in s
if 'Lcom/hlw/gm/Gm;' not in s: s=s.replace(a,a+c,1)
open(p,'w',encoding='utf-8').write(s); print('  OK')
PY

echo "== 5. 屏蔽广告弹窗 =="
python3 - <<'PY'
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
b='smali/com/ada/ane/adapubfun/fun/'
try:
    stub(b+'adaAlarmReceiver.smali','onReceive(',['    return-void'])
    stub(b+'adaAlarmReceiver.smali','showNotification(',['    return-void'])
    stub(b+'adaAlertDialog.smali','call(',['    const/4 v0, 0x0','    return-object v0'])
    print('  OK')
except Exception as e: print('  跳过', e)
PY

echo "== 6. 汇编 =="
smali a smali -o classes.dex

echo "== 7. 打包 =="
python3 - <<'PY'
import zipfile, os
GAME="/root/workspace/huluwa/游戏_当前安装版.apk"
OUT="/root/workspace/huluwa/build/unsigned.apk"
dex=open("/root/workspace/huluwa/build/classes.dex","rb").read()
zg=zipfile.ZipFile(GAME); zo=zipfile.ZipFile(OUT,"w")
for it in zg.infolist():
    nm=it.filename
    if nm.startswith("META-INF/") and nm.upper().endswith((".SF",".RSA",".DSA",".MF")): continue
    data=dex if nm=="classes.dex" else zg.read(nm)
    z=zipfile.ZipInfo(nm, date_time=it.date_time); z.compress_type=it.compress_type; z.external_attr=it.external_attr
    zo.writestr(z,data)
z=zipfile.ZipInfo("assets/hlw/player_ud.json", date_time=(2020,1,1,0,0,0)); z.compress_type=zipfile.ZIP_DEFLATED
zo.writestr(z, open("/root/workspace/huluwa/apk/javasrv/assets/player_ud.json","rb").read())
zo.close(); zg.close()
print("  完成", os.path.getsize(OUT))
PY
zipalign -p -f 4 unsigned.apk aligned.apk
apksigner sign --ks "$ROOT/huluwa.keystore" --ks-pass pass:huluwa123 --key-pass pass:huluwa123 \
  --ks-key-alias huluwa --out signed.apk aligned.apk
echo SIGNED_OK

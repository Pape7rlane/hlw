#!/bin/bash
# 纯 Java 一体包：从原始 APK 重新反编译 → 打补丁 → 打包签名（可重复执行）
set -e
ROOT=/root/workspace/huluwa
GAME="$ROOT/游戏_当前安装版.apk"
W=$ROOT/inject
cd "$W"

echo "== 1. 编译 Java 服务端 =="
rm -rf /tmp/srvcls /tmp/srvdex; mkdir -p /tmp/srvcls /tmp/srvdex
javac -source 8 -target 8 -bootclasspath /tmp/android-api30.jar -d /tmp/srvcls \
      $(find "$ROOT/apk/javasrv/src" -name '*.java')
java -cp /tmp/r8.jar com.android.tools.r8.D8 --min-api 21 --lib /tmp/android-api30.jar \
     --output /tmp/srvdex $(find /tmp/srvcls -name '*.class')

echo "== 2. 反编译原始 dex =="
rm -rf smali_work && python3 -c "
import zipfile;open('/tmp/orig.dex','wb').write(zipfile.ZipFile('$GAME').read('classes.dex'))"
baksmali d /tmp/orig.dex -o smali_work

echo "== 3. 合入我们的类 =="
rm -rf smali_srv && baksmali d /tmp/srvdex/classes.dex -o smali_srv
mkdir -p smali_work/com/hlw && cp -r smali_srv/com/hlw/* smali_work/com/hlw/

echo "== 4. 注入 EntryActivity =="
python3 - <<'PY'
p='smali_work/com/ada/ane/sdk/EntryActivity.smali'
s=open(p,encoding='utf-8').read()
a='    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n'
c='\n    invoke-static {p0}, Lcom/hlw/gm/Gm;->start(Landroid/app/Activity;)V\n'
assert a in s
if 'Lcom/hlw/gm/Gm;' not in s: s=s.replace(a,a+c,1)
open(p,'w',encoding='utf-8').write(s)
print('  注入点 OK')
PY

echo "== 5. 屏蔽广告/登录弹窗 =="
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
b='smali_work/com/ada/ane/adapubfun/fun/'
stub(b+'adaAlarmReceiver.smali','onReceive(',['    return-void'])
stub(b+'adaAlarmReceiver.smali','showNotification(',['    return-void'])
stub(b+'adaAlertDialog.smali','call(',['    const/4 v0, 0x0','    return-object v0'])
print('  广告 SDK 已停用')
PY

echo "== 6. 汇编 =="
smali a smali_work -o classes_java.dex

echo "== 7. 打包（含内置存档）=="
python3 - <<'PY'
import zipfile, os
GAME="/root/workspace/huluwa/游戏_当前安装版.apk"
OUT="/root/workspace/huluwa/inject/hlw_java_unsigned.apk"
dex=open("/root/workspace/huluwa/inject/classes_java.dex","rb").read()
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
print("  打包完成", os.path.getsize(OUT))
PY
zipalign -p -f 4 hlw_java_unsigned.apk hlw_java_aligned.apk
apksigner sign --ks /root/workspace/huluwa/huluwa.keystore --ks-pass pass:huluwa123 --key-pass pass:huluwa123 --ks-key-alias huluwa --out hlw_java_signed.apk hlw_java_aligned.apk
echo SIGNED_OK

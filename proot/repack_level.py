import zipfile, json, os, sys
SRC = "/root/workspace/huluwa/少年葫芦娃_单机版.apk"
DST = "/root/workspace/huluwa/hlxd_lvl_unsigned.apk"
TARGETS = sys.argv[1:] or ["gamelevel1-1"]
repl = {}
for lv in TARGETS:
    name = "assets/res/world/gamelevel/%s/database" % lv
    zin = zipfile.ZipFile(SRC)
    d = zin.read(name); zin.close()
    o = json.loads(d.decode("utf-8"))
    before = o.get("online")
    o["online"] = 0                      # ← 关键：关掉在线标记，走本地 AI
    nb = json.dumps(o, ensure_ascii=False, indent=4).encode("utf-8")
    repl[name] = nb
    print("[改] %s  online %s -> 0  (%d -> %d bytes)" % (lv, before, len(d), len(nb)))

zin = zipfile.ZipFile(SRC, 'r'); zout = zipfile.ZipFile(DST, 'w')
n = 0
for it in zin.infolist():
    name = it.filename
    if name.startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".MF")):
        continue
    data = repl.get(name)
    if data is None:
        data = zin.read(name)
    else:
        print("[替换]", name, len(data), flush=True)
    zi = zipfile.ZipInfo(name, date_time=it.date_time)
    zi.compress_type = it.compress_type
    zi.external_attr = it.external_attr
    zout.writestr(zi, data)
    n += 1
    if n % 500 == 0: print("...", n, flush=True)
zout.close(); zin.close()
print("完成:", DST, os.path.getsize(DST), "bytes,", n, "条目")

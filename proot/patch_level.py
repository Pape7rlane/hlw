# -*- coding: utf-8 -*-
"""改关卡 JSON：① 给怪物补 character（AI 行为表）让它们会打人
                 ② 注入两个友方队友实体（groupID=master 但 type=Monster，避开 HUD #1010）
用法: python3 patch_level.py <unsigned_out.apk> level1 level2 ...
"""
import zipfile, json, os, sys

SRC = "/root/workspace/huluwa/少年葫芦娃_单机版.apk"
DST = sys.argv[1]
LEVELS = sys.argv[2:] or ["gamelevel1-1"]

AI_PROFILE_GUAI = "charSgT1"        # 关卡脚本 proparr 里第一档怪实际使用的 AI 档案
MATES = [
    {"key": "mate1", "roleid": "1", "url": "res/world/role/fighterg14012", "name": "二娃"},
    {"key": "mate2", "roleid": "2", "url": "res/world/role/fighterg14013", "name": "三娃"},
]

def make_mate(m):
    return {
        # ★ 不能用 "master"：HUD 统计主角是按 groupID 的，会把队友算进去 → #1010。
        # 用第三个阵营 "funny"，再改 campObj 让它和 monster 互为敌人。
        "id": m["key"], "type": "Monster", "groupID": "funny",
        "url": m["url"], "character": "char1",     # char1 = 会追击+攻击的 AI
        "aiValid": 1, "input": "ArcadeInput",
        "isAttack": 1, "isBeAttacked": 1, "beAimed": 0,
        "attack": 4200, "defence": 3200, "life": 60000, "speed": 12,
        "angleMode": "3d", "useShadow": 3, "revealState": 1,
        "censusId": m["key"], "name": m["name"], "headPic": "",
        "heightArea": None, "shadowScaleFix": None,
        "noHitedLife": 0, "cartoon": 0, "enemyPlayAttacked": 1,
    }

def patch(o):
    # ① 怪物补 AI 行为表
    n_ai = 0
    for k, v in (o.get("monsters") or {}).items():
        if isinstance(v, dict) and v.get("type") == "Monster" and not v.get("character"):
            v["character"] = AI_PROFILE_GUAI
            n_ai += 1
    # ①b campObj：让 funny 与 monster 互为敌人（原本 funny 是中立）
    camp = o.setdefault("campObj", {})
    camp.setdefault("monster", {"enemy": {}, "neutrality": {}})
    camp.setdefault("funny", {"enemy": {}, "neutrality": {}})
    (camp["monster"]["enemy"] if "enemy" in camp["monster"] else camp["monster"].setdefault("enemy", {}))["funny"] = {"teamId": "funny"}
    camp["monster"].setdefault("neutrality", {}).pop("funny", None)
    camp["funny"].setdefault("enemy", {})["monster"] = {"teamId": "monster"}

    # ② 队友实体 + 生成点
    mp = (o.get("masterPostion") or [{"x": 0, "y": 0, "z": 0}])[0]
    o.setdefault("monsters", {})
    grp = []
    for i, m in enumerate(MATES):
        o["monsters"][m["key"]] = make_mate(m)
        grp.append({
            "doclass": [], "idname": m["key"],
            "attriclass": [], "attris": {}, "propsclass": [],
            "props": {"position": [mp.get("x", 0) + (70 if i == 0 else -70),
                                   mp.get("y", 5), mp.get("z", 0)],
                      "angle": 0},
            "gid": m["key"],
        })
    # ★ 真正的刷怪点在 guaisArr（一组 = 一批同时出现的怪）
    o.setdefault("guaisArr", []).append(grp)
    return n_ai

repl = {}
zin = zipfile.ZipFile(SRC)
for lv in LEVELS:
    name = "assets/res/world/gamelevel/%s/database" % lv
    try:
        raw = zin.read(name)
    except KeyError:
        print("[跳过] %s 不存在" % lv); continue
    o = json.loads(raw.decode("utf-8"))
    n = patch(o)
    repl[name] = json.dumps(o, ensure_ascii=False, indent=4).encode("utf-8")
    print("[改] %s: 补 AI 怪物 %d 只, 加队友 %d 个 (%d→%d bytes)"
          % (lv, n, len(MATES), len(raw), len(repl[name])))
zin.close()

def _zi(it):
    zi = zipfile.ZipInfo(it.filename, date_time=it.date_time)
    zi.compress_type = it.compress_type
    zi.external_attr = it.external_attr
    return zi

zin = zipfile.ZipFile(SRC, "r"); zout = zipfile.ZipFile(DST, "w")
cnt = 0
for it in zin.infolist():
    n = it.filename
    if n.startswith("META-INF/") and n.upper().endswith((".SF", ".RSA", ".DSA", ".MF")):
        continue
    zout.writestr(_zi(it), repl.get(n) or zin.read(n)); cnt += 1
zout.close(); zin.close()
print("完成:", DST, os.path.getsize(DST), "bytes,", cnt, "条目")

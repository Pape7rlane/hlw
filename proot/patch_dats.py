# -*- coding: utf-8 -*-
"""在 dats/local*.dat 里等长替换关卡 JSON：
   ① 怪物补 character（AI 档案），让 AIControl 接管
   ② online 1→0
   ③ 注入两个友方队友（funny 阵营）到 guaisArr
做法：区间内 JSON 压紧（去掉缩进），腾出的空间用来加字段，最后用空格补齐到原长度。
"""
import json, sys, re

LV = "gamelevel1-1"
AI = "charSgT1"
MATES = [("mate1", "1", "res/world/role/fighterg14012", "二娃"),
         ("mate2", "2", "res/world/role/fighterg14013", "三娃")]

def find_object(d, pos):
    """从 pos 往前找包含它的那个 JSON 对象的起止位置（大括号配对）。"""
    i = pos
    depth = 0
    while i > 0:
        c = d[i:i+1]
        if c == b'}':
            depth += 1
        elif c == b'{':
            if depth == 0:
                start = i
                break
            depth -= 1
        i -= 1
    else:
        raise RuntimeError("找不到起点")
    # 从 start 往后配对
    depth = 0
    j = start
    while j < len(d):
        c = d[j:j+1]
        if c == b'{':
            depth += 1
        elif c == b'}':
            depth -= 1
            if depth == 0:
                return start, j + 1
        j += 1
    raise RuntimeError("找不到终点")

def patch(o):
    n_ai = 0
    for k, v in (o.get("monsters") or {}).items():
        if isinstance(v, dict) and v.get("type") == "Monster" and not v.get("character"):
            v["character"] = AI
            n_ai += 1
    o["online"] = 0
    # 阵营：funny 与 monster 互为敌人
    camp = o.setdefault("campObj", {})
    camp.setdefault("funny", {}).setdefault("enemy", {})["monster"] = {"teamId": "monster"}
    camp.setdefault("monster", {}).setdefault("enemy", {})["funny"] = {"teamId": "funny"}
    camp["monster"].setdefault("neutrality", {}).pop("funny", None)
    # 队友
    mp = (o.get("masterPostion") or [{"x": 0, "y": 5, "z": 0}])[0]
    o.setdefault("monsters", {})
    grp = []
    for i, (key, rid, url, name) in enumerate(MATES):
        o["monsters"][key] = {
            "id": key, "type": "Monster", "groupID": "funny",
            "url": url, "character": "char1",
            "aiValid": 1, "input": "ArcadeInput",
            "isAttack": 1, "isBeAttacked": 1, "beAimed": 0,
            "attack": 4200, "defence": 3200, "life": 60000, "speed": 12,
            "angleMode": "3d", "useShadow": 3, "revealState": 1,
            "censusId": key, "name": name, "headPic": "",
            "heightArea": None, "shadowScaleFix": None,
            "noHitedLife": 0, "cartoon": 0, "enemyPlayAttacked": 1,
        }
        grp.append({"doclass": [], "idname": key, "attriclass": [], "attris": {},
                    "propsclass": [],
                    "props": {"position": [mp.get("x", 0) + (70 if i == 0 else -70),
                                           mp.get("y", 5), mp.get("z", 0)],
                              "angle": 0},
                    "gid": key})
    o.setdefault("guaisArr", []).append(grp)
    return n_ai

def main():
    path = "dats/assets/dats/local7.dat"
    d = bytearray(open(path, "rb").read())
    anchor = d.find(('"id":"%s"' % LV).encode())
    if anchor < 0:
        print("找不到", LV); return 1
    start, end = find_object(d, anchor)
    raw = bytes(d[start:end])
    o = json.loads(raw.decode("utf-8"))
    n = patch(o)
    compact = json.dumps(o, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    old_len = len(raw)
    if len(compact) > old_len:
        print("❌ 压紧后仍超长: %d > %d" % (len(compact), old_len)); return 1
    new = compact + b" " * (old_len - len(compact))
    d[start:end] = new
    assert len(d) == 8388608, "总长度变了！"
    open(path, "wb").write(d)
    print("✅ %s 补 AI 怪物 %d 只，注入队友 %d 个" % (LV, n, len(MATES)))
    print("   区间 %d..%d  原 %d 字节 → 压缩 %d + 补空格 %d"
          % (start, end, old_len, len(compact), old_len - len(compact)))
    return 0

sys.exit(main())

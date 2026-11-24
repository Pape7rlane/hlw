#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
少年葫芦娃 · 单机复活服务端
HTTP :8080  登录 / 服务器列表 / socket 配置
TCP  :9000  游戏包协议
"""
import json, zlib, struct, socket, threading, time, sys, os, traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 每章关数（模块级：进关卡算 next 时要用）
CHAPTER_LEVELS_G = [8, 6, 7, 6, 5, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7]

HTTP_PORT = int(os.environ.get("HLW_HTTP") or 8080)
TCP_PORT  = int(os.environ.get("HLW_TCP") or 9000)
HOST_STR  = "127.0.0.1"

# 数据目录：Android 上由 Java 层传进来（应用私有目录），PC 上就是脚本目录。
# 只读资源（props_seed.json）优先从数据目录找，找不到再去脚本目录找。
_DATA_DIR = os.environ.get("HLW_DATA") or os.path.dirname(os.path.abspath(__file__))
_SRC_DIR  = os.path.dirname(os.path.abspath(__file__))

def _datapath(name):
    p = os.path.join(_DATA_DIR, name)
    return p

def _respath(name):
    """只读资源：先查可写数据目录，再查打包目录（Android 里是 APK assets）。"""
    for d in (_DATA_DIR, _SRC_DIR):
        p = os.path.join(d, name)
        if os.path.exists(p):
            return p
    return os.path.join(_DATA_DIR, name)

LOGPATH   = _datapath("server.log")

_lock = threading.Lock()
def log(*a):
    s = "[%s] %s" % (time.strftime("%H:%M:%S"), " ".join(str(x) for x in a))
    print(s, flush=True)
    with _lock:
        with open(LOGPATH, "a", encoding="utf-8") as f:
            f.write(s + "\n")

# ───────────────────────── 服务器列表 ─────────────────────────
SERVERS = {
    "servernum": 1,
    "server0": "http://127.0.0.1:8080/",
    "server0back0": "http://127.0.0.1:8080/",
    "server0back1": "http://127.0.0.1:8080/",
    "servercode0": "1",
    "cutinto0": 0,
    "st0": 1,
    "getsession0": "http://127.0.0.1:8080/getsession.php",
    "serverid0": [1],
    "msgbox0": "",
    "servername0": "LocalServer",
    "socketURL0": "http://127.0.0.1:8080/socketcfg.xml",
}

SOCKET_CFG = '<cfg><domain>127.0.0.1</domain><port>9000</port></cfg>'

LOGIN_OK = {
    "uid":     ["10001"],
    "session": ["LOCALSESSION0001"],
    "login":   "ok",
    "newuser": ["0"],
    "olduid":  [False],
}

# ───────────────── 客户端请求体解密（FunLib.PhpStringify）─────────────────
# funLib.PhpStringify: base64v3(json, 自定义字母表) + "9SmI23gN" 做 MD5，
# 结果是 [32 位 MD5 hex][自定义 base64 载荷]
B64_ALPHA = "C*y0p]oPc[YrRi9htGuU4FaAnNg6BDEH7IOJKL5MQSTVWXZbdefjkl3mqs1vwxz28"

def decode_info(s):
    """把 cb.php 的 info= 参数解回 JSON 对象；失败返回 None"""
    if not s or len(s) < 34:
        return None
    idx = {c: i for i, c in enumerate(B64_ALPHA)}
    bits = n = 0
    out = bytearray()
    for ch in s[32:]:
        v = idx.get(ch)
        if v is None:
            return None
        bits = (bits << 6) | v
        n += 6
        if n >= 8:
            n -= 8
            out.append((bits >> n) & 0xFF)
    try:
        txt = bytes(out).decode("utf-8", "ignore").split("\x00")[0]
        i = txt.find("{")
        j = txt.rfind("}")
        if i < 0 or j < 0:
            return None
        return json.loads(txt[i:j + 1])
    except Exception:
        return None

# ───────────────── 玩家数据持久化 ─────────────────
UD_FILE = _datapath("player_ud.json")

PLAYER_LV = 60          # 背包/强化/仙市/帮会 等模块都按 playerLevel_fun 解锁，1 级全是锁
ROLE_EXP  = 1000000000  # 角色等级由 exp 过 pubinfo.manExp 表换算；给到极大值直接顶到上限

def _base_prop():
    return {"vipLevel": 0, "tl": 99, "maxTl": 99, "lv": PLAYER_LV, "exp": 0,
            "gold": 999999, "diamond": 99999, "power": 1000,
            "userMoney": 999999, "gameMoney": 99999999, "list": []}

def seed_ud():
    """首次运行时的初始 userDGroup（0~10 共 11 组，客户端 p 里就是这么要的）"""
    ud = {str(i): _base_prop() for i in range(11)}
    # analytics.firstTime = 账号首次进入时间，是「角色解封倒计时」的起点
    # （GenUnLock.getStartDate）。给个很早的时间 → 倒计时直接走完 →
    # getUnLockState() 返回 2 → canDoUnLock() 通过 → 有慧眼符就能立刻解封，
    # 不会卡在「加速1小时」那种等待界面。
    ud["0"].update({"analytics": {"tl": 0, "tltime": 0, "ljywk": 0,
                                  "firstTime": "2016-01-01 00:00:00"}})
    # ★ 七个娃娃全部直接给成"已解锁"（state > 0 → General.isLegal = true）。
    # 这样客户端根本不会进解封流程（那个流程要点解封、要等倒计时、实测还会闪退），
    # 打开角色界面直接就是可升级/可装备的状态。
    GEN_NAMES = ["大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"]
    roles = {}
    for i, nm in enumerate(GEN_NAMES):
        roles["role%d" % i] = enrich_role(
            {"id": i, "name": nm, "vip": 0, "showVIP": 0,
             "wakeUpAddTime": 0, "cell": {}, "bag": {}}, i)
    # 预设战队：mainMap.getTeamData() 读的就是 usrData["1"].teams，
    # 缺了它客户端只会在本地造一个"只有大娃"的默认队，阵位空着、引导要求你手动拖二娃。
    ud["1"] = {"roles": roles, "list": [], "sltRole": "role0",
               # ★ 战队里只能有 1 个人！
               # GameCopyFightUI.InitFinish → UserHeadConatin 里会按
               # upload.roleidArr 去配对场景对象 _roleArr[i]；队友对象原本由服务端
               # WCEnterLevel(1211) 生成，单机版没有 → _roleArr[1] undefined →
               # 访问 .roleid 抛 #1010 → 战斗 HUD 初始化失败、关卡卡死。
               "teams": {"defaultTeam": 0, "teamsNum": 1,
                         "team0": {"teamName": "战队1", "id": 0,
                                   "team": ["role0"]}}}

    # 背包：结构 { "<propId>": {"id":..., "num":...} }，PropsList.resetProp 只认 id/num。
    # props_seed.json 是从包内 assets/res/usrDataCache 里扫出来的全部道具 id，
    # 单机版直接每种给 99 个（装备/材料/炼妖壶原料都齐了）
    bag = {}
    try:
        seedfile = _respath("props_seed.json")
        with open(seedfile, encoding="utf-8") as f:
            bag = json.load(f).get("props") or {}
        log("[UD] 背包种子 %d 种道具" % len(bag))
    except Exception as e:
        log("[UD] props_seed.json 读取失败", e)
        bag = {"prop150103": {"id": "prop150103", "num": 9}}

    enrich_bag(bag)
    for i in range(7):
        fill_role_equips(roles["role%d" % i], i, bag)
    ud["2"] = {"props": bag, "list": []}
    # ★「莲台等级」= userDGroup["4"].ms.exp 过 getExpLv() 换算（不是 0.lv！）
    # pubinfo.manExp 是阈值表，给到超过表尾就取到最高等级，
    # 背包 / 强化 / 仙市 / 帮会 这些 playerLevel_fun 模块才开
    ud["4"] = {"nickName": "6JGr6Iqm5aiD", "level": 60,
               "ms": {"exp": 999999999, "level": 60}, "list": []}
    # 关卡进度：只保留序章通关标记（g0_i0.bastJudge=1）。
    # ⚠️ 不要批量填满整章整章的 bastJudge —— 实测会让主线地图的关卡节点
    #    全部不渲染（地图只剩路径曲线，节点消失）。
    #    解锁依赖的是"上一关 bastJudge >= 1"，真要全解锁得逐关校验渲染。
    # 关卡进度：第一章 21 关（地图右下角 0/21 就是章节总关数）+ 2-1（g1_i0）。
    # ⚠️ 数量必须**等于**实际关数：填多了（比如 30 关/章）会让地图节点渲染溢出、
    #    整张图只剩路径没有节点；填少了则后面的关卡显示为未解锁。
    # 关卡记录的真实结构（roleMagic 的开放条件写在 localPubinfo.moduleConfig 里）：
    #   story.g1_i0.bastJudge                 关卡是否通过
    #   story.g1_i0.roles.all.winTimes >= 1   通关次数  ← 法术真正判定的是这个
    #   story.g1_i0.roles.all.failTimes       失败次数
    # 每章关数（commonChapterCfg 实测）：第1章7关、第2章6关、第3章7关、第4章6关、
    # 第5章5关、第6~15章各7关。地图右下角 "0/21" 是 7关×3星=21 **星**，不是关数！
    # 之前按 21 关填 → 多出假关卡 → 地图节点渲染崩溃。
    CHAPTER_LEVELS = [8, 6, 7, 6, 5, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7]

    def clear_rec(win=3):
        return {"bastJudge": win, "roles": {"all": {"winTimes": win, "failTimes": 0}}}
    story = {}
    for g, n in enumerate(CHAPTER_LEVELS):
        for i in range(n):
            story["g%d_i%d" % (g, i)] = clear_rec(3)
    ud["6"] = {"story": story, "analytics": {"ljywk": 0}}
    # 首充：WindowView 用 "8.activity.firstPayGet1111" 判断是否显示首充入口/弹窗。
    # 单机版没有充值，直接标成已领，避免一进主城就弹「首充送仙品武器」挡住操作。
    ud["8"] = dict(_base_prop())
    ud["8"].update({"activity": {"firstPayGet1111": 1},
                    "recharge": {"total": 1}, "firstPay": 1})
    # now_g/now_i = **进度指针**，Sweep.checkLock 完全按它判定解锁范围：
    #   解锁条件：param1 < now_g 的整章；param1 == now_g 时 param2 <= now_i (+1 若当前关已过)
    # 所以它指向哪里，之后的关卡就都是锁的。
    # 推到第十五章最后一关(15-7) → 前面 14 章 + 第 15 章全部解锁。
    # ⚠️ 注意：设成 -1 会触发 checkLock 第一行直接 return true（全锁）。
    ud["7"] = {"war": {"r0": {}, "now_g": 14, "now_i": 6,
                       "now_drop": [], "ta": {}}}
    return ud

UD = {}
UD_LOCK = threading.Lock()

# 已知的功能模块名（客户端 checkModuleOpenOrLock / guideRemember.modules.<名>.open）
EQUIP_STRENGTHEN_LV = 60   # 装备强化等级（Prop.lv 取的就是 data.strengthen.lv）
EQUIP_REFINE_TIMES   = 5    # 精炼次数（Prop.refinedTimes）

def enrich_prop(prop, lid=None):
    """给道具补上实例数据（强化等级/精炼次数）。
    没有 data.strengthen.lv 的装备，Prop.lv 会落回配置里的最低值，
    战力自然上不去；补上后 GeneralStore.getGeneralObjTotalPower 才算得高。
    """
    if not isinstance(prop, dict):
        return prop
    data = prop.get("data")
    if not isinstance(data, dict):
        data = {}
    st = data.get("strengthen")
    if not isinstance(st, dict):
        st = {}
    st.setdefault("lv", EQUIP_STRENGTHEN_LV)
    st.setdefault("refineTimes", EQUIP_REFINE_TIMES)
    data["strengthen"] = st
    prop["data"] = data
    return prop

def enrich_bag(bag):
    n = 0
    for k, v in (bag or {}).items():
        enrich_prop(v)
        n += 1
    return n

GEN_GRADE      = 6      # 突破阶数：gradeCon.maxGrade 实测 = 6（超过会索引越界崩客户端）
GEN_GPT_LEN    = 500    # 突破点数
GEN_KARMA_LV   = 8      # 情义等级：yuanFen.maxLevelNum = 9，最高等级 = 8
GEN_GROW       = 9999   # 修炼/成长值
GEN_SKILL_LV   = 98     # 技能等级：arcadeskill.effects.*.effectValue 数组实测 99 项(下标0~98)，
                        # 给 100 会越界 → effectValue[100]=undefined → 技能伤害极低

def gen_skill_list(i):
    """按客户端自己的命名规则给出技能表（未知 id 客户端会忽略，多给无害）：
    role0(大娃) 实测上报的是 skill0 / skill100 / skill200，其余娃按 +i 偏移。"""
    out = [{"id": "skill%d" % i, "lv": GEN_SKILL_LV, "state": 1}]
    for n in range(1, 6):
        out.append({"id": "skill%d" % (100 * n + i), "lv": GEN_SKILL_LV, "state": 1})
    return out

# 装备槽位 → 道具 id（取自客户端自己"一键装备"时实际选中的 id）
SHARED_EQUIPS = {
    "precious": "prop160001",   # 法宝
    "necklace": "prop160101",   # 项链
    "ring":     "prop160201",   # 戒指
    "cloth":    "prop160301",   # 衣服
    "belt":     "prop160401",   # 腰带
    "foot":     "prop160501",   # 鞋
}

def weapon_ids(i):
    """第 i 个娃的左右手武器：客户端给它选的是 prop16{1+i}601 / 602"""
    return "prop16%d601" % (i + 1), "prop16%d602" % (i + 1)

def fill_role_equips(role, i, bag):
    """给角色把 8 个装备槽全穿上（幂等：只填空槽）。

    这样每次登录都是"已穿戴"状态，不用再手动点一键装备。
    cell[槽位] 必须是**数组**（GenEquip.resetCellList 取 cellObj[槽][0]）。
    """
    if not isinstance(role, dict):
        return role
    cell = role.setdefault("cell", {})
    rbag = role.setdefault("bag", {})
    slots = dict(SHARED_EQUIPS)
    ar, al = weapon_ids(i)
    slots["armR"] = ar
    slots["armL"] = al
    for slot, pid in slots.items():
        if cell.get(slot):
            continue
        cell[slot] = [pid]
        prop = bag.get(pid)
        if not prop:
            prop = {"id": pid, "num": 1}
            bag[pid] = prop
        enrich_prop(prop)
        rbag[pid] = prop
    return role

def enrich_role(role, i):
    """角色数据：只给**已验证可用**的字段。

    ⚠️ 反面教训：grade / deify / grow / yuanFenLevel / mastercall / skill 这些
    成长系统字段不能瞎给值 —— 客户端会用真实配置索引去查
    （GradeSystem.getGradeIndexCfg(grade)、YuanFenSystem.getYuanFenCfg 等），
    越界或形状不对会在 StreamWrapper.OnLoginPlayer 里直接崩掉进程
    （实测：给了 grade=8 后客户端在收到 GCLoginPlayer(120) 就断线，连 608 都发不出来）。
    要开这些系统，必须先从 roleCon / 突破表里读出真实上限，再逐个字段增量验证。

    可靠字段：
      state        已解锁（General.isLegal = state > 0）
      level        角色等级（General.level 直读）
      exp          经验
      totalPower   关卡面板"阵容战力"直接读这个值；给 0 会弹"战力不足"。
                   真实战力由 master 的 atk/def/life 决定，这里填个高值只为显示
      cell / bag   装备槽（数组形式）+ 角色装备背包
    """
    if not isinstance(role, dict):
        return role
    role["state"] = 1
    role["level"] = 90
    role["lv"] = 90
    role["exp"] = ROLE_EXP
    role["totalPower"] = 1200000     # 高于所有关卡推荐战力（15 章约 95 万）
    # 法术：roleCon 里解出的真实 id 是 skillN / skill10N / skill20N
    # （role5 实测 skill5, skill105, skill205），给满级
    role["skill"] = [{"id": "skill%d" % i, "lv": GEN_SKILL_LV, "state": 1},
                     {"id": "skill%d" % (100 + i), "lv": GEN_SKILL_LV, "state": 1},
                     {"id": "skill%d" % (200 + i), "lv": GEN_SKILL_LV, "state": 1}]
    # 突破：上限 6（gradeCon.maxGrade）
    role["grade"] = GEN_GRADE
    role["gptLen"] = 0
    # 情义：上限 8（yuanFen.maxLevelNum = 9，最高等级 = 8）
    role["yuanFenLevel"] = GEN_KARMA_LV
    role["yuanFen"] = 0
    # 觉醒 / 修炼 / 法宝：依赖各自配置表（deifyCost / wakeUpCost / mastercall），
    # 暂不写，避免再次越界崩溃
    for k in ("deify", "grow", "mastercall"):
        role.pop(k, None)
    role.setdefault("cell", {})
    role.setdefault("bag", {})
    return role

def set_guide_flag(gr, key, val):
    """引导标志双写：
    - 扁平 key（NewPlayerGuide.IsGuideComplete: _loc4_["modules.task.open"]）
    - 嵌套路径（LocalGBaseFun.getUserValue("guideRemember.modules.bag.open") 会逐层走）
    两种读法都存在，只写一种就会被另一条路径读不到 → 解锁动画反复播。
    """
    gr[key] = val
    node = gr
    parts = key.split(".")
    for k in parts[:-1]:
        nxt = node.get(k)
        if not isinstance(nxt, dict):
            nxt = {}
            node[k] = nxt
        node = nxt
    node[parts[-1]] = val

MODULE_NAMES = [
    "task", "bag", "bagCompose", "bagDecompose", "bagSynthesis",
    "equipment", "equipmentRefine", "equipmentBreak", "equipmentCompose",
    "market", "marketGang", "marketPot", "lotterydraw", "bangdan",
    "gang", "linghuSys", "demonpot", "roleBreak", "roleKarma", "roleGrow",
    "MysticalWorld", "MysticalWorldArena", "MysticalWorldList",
    "MysticalWorldMoney", "MysticalWorldMonster", "MysticalWorldTask",
    "MysticalWorldTower", "MysticalWorldWorldBoss",
    "danmaku", "war_sport", "PVP", "hard",
]

def ensure_guide_seed():
    """把"所有功能已开、所有解锁动画已播过"写进 0.guideRemember。

    莲台等级一给满，所有 playerLevel_fun 模块的开放条件同时成立，
    客户端就会把每个模块都当成"新解锁"，一遍遍播解锁动画。
    这些标记放在 0.guideRemember 里（**扁平键**），预置成 true 就不会再播。
    """
    added = 0
    for gi in ("0", "1"):
        gr = UD.setdefault(gi, {}).setdefault("guideRemember", {})
        for nm in MODULE_NAMES:
            for suffix in ("open", "unLock"):
                k = "modules.%s.%s" % (nm, suffix)
                if k not in gr:
                    set_guide_flag(gr, k, True)
                    added += 1
        if "guideEnd" not in gr:
            gr["guideEnd"] = 1
            added += 1
        for n in range(10):
            k = "openBtn%d.over" % n
            if k not in gr:
                set_guide_flag(gr, k, 1)
                added += 1
        # ★ 把所有新手引导分段都标成"已完成"。
        # 引导层会拦截输入：现在卡在"拖动二娃头像组队"这一步，
        # 候选区空着就永远拖不动，而 233/340 这些请求根本发不出去
        # （日志里出现过 g3/g4/g5/g23/g36/g41/g56/g58.over，就是这些分段）。
        for n in range(1, 81):
            for k in ("g%d.over" % n, "g%d_1.over" % n):
                if k not in gr:
                    set_guide_flag(gr, k, 1)
                    added += 1
    if added:
        log("[UD] 预置 guideRemember %d 条（抑制重复解锁动画）" % added)
        save_ud()
    return added

def load_ud():
    global UD
    try:
        with open(UD_FILE, encoding="utf-8") as f:
            UD = json.load(f)
        log("[UD] 已载入 player_ud.json (%d 组)" % len(UD))
    except Exception:
        UD = seed_ud()
        log("[UD] 首次运行，用种子数据")
    # 迁移：给已有背包/已穿戴装备补实例数据
    try:
        bag = ((UD.get("2") or {}).get("props") or {})
        enrich_bag(bag)
        gbag = (UD.get("2") or {}).setdefault("props", {})
        for rk, role in ((UD.get("1") or {}).get("roles") or {}).items():
            if isinstance(role, dict):
                idx = int(str(rk).replace("role", "") or 0)
                enrich_role(role, idx)
                enrich_bag(role.get("bag") or {})
                fill_role_equips(role, idx, gbag)
        for rk, role in ((UD.get("1") or {}).get("roles") or {}).items():
            if isinstance(role, dict):
                role["exp"] = max(int(role.get("exp") or 0), ROLE_EXP)
                # General.level 直读 genObj.level：缺了或太低会显示 1 级
                if int(role.get("level") or 0) < 90:
                    role["level"] = 90
                if int(role.get("lv") or 0) < 90:
                    role["lv"] = 90
        # 关卡进度收敛：只留序章通关，避免节点不渲染
        def _rec(win=3):
            return {"bastJudge": win, "roles": {"all": {"winTimes": win, "failTimes": 0}}}
        _lv = [8, 6, 7, 6, 5, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7]
        st = {}
        for g, n in enumerate(_lv):
            for i in range(n):
                st["g%d_i%d" % (g, i)] = _rec(3)
        UD.setdefault("6", {})["story"] = st
        g8 = UD.setdefault("8", {})
        g8.setdefault("activity", {})["firstPayGet1111"] = 1
        g8.setdefault("recharge", {})["total"] = 1
        g8["firstPay"] = 1
        w = UD.setdefault("7", {}).setdefault("war", {})
        # 进度指针推到最后一章最后一关，全关卡解锁
        w["now_g"] = 14
        w["now_i"] = 6
        t = UD.setdefault("1", {}).setdefault("teams", {})
        t.setdefault("defaultTeam", 0)
        t.setdefault("teamsNum", 1)
        # 只在缺队伍时给默认值，绝不覆盖玩家自定义的队伍
        t.setdefault("team0", {"teamName": "战队1", "id": 0, "team": ["role0"]})
        save_ud()
    except Exception as e:
        log("[UD] 迁移异常", e)
    ensure_guide_seed()
    return UD

def save_ud():
    with UD_LOCK:
        try:
            with open(UD_FILE, "w", encoding="utf-8") as f:
                json.dump(UD, f, ensure_ascii=False)
        except Exception as e:
            log("[UD] 保存失败", e)

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"
    def log_message(self, *a): pass

    def _send(self, body, ctype="application/json; charset=utf-8"):
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", ctype.split(";")[0])
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)
        try:
            self.wfile.flush()
        except Exception:
            pass
        log("[HTTP] 已发送", len(body), "字节")

    def do_GET(self):  self.route(b"")
    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0)
        self.route(self.rfile.read(n) if n else b"")

    def route(self, body):
        path = self.path.split("?")[0]
        log("[HTTP]", self.command, path, "|BODY|", (body or b"")[:2000].decode("utf-8","replace"))
        if not path.endswith((".pack",)):
            log("[HTTP] HDR", {k:v for k,v in self.headers.items() if k.lower() in ("content-type","content-length","referer","accept-encoding")})
        try:
            if "socketcfg" in path or path.endswith(".xml"):
                return self._send(SOCKET_CFG, "text/xml; charset=utf-8")
            if "getserver" in path.lower() or path.endswith(".htm"):
                body = json.dumps(SERVERS, separators=(",", ":"))
                log("[HTTP] 返回列表", body)
                return self._send(body)
            if "getsession" in path or "check_login" in path:
                # 客户端要求: zlib 压缩的 JSON，uid/session 必须是数组，login == "ok"
                return self._send(zlib.compress(json.dumps(LOGIN_OK).encode()),
                                  "application/octet-stream")
            if "elog" in path or "/l.php" in path:
                try:
                    import urllib.parse
                    q = urllib.parse.parse_qs((body or b"").decode("utf-8", "ignore"))
                    log("[CLIENT]", (q.get("msg") or [""])[0][:2000])
                except Exception:
                    log("[CLIENT]", (body or b"")[:400])
                return self._send("")
            if "update_reslist" in path:
                # ★ 以前这里伪造 res/probe3.bin 让客户端走异步更新，结果它把
                # buff.obj / bytesBuff.bytes 写成了用户层副本并覆盖包内资源，
                # 导致 UI 贴图渲染成黑块。现在一律"无需更新"。
                payload = {"msg": "ok", "ver": 1,
                           "servers": ["http://127.0.0.1:8080"],
                           "allRes": {}, "moduleList": [], "dirList": []}
                return self._send(zlib.compress(json.dumps(payload).encode()),
                                  "application/octet-stream")
            if path.endswith(".pack"):
                # 单机版没有真实分包资源；返回 404，客户端会保留包内原始资源
                log("[DL] 资源下载请求(忽略)", path)
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            if "cb.php" in path:
                import urllib.parse
                q = urllib.parse.parse_qs((body or b"").decode("utf-8", "ignore"))
                t = int((q.get("type") or ["0"])[0])
                info_raw = (q.get("info") or [""])[0]
                info = decode_info(info_raw)
                log("[CB] type=%d info=%s" % (t, json.dumps(info, ensure_ascii=False)[:1200]
                                              if info is not None else "<解不开>"))

                if t == 2:
                    # gearsystem / extatv 包内都有正版，绝不能 override（会写坏 Local Store）
                    # extatvBtnList 包内没有，缺它 EquipSystem.tryLoadDebugAsScript() 直接 #1009
                    payload = {"msg": "ok", "returndata": {
                        "deleteCfgs": [],
                        # ★ updateCfgs 是**合并**语义（updateByObject），可以只补字段：
                        #   包内 roleCon 没有 totalIdArr → mainMap 的
                        #   ALL_ROLES = gameCfg.roleInf.totalIdArr 变成 undefined
                        #   → getAllRoleKey 直接 return → 队伍候选区永远空白、拖不出人。
                        "updateCfgs": _merge_role_patch(),
                        "overrideCfgs": {"extatvBtnList": {
                            "ver": 1,
                            "debugAs": None,
                            # ★ 必须有 dtSystemArr！ExtAtvTotalDtSystem.init() 里
                            #   extatvDtSysArr = temCfg.dtSystemArr → 缺了就 null 访问抛 #1009
                            "dtSystemArr": [],
                        }}}}

                elif t == 0:
                    ud = dict(UD)
                    m = get_master()
                    ud["7"] = dict(ud.get("7") or {})
                    ud["7"]["war"] = dict((ud["7"].get("war") or {}))
                    ud["7"]["war"]["r0"] = m
                    # VIP 拉满（pubinfo.maxVipLv = 15）
                    ud.setdefault("0", {})["vipLevel"] = 15
                    m["vip"] = 15
                    m["showVIP"] = 1
                    # 客户端把"当前选中角色"记在 1.sltRole；战斗头像、CSInitPlayer
                    # 都按它来取。不改它的话，不管你队伍选谁，HUD 都显示大娃。
                    try:
                        sel_key = ((ud["1"].get("teams") or {}).get("team0") or {}).get("team", ["role0"])[0]
                    except Exception:
                        sel_key = "role0"
                    ud["1"]["sltRole"] = sel_key
                    UD["1"]["sltRole"] = sel_key      # 落库，下次登录客户端按它选当前角色
                    save_ud()
                    ud["7"]["war"].setdefault("now_drop", [])
                    ud["7"]["war"].setdefault("ta", {"id": m.get("id", 0)})
                    # 进度指针交给客户端/结算逻辑推进，这里不再强制覆盖，
                    # 否则清剧情后指针被顶到 14/6，"通关1-1却不解锁1-2" 这类问题会反复出现。
                    payload = {"msg": "ok", "returndata": ud}

                elif t == 233:
                    # 战队数据 / 战队保存（同一个接口）：
                    #   mainMap.uploadTeamData() → {type:"getTeamData", addTeam:[角色key], curTeamId, teamType}
                    # addTeam 就是玩家在界面上拖出来的自定义队伍，必须落库，否则每次登录又变回默认队。
                    cid = int((info or {}).get("curTeamId") or 0)
                    key = "team%d" % cid
                    add = [a for a in ((info or {}).get("addTeam") or []) if a]
                    teams = UD.setdefault("1", {}).setdefault("teams", {})
                    if add:
                        members = []
                        for a in add:
                            if a in (UD["1"].get("roles") or {}):
                                members.append(a)
                        if members:
                            # ★ 上阵人数锁 1：这个客户端的"面板格数 = 上阵人数 = 关卡配置 maxFightRole"，
                            #   两者共用同一个值，没法"面板 3 人、战斗 1 人"。
                            #   多人上阵时战斗 HUD 会 #1010 崩溃（队友实体无法生成）。
                            #   所以只保留玩家选的第一个，其余丢弃，保证面板里怎么拖都能安全出战。
                            picked = members[-1:]
                            if len(members) > 1:
                                log("[CB] 队伍只能上阵 1 人，保留最后拖入的 %s（丢弃 %s）"
                                    % (picked, members[:-1]))
                            teams[key] = {"teamName": teams.get(key, {}).get("teamName", "战队1"),
                                          "id": cid, "team": picked}
                            members = picked
                            # ★ 同步"当前角色"：客户端把 1.sltRole 规范化写进 cookie(curSltRole)，
                            #   头像/名字/武器/战斗模型全都按它取。
                            #   不跟着改的话，队伍换了七娃，左上角还是大娃。
                            UD.setdefault("1", {})["sltRole"] = picked[0]
                            teams["defaultTeam"] = cid
                            save_ud()
                            log("[CB] 保存自定义队伍 %s -> %s" % (key, members))
                    team = (teams.get(key) or {}).get("team") or ["role0"]
                    payload = {"msg": "ok", "act": None,
                               "returndata": {"teamData": {key: {"team": team, "curTeamId": cid}}}}

                elif t == 340:
                    # 角色通用操作：装备/卸下/技能升级/买灵力 都走这个
                    #   actType: up_batch / down_batch
                    #   equipArr: [{roleId, cellName, propId, pKey, onlyKey?}]
                    act = str((info or {}).get("actType") or "")
                    log("[CB] type=340 actType=%s" % act)
                    roles = UD.setdefault("1", {}).setdefault("roles", {})
                    changed = False
                    for it in ((info or {}).get("equipArr") or []):
                        rid = str(it.get("roleId"))
                        rk = rid if rid.startswith("role") else "role" + rid
                        cell = str(it.get("cellName") or "")
                        pkey = str(it.get("pKey") or it.get("propId") or "")
                        role = roles.setdefault(rk, {"id": int(rid.replace("role", "") or 0),
                                                     "name": "offline", "level": 30, "lv": 30,
                                                     "vip": 0, "showVIP": 0, "state": 1,
                                                     "exp": ROLE_EXP,
            "level": 90, "lv": 90,   # General.level 直接取 genObj.level，不给就是 1 级 "deify": 0,
                                                     "totalPower": 1751})
                        role.setdefault("cell", {})
                        role.setdefault("bag", {})
                        if not cell or not pkey:
                            continue
                        if act == "down_batch":
                            # cell[槽位] 是**数组**（resetCellList 里取 cellObj[cell][0]）
                            arr = role["cell"].get(cell)
                            if isinstance(arr, list) and pkey in arr:
                                arr.remove(pkey)
                            role["cell"][cell] = arr or []
                            changed = True
                        else:   # up_batch / 单件穿戴
                            prop = (UD.get("2", {}).get("props") or {}).get(pkey)
                            if not prop:
                                prop = {"id": str(it.get("propId") or pkey), "num": 1}
                                UD.setdefault("2", {}).setdefault("props", {})[pkey] = prop
                            role["bag"][pkey] = prop
                            # ★ 必须是数组：GenEquip.resetCellList 里
                            #   _loc2_ = cellObj[槽位][0]  ← 取第一个元素当 propKey
                            role["cell"][cell] = [pkey]
                            changed = True
                    upd = None
                    if changed:
                        save_ud()
                        upd = ["1|roles|" + json.dumps(roles, ensure_ascii=False)]
                        log("[CB] 穿戴结果: %s" % {k: v.get("cell") for k, v in roles.items()})
                    payload = {"msg": "ok", "act": None, "update": upd, "returndata": {}}

                elif t == 1001:
                    # 新手引导存档。客户端 SaveGuideOk 里判断 saveKey == curGuideKey 才 GuideRelease，
                    # 而 IsGuideComplete() 读的是 usrData 的 guideRemember —— 服务端不落这份状态，
                    # 引导就会无限重发同一个 key（实测 806 次/分钟），把 HTTP 队列堵死，
                    # 导致后面的解封请求(601)根本发不出去、10s 超时显示"网络较慢"。
                    # ★ 关键：NewPlayerGuide.IsGuideComplete() 读的是
                    #   usrData["0"]["guideRemember"]，而且键是**扁平**的
                    #   guideRemember["modules.task.open"] = 1
                    # （不是嵌套对象、也不在 "1" 组里）
                    upd = None
                    if isinstance(info, dict):
                        vk = str(info.get("vk") or "")
                        if vk.startswith("guideRemember."):
                            gk = vk[len("guideRemember."):]
                            # ★ 组号来自请求的 vi：LocalGBaseFun.SAVE_GUIDE_DATA_INDEX = 1，
                            # 功能解锁/红点写的是 "1".guideRemember；而 NewPlayerGuide
                            # 判断引导步骤读的是 "0".guideRemember。写错组号 → 解锁动画反复播。
                            gi = str(int(info.get("vi", 1)))
                            gr = UD.setdefault(gi, {}).setdefault("guideRemember", {})
                            vv = info.get("vv")
                            if gr.get(gk) != vv:
                                set_guide_flag(gr, gk, vv)
                                save_ud()
                                upd = ["%s|guideRemember|" % gi + json.dumps(gr, ensure_ascii=False)]
                            else:
                                # 同一个 key 重复保存（客户端引导死循环）：不再回 update，
                                # 只回一个极小的 ok 包，避免把请求队列拖死
                                log("[CB] 1001 重复 key=%s（已抑制）" % gk)
                    payload = {"msg": "ok", "act": None, "update": upd, "returndata": {}}

                elif t == 601:
                    # 角色解封（GenUnLockProxy.genUnLock）：roleidCon = "role2"
                    rid = str((info or {}).get("roleidCon") or "").replace("role", "")
                    log("[CB] 解封角色 role%s" % rid)
                    roles = UD.setdefault("1", {}).setdefault("roles", {})
                    rk = "role" + rid
                    cur = roles.get(rk) or {"id": int(rid or 0), "name": "offline",
                                            "level": 1, "lv": 1, "vip": 0, "showVIP": 0,
                                            "exp": 0, "deify": 0, "totalPower": 1751}
                    cur["state"] = 1
                    cur["id"] = int(rid or 0)
                    roles[rk] = cur
                    save_ud()
                    payload = {"msg": "ok", "act": None,
                               "update": ["1|roles|" + json.dumps(roles, ensure_ascii=False)],
                               "returndata": {}}

                elif t == 600:
                    # 解封加速（genAccelerate），直接返回成功
                    payload = {"msg": "ok", "act": None, "returndata": {}}

                elif t == 5422:
                    # VIP 特权/福利领取、宝箱、充值列表 都走这个 type，用 atype 区分。
                    #   viptequan : 领取每日 VIP 福利（配置 actchildvipfuli）
                    #      判定 2.activity.vipReward.vip6.lastling 是否"今天"
                    #      奖励 prop598(VIP6) / prop599(VIP12)
                    _at = (info or {}).get("atype") if isinstance(info, dict) else None
                    if _at in ("viptequan", "lingIndex"):
                        _now = time.strftime("%Y-%m-%d %H:%M:%S")
                        _act = UD.setdefault("2", {}).setdefault("activity", {})
                        _vr = _act.setdefault("vipReward", {})
                        _grant = {}
                        for _lv, _pid in ((6, "prop598"), (12, "prop599")):
                            _node = _vr.setdefault("vip%d" % _lv, {})
                            _node["lastling"] = _now
                            _grant[_pid] = {"id": _pid, "num": 1}
                        _bag = UD.setdefault("2", {}).setdefault("props", {})
                        for _pid, _it in _grant.items():
                            _cur = _bag.get(_pid) or {"id": _pid, "num": 0}
                            if not isinstance(_cur, dict):
                                _cur = {"id": _pid, "num": 0}
                            _cur["num"] = int(_cur.get("num") or 0) + _it["num"]
                            _bag[_pid] = _cur
                        save_ud()
                        log("[VIP] 领取福利 %s，lastling=%s" % (list(_grant.keys()), _now))
                        payload = {"msg": "ok", "act": None,
                                   "returndataNew": _grant,
                                   "update": ["2|activity|" + json.dumps(_act, ensure_ascii=False),
                                              "2|props|" + json.dumps(_bag, ensure_ascii=False)]}
                    elif _at == "box":
                        payload = {"msg": "ok", "act": None, "returndata": {}, "update": []}
                    elif _at == "payList":
                        payload = {"msg": "ok", "act": None, "returndata": {"list": []}, "update": []}
                    else:
                        log("[CB] 5422 atype=%s 未处理" % _at)
                        payload = {"msg": "ok", "act": None, "returndata": {}, "update": []}

                elif t == 44444:
                    # 进关卡。必须回 next（下一关的 gr_id/in_id），否则结算界面点「下一关」
                    # 会卡死：GameHandler.getNextInstance() 里 other.next 为空直接 return，
                    # 而 exit() 又要用它设好的 exitCallFun。
                    _ret = {}
                    _g = -1
                    try:
                        _g = int((info or {}).get("gr_id"))
                        _i = int((info or {}).get("in_id"))
                        _cnt = CHAPTER_LEVELS_G[_g] if 0 <= _g < len(CHAPTER_LEVELS_G) else 7
                        if _i + 1 < _cnt:
                            _ret["next"] = {"gr_id": "group%d" % _g, "in_id": "instance%d" % (_i + 1)}
                        elif _g + 1 < len(CHAPTER_LEVELS_G):
                            _ret["next"] = {"gr_id": "group%d" % (_g + 1), "in_id": "instance0"}
                        log("[44444] 进关卡 gr=%d in=%d → next=%s" % (_g, _i, _ret.get("next")))
                    except Exception as _e:
                        log("[44444] next 计算失败:", _e)
                    # te.ta = 特殊副本(锁妖塔等)的队伍数据：
                    #   t         开关（SytInput.isNowDate 用它判断是否特殊副本）
                    #   masterArr 可切换出战的角色列表（顶部那排葫芦娃位子）
                    # 不返回 te 会让 SytInput.mychangenumb() 抛 #1010 → 塔里不能动也不能打。
                    _team = ((UD.get("1") or {}).get("teams") or {}).get("team0", {}).get("team") or ["role0"]
                    # masterArr 里必须是数字 roleid（客户端会拼 "role"+id 去查 roleCon）
                    _arr = []
                    for _rk in _team:
                        try:
                            _arr.append(int(str(_rk).replace("role", "")))
                        except Exception:
                            _arr.append(0)
                    # 只有特殊副本（锁妖塔等，gr_id >= 2000）才下发 te；
                    # 普通章节给了它反而会让战斗头像栏 getRoleName(#1010) 崩掉。
                    # te.ta 只给特殊副本（锁妖塔等 gr_id >= 2000）。
                    # 普通章节下发它会让战斗头像栏 getRoleName 抛 #1010。
                    if _g >= 2000:
                        _now = time.strftime("%Y-%m-%d %H:%M:%S")
                        _ret["te"] = {"ta": {
                            "role": _arr, "master": _arr[0] if _arr else 0,
                            "masterArr": _arr, "t": _now, "ceng": -1,
                            "id": _g, "prop": {},
                        }}
                    payload = {"msg": "ok", "act": None, "returndata": _ret}

                elif t == 44445:
    # ★ 把客户端上报的通关结果落库：gr_id=章节, in_id=关卡编号, isWin=1
                    try:
                        _g, _i = info.get("gr_id"), info.get("in_id")
                        if info.get("isWin") == 1 and _g is not None and _i is not None:
                            _key = "g%s_i%s" % (int(_g), int(_i))
                            _st = UD.setdefault("6", {}).setdefault("story", {})
                            _rec = _st.get(_key) or {}
                            _rec["bastJudge"] = max(int(_rec.get("bastJudge") or 0), 1)
                            _rr = _rec.setdefault("roles", {}).setdefault("all", {})
                            _rr["winTimes"] = int(_rr.get("winTimes") or 0) + 1
                            _rr.setdefault("failTimes", 0)
                            _st[_key] = _rec
                            # 进度指针推进到刚打的这一关
                            _w = UD.setdefault("7", {}).setdefault("war", {})
                            _w["now_g"] = int(_g)
                            _w["now_i"] = int(_i)
                            save_ud()
                            log("[BATTLE] 记录通关 %s (bastJudge=%d)" % (_key, _rec["bastJudge"]))
                    except Exception as _e:
                        log("[BATTLE] 记录通关失败:", _e)
                                        # 战斗结算。客户端在本地算好掉落（info.prop = {propId: num}）发上来，
                    # 单机版直接采信并写入背包，然后把同样的奖励回传 + 用 update 落库。
                    props = {}
                    win = 1
                    score = 0
                    if isinstance(info, dict):
                        win = int(info.get("isWin") or 0)
                        score = int(info.get("score") or 0)
                        pr = info.get("prop") or {}
                        if isinstance(pr, dict):
                            for k, v in pr.items():
                                try:
                                    props[str(k)] = int(v)
                                except Exception:
                                    pass
                    bag = dict((UD.get("2") or {}).get("props") or {})
                    for pid, num in props.items():
                        cur = bag.get(pid) or {"id": pid, "num": 0}
                        if not isinstance(cur, dict):
                            cur = {"id": pid, "num": 0}
                        cur["id"] = pid
                        cur["num"] = int(cur.get("num") or 0) + num
                        bag[pid] = cur
                    UD.setdefault("2", {})["props"] = bag
                    save_ud()
                    log("[BATTLE] 结算 win=%d score=%d 掉落=%s | 背包现在 %d 种"
                        % (win, score, props, len(bag)))
                    # 注意：客户端 sendGameResultOk 里会执行
                    #   returndata.prop = TransformRstData.getInOutPArrFProxy(returndataNew)
                    # 也就是说**真正决定结算界面显示什么的是 returndataNew，不是 returndata.prop**。
                    # returndataNew.props = { propId: {num: N} }（可带 onlyKey 数组表示独立实例）
                    payload = {
                        "msg": "ok",
                        "act": None,
                        "update": ["2|props|" + json.dumps(bag, ensure_ascii=False)],
                        "returndataNew": {
                            "props": {k: {"num": v} for k, v in props.items()},
                            "gold": 1000,
                            "exp": 50,
                        },
                        "returndata": {
                            "prop": [{"id": k, "num": v} for k, v in props.items()],
                            "nowRate": 3 if win else 0,
                            "noRate": 0,
                            "score": score,
                        }}

                else:
                    log("[CB] type=%d 未实现，回空 ok 包" % t)
                    payload = {"msg": "ok", "returndata": {}}
                return self._send(zlib.compress(json.dumps(payload, ensure_ascii=False).encode()),
                                  "application/octet-stream")
            if "platinfo" in path:
                return self._send(json.dumps({"code": 0, "msg": "ok"}))
            # 统计 / 支付 / 公告 一律 200 空
            return self._send("")
        except Exception:
            log("ERR", traceback.format_exc())
            self._send("")

# ───────────────────────── TCP 协议 ─────────────────────────
# 包:  [byte 0x07][int32 len][byte msgVersion][byte 0][int16 id][int16 type][body]
# 长度 len 含自身 11 字节头
# 反编译依据: stream/message/MessageBase.as Encode()
#   writeByte(7) writeInt(length) writeByte(msgVersion) writeByte(0)
#   writeShort(id & 0xFFFF) writeShort(type)
#
# 握手真实顺序（StreamManager.as / StreamWrapper.as）:
#   TCP connect -> 客户端只启动 20s 心跳计时器，什么都不发
#   S->C GCCryptKey(116)  -> 客户端回 CGCryptKey(115)，设 cryptKey，派发 "ready"
#   C->S CGVersion(128)   <- 由 "ready" 触发
#   ...
# 所以必须先下发 116，否则客户端永远停在 STATUS_CONNECTED，只发心跳(type=1)

PLAYER_STATE_FILE = _datapath("player_state.json")
PLAYER_STATES_FILE = _datapath("player_states.json")
LAST_PLAYER = {}
PLAYER_STATES = {}      # {"0": {...大娃...}, "6": {...七娃...}}  按角色分别缓存

def load_player_state():
    try:
        with open(PLAYER_STATE_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}

def load_player_states():
    global PLAYER_STATES
    try:
        with open(PLAYER_STATES_FILE, encoding="utf-8") as f:
            PLAYER_STATES = json.load(f)
    except Exception:
        PLAYER_STATES = {}

def save_player_states():
    try:
        with open(PLAYER_STATES_FILE, "w", encoding="utf-8") as f:
            json.dump(PLAYER_STATES, f, ensure_ascii=False)
    except Exception:
        pass

def save_player_state(obj):
    try:
        with open(PLAYER_STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False)
    except Exception:
        pass

POLICY_PUSH = os.environ.get("POLICY_PUSH", "1") == "1"
POLICY_PUSH_DELAY = float(os.environ.get("POLICY_PUSH_DELAY", "0.5"))

POLICY = (b'<cross-domain-policy><allow-access-from domain="*" to-ports="*" '
          b'secure="false"/></cross-domain-policy>\x00')

def build(msg_type, body=b"", msg_id=0, msg_ver=0):
    total = 11 + len(body)
    return (b"\x07" + struct.pack(">i", total) + bytes([msg_ver & 0xFF, 0])
            + struct.pack(">h", msg_id & 0x7FFF) + struct.pack(">h", msg_type) + body)

def build_crypt_key():
    # GCCryptKey: int32 algorithm, uint32 elen, encryptKey, uint32 dlen, decryptKey
    # 全 0 密钥 => XOR 恒等 => 协议保持明文，但客户端 cryptKey 非空，握手继续
    payload = struct.pack(">i", 0)
    payload += struct.pack(">I", 4) + b"\x00\x00\x00\x00"
    payload += struct.pack(">I", 4) + b"\x00\x00\x00\x00"
    return build(116, payload)

def parse(buf):
    """从缓冲区切出一个完整包，返回 (type, msgid, body, consumed) 或 None"""
    if not buf:
        return None
    if buf[0] != 7:
        i = buf.find(b"\x07")
        if i <= 0:
            return ("BAD", 0, buf[:64], len(buf))
        return ("BAD", 0, buf[:i][:64], i)
    if len(buf) < 11:
        return None
    total = struct.unpack(">i", buf[1:5])[0]
    if total < 11 or total > (1 << 20):
        return ("BAD", 0, buf[:16], 1)       # 只丢 1 字节重新同步
    if len(buf) < total:
        return None
    msg_ver = buf[5]
    msg_id = struct.unpack(">h", buf[7:9])[0]
    mtype  = struct.unpack(">h", buf[9:11])[0]
    body   = buf[11:total]
    return (mtype, msg_id, body, total, msg_ver)

def read_cstr(b, off):
    """MessageBase.ReadString: 读到 \0 为止（不含）"""
    if off >= len(b):
        return None
    e = b.find(b"\x00", off)
    if e < 0:
        return b[off:].decode("utf-8", "ignore")
    return b[off:e].decode("utf-8", "ignore")

def parse_init_player(j):
    """CSInitPlayer 的 json → 存成 7.war.r0 用的战斗属性对象"""
    obj = json.loads(j)
    # GeneralHandler 会把 master.id 当 roleid，并把 id 换成本账号 uid
    if "id" not in obj and "roleID" in obj:
        obj["id"] = obj["roleID"]
    obj.setdefault("roleID", str(obj.get("id", 0)))
    obj.setdefault("grade", 0)
    obj.setdefault("deify", 0)
    obj.setdefault("groupID", "")
    LAST_PLAYER.clear()
    LAST_PLAYER.update(obj)
    save_player_state(obj)
    # 按角色分别存：每个娃的模型 url 和武器外观都不一样（刀/弓/剑/珠/杖/轮/…）
    try:
        rid = str(obj.get("roleID", obj.get("roleid", 0)))
        PLAYER_STATES[rid] = obj
        save_player_states()
    except Exception:
        pass
    log("[TCP] 已缓存战斗属性 name=%s atk=%s life=%s" %
        (obj.get("name"), obj.get("attack"), obj.get("life")))

def fallback_master():
    """没收到过 608 时的兜底（数值照客户端 1 级大娃的实测值）"""
    return {"id": 0, "roleID": "0", "name": "葫芦娃", "url": "fighterg14011",
            "headPic": "0", "vip": 0, "showVIP": 1, "level": 1,
            "attack": 46, "defence": 22, "maxSpeed": 10,
            "life": 480, "maxLife": 480, "realLife": 480, "realMaxLife": 480,
            "crit": 0.08, "critHit": 1.35, "comboType": 0, "frameRate": 1,
            "attackFactor": 1, "defenceFactor": 1, "critFactor": 1,
            "critHitFactor": 1, "maxLifeFactor": 1, "maxSpeedFactor": 1,
            "props": [], "groupID": "", "groupName": "帮会名字",
            "fightPower": 1751, "showPlayerNum": 1, "killPlayerNum": 0,
            "grade": 0, "deify": 0,
            "skill": [{"state": 1, "id": "skill0", "lv": 0},
                      {"state": 1, "id": "skill100", "lv": 0},
                      {"state": 0, "id": "skill200", "lv": 0}]}

def get_master():
    """战斗用的角色快照（userDGroup["7"].war.r0）。

    ★★ 关键：战斗里读的字段名跟 CSInitPlayer 的 C→S 格式**不一样**！
    以客户端自己的离线 master（ConstGame.FAKE_GAME_MASTER）为准：

        atk / def / life / speed / crit / critFactor / comboType
        atk_factor / def_factor / speed_factor / crit_factor / life_factor
        critFactor_factor / sDmgRdc_factor / sDmgRdc
        extrsEquip   ← 装备外观（不是 props！）
        skill        ← 技能列表 [{id,lv,state}]

    之前我们直接回显 CSInitPlayer 的 json（attack/defence/maxSpeed/props），
    客户端读 atk/def/life/extrsEquip 全是 undefined → 没武器、属性错乱、技能不生效。
    """
    # 先确定出战的是哪个娃，再用"这个娃自己的"快照（模型/武器/技能都不同）
    NAMES_PRE = ["大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"]
    team_pre = ((UD.get("1") or {}).get("teams") or {}).get("team0", {}).get("team") or ["role0"]
    sel_pre = team_pre[0] if team_pre else "role0"
    try:
        sel_pre_idx = int(str(sel_pre).replace("role", ""))
    except Exception:
        sel_pre_idx = 0
    # 以**客户端上报的 roleID** 为准（它就是 cookie 里的 curSltRole，玩家在主城点箭头切换后
    # 会上报新的 roleID）。队伍选择只在还没有上报时兜底。
    if LAST_PLAYER.get("roleID") is not None:
        try:
            sel_pre_idx = int(LAST_PLAYER["roleID"])
        except Exception:
            pass

    if not PLAYER_STATES:
        load_player_states()
    src = PLAYER_STATES.get(str(sel_pre_idx))
    if not src:
        src = LAST_PLAYER if LAST_PLAYER else (load_player_state() or fallback_master())

    g = lambda k, d=0: (src.get(k) if src.get(k) is not None else d)

    # ★ 出战的是队伍里选中的那个娃，不是固定的大娃。
    #   客户端战斗只读 7.war.r0 这一格，所以这里把身份字段换成所选角色，
    #   否则不管你拖谁，进战斗都是大娃。
    NAMES = ["大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"]
    team = ((UD.get("1") or {}).get("teams") or {}).get("team0", {}).get("team") or ["role0"]
    sel = team[0] if team else "role0"
    try:
        sel_idx = int(str(sel).replace("role", ""))
    except Exception:
        sel_idx = 0
    sel_role = ((UD.get("1") or {}).get("roles") or {}).get(sel) or {}

    role_id = sel_idx
    role_id_int = sel_idx

    m = {
        "name": sel_role.get("name") or (NAMES[sel_idx] if sel_idx < 7 else g("name", "葫芦娃")),
        "url": "fighterg1401%d" % (sel_idx + 1),
        # id 用 uid 形式；roleid 必须是数字（模型/技能表按它对）
        "id": g("id", "HL10001"),
        "roleid": role_id_int,
        "level": int(g("level", 90)),
        "vip": int(g("vip", 0)),
        "showVIP": int(g("showVIP", 1)),
        # headPic = 角色编号（roleCon 里 role5 的 headPic 就是 5），决定左上角头像和名字
        "headPic": str(sel_idx),
        "comboType": int(g("comboType", 0)),
        # ── 属性（改名！）──
        "atk": g("attack", 4746),
        "def": g("defence", 3818),
        "life": g("realLife", g("life", 68257)),
        "speed": g("maxSpeed", 10),
        "crit": g("crit", 0.08),
        "critFactor": g("critHit", 1.35),
        "sDmgRdc": 0,
        # ── 系数 ∝ _factor ──
        "atk_factor": g("attackFactor", 1),
        "def_factor": g("defenceFactor", 1),
        "speed_factor": g("maxSpeedFactor", 1),
        "crit_factor": g("critFactor", 1),
        "life_factor": g("maxLifeFactor", 1),
        "critFactor_factor": g("critHitFactor", 1),
        "sDmgRdc_factor": 1,
        # ── 装备外观（props → extrsEquip）──
        # 只有拿到该角色自己的快照时才带武器；否则清空，避免 "加载资源错误 fighterg1401X_weapon_dao"
        "extrsEquip": (src.get("props") or []) if str(src.get("roleID", src.get("roleid"))) == str(sel_idx) else [],
        # ── 技能：全部已解锁 ──
        # ★ 每个娃有自己的技能 id：skillN / skill10N / skill20N（N=角色编号）
        #   之前全发 skill0/100/200，所以不管用哪个娃，技能图标都是大娃的。
        "skill": [{"id": "skill%d"   % sel_idx, "lv": GEN_SKILL_LV, "state": 1},
                  {"id": "skill10%d" % sel_idx, "lv": GEN_SKILL_LV, "state": 1},
                  {"id": "skill20%d" % sel_idx, "lv": GEN_SKILL_LV, "state": 1}],
        "grade": int(g("grade", 6)),
        "deify": int(g("deify", 0)),
        "groupID": "",
        "groupName": g("groupName", "帮会名字"),
    }
    # 自定义属性覆盖（面板里填的值优先）
    _c = UD.get("custom") or {}
    if _c:
        if _c.get("atk"):
            m["atk"] = int(_c["atk"])
        if _c.get("def"):
            m["def"] = int(_c["def"])
        if _c.get("life"):
            m["life"] = int(_c["life"])
            m["realLife"] = int(_c["life"])
            m["maxLife"] = int(_c["life"])
        if _c.get("crit"):
            m["crit"] = float(_c["crit"])
        if _c.get("critFactor"):
            m["critFactor"] = float(_c["critFactor"])
    return m

def _split3(body):
    """CGLoginAccount: account\\0 password\\0 session\\0 loginType(1)"""
    parts, off = [], 0
    for _ in range(3):
        s = read_cstr(body, off)
        if s is None:
            break
        parts.append(s)
        off += len(s.encode("utf-8")) + 1
    if off < len(body):
        parts.append("loginType=%d" % body[off])
    return parts

def handle_packet(conn, mtype, msg_id, body, msg_ver):
    r = struct.pack(">i", 0)                      # result = 0 成功
    if mtype == 1:                                # CGHeartBeat 20s 一次
        log("[TCP] recv CGHeartBeat id=%d" % msg_id)
        return build(2, r, msg_id)                # 客户端未注册 type 2，收到即忽略
    if mtype == 115:                              # CGCryptKey（客户端回执）
        log("[TCP] recv CGCryptKey result=%d" % (struct.unpack(">i", body[:4])[0] if len(body) >= 4 else -1))
        return None
    if mtype == 128:                              # CGVersion -> GCVersion
        log("[TCP] recv CGVersion ver=%d info=%s" % (msg_ver, read_cstr(body, 0)))
        return build(129, r, msg_id)
    if mtype == 117:                              # CGLoginAccount -> GCLoginAccount
        acc = read_cstr(body, 0)
        log("[TCP] recv CGLoginAccount account=%r raw=%s" % (acc, body[:96].hex()))
        log("[TCP]   account/password/session/loginType = %s" % _split3(body))
        return build(118, r, msg_id)
    if mtype == 119:                              # CGLoginPlayer -> GCLoginPlayer
        log("[TCP] recv CGLoginPlayer raw=%s" % body[:80].hex())
        return build(120, r, msg_id)
    # ── 关卡流程（604/606/612）───────────────────────────────
    if mtype == 604:                              # CSLockLevel -> SCLockLevel(605)
        # 编码: levelID\0 paramJson\0   （CSLockLevel.Encode 顺序）
        level_id = read_cstr(body, 0) or ""
        p_off = len(level_id.encode("utf-8")) + 1
        param = read_cstr(body, p_off) or ""
        log("[TCP] recv CSLockLevel levelID=%r param=%s" % (level_id, param))
        # SCLockLevel.Decode 顺序很特殊: levelID(必须是同一串,否则客户端直接 return)
        #                              → result int → copyID int → onlineNum int → param str
        #                              levelID 为空/不匹配 = 客户端认为包格式错误，直接丢弃
        resp_body = (level_id.encode("utf-8") + b"\x00"
                     + struct.pack(">i", 0)          # result 0 = 锁定成功
                     + struct.pack(">i", int(time.time()) & 0x7FFF)  # copyID
                     + struct.pack(">i", 1)          # onlineNum
                     + b"\x00")                     # param 空串
        return build(605, resp_body, msg_id)

    if mtype == 606:                              # CSEnterLevel -> SCEnterLevel(607)
        # 编码: float x,y,z, float bloodPercent, int killPlayerNum, attriFactor\0
        try:
            x, y, z, bp = struct.unpack(">ffff", body[0:16])
            kill = struct.unpack(">i", body[16:20])[0]
            attri = read_cstr(body, 20) or ""
        except Exception:
            x = y = z = bp = 0.0; kill = 0; attri = ""
        log("[TCP] recv CSEnterLevel pos=(%.2f,%.2f,%.2f) blood=%.2f kill=%d attri=%s"
            % (x, y, z, bp, kill, attri[:80]))
        # 607 = 进关卡成功；紧跟 1265 把客户端设为"驱动方"，让它本地跑关卡逻辑。
        out = build(607, r, msg_id) + build(1265, r, msg_id)
        # ★ AI 队友：正式做法是逐包下发 WCEnterLevel(1211)，
        #   客户端 Online.OnWCEnterLevel 会 new 一个 Master 实体加进场景。
        #   （之前用 combineObject.monsters.extendFightN 注入不行——那会被当怪物，
        #     且战斗 HUD 的 _roleArr 数量对不上会抛 #1010）
        # ── 方案A 第一步：生成一只"服务端控制的怪"，验证 WCEnterLevel 能不能在关卡里生效 ──
        mon = {
            "id": "testMon0",
            "url": "monster_b21007",
            "speed": 8,
            "roleID": "0",
            "name": "测试蝎子",
            "vip": 0, "showVIP": 0,
            "level": 1,
            "headPic": "0",
            "groupID": "monster",          # 敌方阵营
            "noAttackGroupID": [],
            "comboType": 0,
            "attackAreaFactor": 1,
            "x": x + 120, "y": y, "z": z,
            "killNum": 0, "killNumTotal": 0,
            "groupName": "",
            "skill": [],
            "crit": 0.05, "critHit": 1.2,
            "critFactor": 1, "critHitFactor": 1,
            "attack": 500, "defence": 100,
            "life": 5000, "maxLife": 5000,
            "attackFactor": 1, "defenceFactor": 1,
            "maxLifeFactor": 1, "maxSpeedFactor": 1,
            "grade": 0, "deify": 0,
            "roleExtend": "{}",
        }
        out += build(1211, json.dumps(mon, ensure_ascii=False).encode("utf-8") + b"\x00", msg_id)
        log("[TCP] 尝试生成测试怪 testMon0 于 (%.0f,%.0f,%.0f)" % (x + 120, y, z))

        # ── 队友：走 combineObject.monsters 注入（客户端自己的 Master 模板），
        #    WCEnterLevel(1211) 在这类关卡里不生成实体，已实测无效。
        team = ((UD.get("1") or {}).get("teams") or {}).get("team0", {}).get("team") or []
        mates = [rk for rk in team[1:3]]
        if not mates:
            mates = ["role1", "role2"]
        for n, rk in enumerate(mates):
            idx = int(str(rk).replace("role", "") or 0)
            role = ((UD.get("1") or {}).get("roles") or {}).get(rk) or {}
            name_list = ["大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"]
            obj = {
                "id": "mate%d" % n,
                # 在线玩家实体走资源加载，需要完整路径（本地 master 才用裸名）
                "url": "res/world/role/fighterg1401%d" % (idx + 1),
                "speed": 10,
                "roleID": str(idx),
                "name": name_list[idx] if idx < 7 else "队友",
                "vip": 0, "showVIP": 0,
                "level": int(role.get("level") or 90),
                "headPic": "0",
                "groupID": "master",
                "noAttackGroupID": ["master"],
                "comboType": 0,
                "attackAreaFactor": 1,
                "x": 30 + n * 30, "y": 0, "z": 30 + n * 30,
                "killNum": 0, "killNumTotal": 0,
                "groupName": "",
                "skill": role.get("skill") or [],
                "crit": 0.08, "critHit": 1.35,
                "critFactor": 1, "critHitFactor": 1,
                "attack": 4746, "defence": 3818,
                "life": 67717, "maxLife": 67717,
                "attackFactor": 1, "defenceFactor": 1,
                "maxLifeFactor": 1, "maxSpeedFactor": 1,
                "grade": 6, "deify": 0,
                "roleExtend": "{}",
            }
            body = json.dumps(obj, ensure_ascii=False).encode("utf-8") + b"\x00"
            _ = body  # 不再用 1211（无效）
        return out

    if mtype == 612:                              # CSLeaveLevel -> SCLeaveLevel(613)
        log("[TCP] recv CSLeaveLevel")
        return build(613, r, msg_id)

    if mtype == 608:                              # CSInitPlayer -> SCInitPlayer
        j = read_cstr(body, 0) or ""
        log("[TCP] recv CSInitPlayer json=%s" % j)
        # 把客户端自算的战斗属性存下来，下次 cb.php?type=0 以 7.war.r0 回给它的
        # checkRequestGameReal()（缺这个就是"进入战斗时缺少角色战斗属性"）
        try:
            parse_init_player(j)
        except Exception:
            log("[TCP] CSInitPlayer 解析失败（不影响回复）")
        # SCInitPlayer: int32 result + string levelID(\0结尾，空串合法)
        return build(609, r + b"\x00", msg_id)
    log("[TCP] recv type=%d id=%d len=%d body=%s -> 兜底回 %d"
        % (mtype, msg_id, len(body), body[:64].hex(), mtype + 1))
    return build(mtype + 1, r, msg_id)

def client_thread(conn, addr):
    log("[TCP] 客户端接入", addr)
    try:
        if POLICY_PUSH:
            conn.sendall(POLICY)
            log("[TCP] 主动推送策略文件（客户端会当垃圾清掉，先发以确保 Flash 放行）")
            time.sleep(POLICY_PUSH_DELAY)
        # ★ 握手第一步：必须先下发 GCCryptKey，客户端的 "ready" 只由它触发
        pkt = build_crypt_key()
        conn.sendall(pkt)
        log("[TCP] 下发 GCCryptKey(116) 零密钥 -> 等客户端回 ready 后发 CGVersion")
    except Exception as e:
        log("[TCP] 初始化失败", e)
    buf = b""
    try:
        conn.settimeout(600)
        while True:
            data = conn.recv(8192)
            if not data:
                log("[TCP] 连接关闭")
                break
            log("[TCP] RAW %d bytes: %s" % (len(data), data[:256].hex()))
            if b"<policy-file-request" in data:
                log("[TCP] 收到 policy-file-request，回复放行策略")
                try:
                    conn.sendall(POLICY)
                except Exception:
                    pass
                k = data.find(b"<policy-file-request")
                j = data.find(b"\x00", k)
                if j < 0:
                    continue
                data = data[:k] + data[j + 1:]
                if not data:
                    continue
            buf += data
            while True:
                pk = parse(buf)
                if pk is None:
                    break
                if pk[0] == "BAD":
                    junk, used = pk[2], pk[3]
                    log("[TCP] 垃圾字节 %d 个，丢弃重同步: %s" % (used, junk.hex()))
                    buf = buf[used:]
                    continue
                mtype, msg_id, body, used, msg_ver = pk
                buf = buf[used:]
                resp = handle_packet(conn, mtype, msg_id, body, msg_ver)
                if resp:
                    conn.sendall(resp)
                    log("[TCP] send type=%d id=%d len=%d"
                        % (struct.unpack(">h", resp[9:11])[0],
                           struct.unpack(">h", resp[7:9])[0], len(resp)))
    except socket.timeout:
        log("[TCP] 超时断开")
    except Exception:
        log("[TCP] ERR", traceback.format_exc())
    finally:
        try: conn.close()
        except Exception: pass

def tcp_server():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("0.0.0.0", TCP_PORT))
    s.listen(16)
    log("[TCP] 监听 0.0.0.0:%d" % TCP_PORT)
    while True:
        conn, addr = s.accept()
        threading.Thread(target=client_thread, args=(conn, addr), daemon=True).start()

def serve_http(port):
    srv = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    log("[HTTP] 监听 0.0.0.0:%d" % port)
    srv.serve_forever()

ROLE_NAMES = ["大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"]

ROLE_CON_NAMES = ["大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"]

def _merge_role_patch():
    """合并「候选角色列表」和「当前角色身份覆盖」两个补丁。"""
    base = {"roleCon": {"totalIdArr": [0, 1, 2, 3, 4, 5, 6]}}
    try:
        extra = role_con_patch()
        base["roleCon"].update(extra.get("roleCon") or {})
    except Exception as e:
        log("[CFG] role patch 失败: %s" % e)
    return base

def role_con_patch():
    """把 roleCon.role0 的身份换成"当前出战角色"。

    客户端 cookie 的 curSltRole 永远是 role0（游戏自身没有切换入口），
    所以主城站着的、左上角名字都取自 roleCon.role0。
    登录时用 updateCfgs 把 role0 的 url/name 换成所选角色 →
    主城、头像、名字、战斗模型就全部统一了。
    """
    try:
        cur = UD.get("1", {}).get("sltRole") or "role0"
        idx = int(str(cur).replace("role", ""))
    except Exception:
        idx = 0
    if not (0 <= idx <= 6):
        idx = 0
    patch = {"roleCon": {"role0": {
        "name": ROLE_CON_NAMES[idx],
        "url": "fighterg1401%d" % (idx + 1),
        "headPic": str(idx),
        "skill":  "skill%d" % idx,
        "skill1": "skill10%d" % idx,
        "skill2": "skill20%d" % idx,
    }}}
    return patch

def reset_story():
    """把全部关卡的通关记录清零 → 每关都当"没打过"，重新播放剧情。

    关卡解锁用的是 7.war.now_g/now_i 这个进度指针，跟 story 里的 bastJudge 无关，
    所以清零后 15 章依然全部可以进。
    """
    story = (UD.get("6") or {}).get("story") or {}
    n = 0
    for k, v in story.items():
        if isinstance(v, dict):
            v["bastJudge"] = 0
            r = v.get("roles")
            if isinstance(r, dict):
                for sub in r.values():
                    if isinstance(sub, dict):
                        sub["winTimes"] = 0
            n += 1
    w = UD.setdefault("7", {}).setdefault("war", {})
    w["now_g"] = 0          # 进度指针回到 1-1，之后通关会自然推进
    w["now_i"] = 0
    save_ud()
    log("[UI] 剧情重放：已清零 %d 个关卡，进度指针回到 0/0" % n)
    return n

def restore_story():
    """恢复全通关（三星）。"""
    story = (UD.get("6") or {}).get("story") or {}
    n = 0
    for k, v in story.items():
        if isinstance(v, dict):
            v["bastJudge"] = 3
            r = v.get("roles")
            if isinstance(r, dict):
                for sub in r.values():
                    if isinstance(sub, dict):
                        sub["winTimes"] = 3
            n += 1
    w = UD.setdefault("7", {}).setdefault("war", {})
    w["now_g"] = 14
    w["now_i"] = 6
    save_ud()
    log("[UI] 已恢复全通关 %d 关，指针推到 14/6" % n)
    return n


def set_stats(atk=0, dfn=0, life=0, crit=-1.0, critf=-1.0):
    """自定义全部角色的战斗属性（攻击/防御/生命/暴击率/暴击伤害）。

    存在 player_ud.json 的 custom 节点里，get_master() 会优先生效。
    传 0 表示"不覆盖该项"。
    """
    try:
        c = UD.setdefault("custom", {})
        for k, v in (("atk", atk), ("def", dfn), ("life", life), ("crit", crit), ("critFactor", critf)):
            try:
                f = float(v)
            except Exception:
                f = 0.0
            if f > 0:
                c[k] = f
        save_ud()
        log("[UI] 自定义属性 -> %s" % c)
        return "ok"
    except Exception as e:
        return "err: %s" % e

def clear_stats():
    UD.pop("custom", None)
    save_ud()
    log("[UI] 已清除自定义属性")
    return "ok"

def set_role(idx):
    """切换当前出战角色：写 1.sltRole + 战队成员，下次登录生效。

    客户端把 1.sltRole 规范化写进本地 cookie(curSltRole)，
    头像/名字/武器/战斗模型/技能图标全都按它取。
    """
    try:
        idx = int(idx)
    except Exception:
        return "bad index"
    if not (0 <= idx <= 6):
        return "bad index"
    key = "role%d" % idx
    UD.setdefault("1", {})
    UD["1"]["sltRole"] = key
    teams = UD["1"].setdefault("teams", {})
    t0 = teams.setdefault("team0", {"teamName": "战队1", "id": 0, "team": [key]})
    t0["team"] = [key]
    save_ud()
    log("[UI] 切换角色 -> %s" % key)
    return ROLE_NAMES[idx]

def get_role():
    try:
        return UD.get("1", {}).get("sltRole", "role0")
    except Exception:
        return "role0"

def start_all(data_dir=""):
    """Android(Chaquopy) 入口：把可写数据目录指向应用私有目录后再启动。"""
    global _DATA_DIR, LOGPATH, UD_FILE, PLAYER_STATE_FILE, PLAYER_STATES_FILE
    if data_dir:
        _DATA_DIR = data_dir
        try:
            os.makedirs(_DATA_DIR, exist_ok=True)
        except Exception:
            pass
        LOGPATH = _datapath("server.log")
        UD_FILE = _datapath("player_ud.json")
        PLAYER_STATE_FILE = _datapath("player_state.json")
        PLAYER_STATES_FILE = _datapath("player_states.json")
        # 首次运行时把打包内的存档/种子复制到可写目录
        import shutil
        for name in ("player_ud.json", "player_states.json", "props_seed.json"):
            dst = os.path.join(_DATA_DIR, name)
            src = os.path.join(_SRC_DIR, name)
            if not os.path.exists(dst) and os.path.exists(src):
                try:
                    shutil.copyfile(src, dst)
                except Exception as e:
                    log("[INIT] 复制 %s 失败: %s" % (name, e))
    threading.Thread(target=main, daemon=True).start()
    return "started"


def main():
    load_ud()
    load_player_states()
    threading.Thread(target=tcp_server, daemon=True).start()
    for p in (HTTP_PORT, 808):
        threading.Thread(target=serve_http, args=(p,), daemon=True).start()
    while True:
        time.sleep(3600)

if __name__ == "__main__":
    main()

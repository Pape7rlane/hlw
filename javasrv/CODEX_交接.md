# 少年葫芦娃 · 一体包工程交接（给 Codex / 下一个 AI 或人）

## 0. 一句话现状

游戏 + Java 服务端 + GM 面板已经打成一个 285 MB 的 APK，**能进主城、能进关卡、能改角色/属性/剧情**；
**唯一未解决：进关卡后角色动不了（疑似客户端在等某个 TCP 包）**。

---

## 1. 工程位置

```
/root/workspace/huluwa/                  # 工作区（Termux/proot Ubuntu）
├── 游戏_当前安装版.apk                   # 285 MB 原始游戏包（构建基础，可再用）
├── huluwa.keystore                      # 签名（pass: huluwa123, alias: huluwa）
├── build_onepkg.sh                      # 一键构建脚本（核心）
├── 交接文档.md                          # 更早的踩坑全记录（673→755 行）
├── CODEX_交接.md                        # 本文件
├── mock/                                # Python 版服务端（参考实现，逻辑最全）
│   ├── server.py                        # 2000+ 行，所有协议细节都在这里
│   └── player_ud.json                   # 满级存档
├── src/                                  # 反编译的 AS3 源码（查客户端行为用）
└── apk/                                 # git 仓库（见第 7 节）
    └── javasrv/src/com/hlw/
        ├── srv/{Json,Store,Http,Tcp,Srv,Log_}.java   # Java 服务端
        └── gm/Gm.java                                # GM 面板
```

构建产物：`/root/workspace/huluwa/build/signed.apk` → 复制到 `/sdcard/Download/DSH-Go/`

---

## 2. 构建（一条命令）

```bash
bash /root/workspace/huluwa/build_onepkg.sh      # 约 8 分钟
cp /root/workspace/huluwa/build/signed.apk /sdcard/Download/DSH-Go/少年葫芦娃_一体包_vN.apk
```

需要的外部工具（脚本里写死了路径，重建前确认存在）：

| 路径 | 用途 | 获取方式 |
|---|---|---|
| `/usr/bin/baksmali`、`/usr/bin/smali` | dex ↔ smali | `apt install -y smali` |
| `/tmp/tools/android.jar` | 编译 Android Java | 下载 `https://github.com/Sable/android-platforms/raw/master/android-30/android.jar` |
| `/tmp/tools/r8.jar` | class → dex（D8） | `https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.3.37/r8-8.3.37.jar` |
| `zipalign`、`apksigner`、`javac` | 打包签名 | apt（build-tools / JDK 17+） |

脚本做的 7 步：

1. `javac` 编译 `javasrv/src/**` → `D8` 转 dex
2. 从游戏包取 `classes.dex` → `baksmali` 出 smali 树
3. 把我们编译出的 `com/hlw/**` smali 合入
4. **注入** `smali/com/ada/ane/sdk/EntryActivity.smali` 的 `onCreate`：
   ```smali
   invoke-static {p0}, Lcom/hlw/gm/Gm;->start(Landroid/app/Activity;)V
   ```
5. 屏蔽广告 SDK：把 `adaAlarmReceiver.onReceive` / `showNotification`、`adaAlertDialog.call` 改成直接 return
6. `smali a` 汇编回 dex
7. 用原始 APK 重新打包（替换 classes.dex，加 `assets/hlw/player_ud.json` 满级存档）→ `zipalign` → `apksigner`

---

## 3. Java 服务端结构（`javasrv/src/com/hlw/srv/`）

| 文件 | 职责 |
|---|---|
| `Json.java` | 极简 JSON 解析/序列化（不要换 org.json，桌面端要能跑测试） |
| `Store.java` | 存档：`player_ud.json` 读写、`7.war.r0` 战斗快照、通关记录、GM 改属性 |
| `Http.java` | **HTTP 8080**：getserver / getsession / socketcfg / update_reslist / cb.php 各 type |
| `Tcp.java` | **TCP 9000**：握手 GCCryptKey(116)、心跳、604/606/612/608 等 |
| `Srv.java` | 入口 `start(Context)` + GM 调用的 `setRole/setStats/resetStory/restoreStory/status` |
| `gm/Gm.java` | GM 悬浮窗（`TYPE_APPLICATION_OVERLAY`）+ 紧凑面板 |

### 关键实现点（别改坏）

- **启动必须在主线程**：`Srv.start()` 在 `runOnUiThread` 里调用（历史坑：Python 版在后台线程初始化直接 SIGSEGV）
- **战斗数据必须用客户端上报的**：收到 `CSInitPlayer(608)` 就 `Store.setLastPlayer(json)`，
  `cb.php type=0` 用它拼 `7.war.r0`；**硬编码字段会缺 props/critHit/realLife → 战斗卡死**
- **HTTP 报文解析**：读到 `\r\n\r\n` 为止（曾经写成按行计数，导致请求永远无应答 → "获取服务器列表失败"）
- **GM 不能用 Dialog**：AIR 重建 Activity 后 token 失效 → `BadTokenException` 崩溃。必须用 WindowManager 悬浮窗
- **GM 按钮不能用 addContentView/decorView**：AIR 用 SurfaceView 渲染会盖住，只有系统悬浮窗能显示

### 协议速查

```
TCP 包：[0x07][int32 总长(含11字节头)][byte ver][byte 0][int16 id][int16 type][body]  大端
服务端先发 GCCryptKey(116)：int32 algo=0, uint32 4, 00000000, uint32 4, 00000000（零密钥=明文）

604 CSLockLevel  → 605:  levelID\0 + int32 result(0) + int32 copyID + int32 onlineNum(1) + \0
606 CSEnterLevel → 607:  int32 0
                   1265: int32 0                （客户端变驱动方，本地跑关卡）
                   1211: 实体 JSON + \0          （WCEnterLevel，缺它会卡在进场横幅）
612 CSLeaveLevel → 613:  int32 0
608 CSInitPlayer → 609:  int32 0 + \0
其它未知 type    → 兜底回 (type+1) + int32 0
```

`cb.php` 的 `info=` 参数解码：跳过前 32 位 MD5 hex，其后用自定义 base64 字母表
`C*y0p]oPc[YrRi9htGuU4FaAnNg6BDEH7IOJKL5MQSTVWXZbdefjkl3mqs1vwxz28` 解码，取第一个 `{` 到最后一个 `}`。
响应要 **zlib 压缩**，`Content-Type: application/octet-stream`。

已实现的 cb type：`0`(用户数据) `2`(配置) `233`(战队) `340`(装备) `1001`(引导)
`44444`(进关卡/next) `44445`(结算+通关记录) `5422`(VIP福利)，其余回空 `ok`。

---

## 4. ★ 唯一未解决的问题：进关卡后动不了

### 现象
- 关卡能进（横幅过掉、场景加载）
- 角色**不能移动 / 不能攻击**，画面卡住
- 没有崩溃、没有报错弹窗

### 已经排除
- 605/607/1265/609/613 的包格式已和 Python 版**逐字节对齐**
- 1211 实体包已补发
- `7.war.r0` 已改用客户端上报的完整快照（37 个字段）

### 下一步怎么查（照这个做）

1. 复现：设备上开游戏 → 进任意**普通章节**关卡（别选秘境）→ 卡住
2. **立刻**抓日志（不要退出游戏，缓冲会被清）：
   ```bash
   adb logcat -d | grep "HLWSRV"
   # 或本机：mcp__phone__android_shell → logcat -d | grep HLWSRV
   ```
3. 看最后几条：
   - 出现 `未处理 type=N` → **客户端在等这个包**，在 `Tcp.java` 加 case 实现
   - 只有 `进关卡 pos=... 已回 607+1265+1211` 然后没了 → 客户端在等**别的东西**（多半是 HTTP `cb.php` 某 type，看 HTTP 侧日志）
   - 什么都没有 → 注入/线程没跑起来，看 `HLWGM`

4. 对照 Python 版：`mock/server.py` 的 `handle_packet()`（约 1160–1300 行）是**已验证能在关卡里正常玩的实现**，
   逐条比对它的回包差异是最快的路径。

### 可能的根因（按可能性排序）

1. **缺某个 CS 包的处理**：Python 的日志里进关卡后有 `1215`(49B 刷屏)、`1221`(17B)、`1223`(49B)，
   Java 版只做了兜底 `type+1`。注意 Python 的日志是 `run.out`（4.4 MB），可以 grep 对照：
   ```bash
   grep -E "recv type=" mock/run.out | tail -50
   ```
2. **`44445` 结算包**没在关卡开始时下发 → 客户端等结算数据
3. **`1265` 的 body 不是 int32 0**（Python 是 `build(1265, r)` = int32 0，但值得再核一次）
4. 关卡内怪物由 `1211` 下发，但我们的 JSON 缺必需字段（对照 `mock/server.py` 里 `mon = {...}` 的完整字段表）

---

## 5. GM 面板现状

- 悬浮窗 `TYPE_APPLICATION_OVERLAY`，清单已有 `SYSTEM_ALERT_WINDOW` 权限
- 首次启动会跳到「显示在其他应用上层」授权页 → 授权后 3 秒自动挂载（最多重试 10 次）
- 面板：标题+关闭按钮、状态行（不显示端口）、7 个角色一行、4 个属性输入+应用、重放/恢复剧情
- **不要**改回 Dialog / addContentView（见第 3 节的两个坑）

---

## 6. 设备与测试

```bash
# 装包（必须先拷到 /data/local/tmp，SELinux 禁止从 /sdcard 直接装）
cp /sdcard/Download/DSH-Go/xxx.apk /data/local/tmp/a.apk
am force-stop com.ada.snhlw.android.qihoo
pm install -r -d /data/local/tmp/a.apk

# 启动
am start -n com.ada.snhlw.android.qihoo/com.ada.ane.sdk.EntryActivity

# 抓日志
logcat -c; am start ...; sleep 20; logcat -d | grep -E "HLWGM|HLWSRV"
```

- 首次进游戏标题页显示「新游戏」是**正常**的：点「切换服」→ 选 LocalServer → 确定 → 就能按老玩家（ID 10001）进
- 游戏画面是 secure window，不能截图/点按，只能靠用户录屏 + logcat

---

## 7. 仓库（git@github.com:Pape7rlane/hlw.git）

| 分支 | 内容 |
|---|---|
| `onepkg-java` | **当前主线**：Java 服务端 + GM + 构建脚本 + 云端 workflow |
| `backup/2026-11-25-final` | 定稿快照（每次推送前请重建） |
| `proot-server` | Python 版服务端 + 存档 + 脚本备份 |
| `main` | 更早的 Chaquopy 服务端 APK 工程（已弃用） |
| `onepkg` | Python 一体包工程（已弃用，留档） |

推送规则：**每次 push 前先 `git branch -f backup/$(date +%F) main && git push -f`**。

云端构建（`.github/workflows/onepkg-java.yml`）需要：
- 仓库变量 `BASE_APK_URL`（游戏原包放 Release 后的直链，299 MB 不能进仓库）
- Secrets：`KS_BASE64`（keystore base64）、`KS_PASS`、`KS_ALIAS`

---

## 8. 历史踩坑（不要重犯）

| 坑 | 后果 |
|---|---|
| 后台线程初始化 Python | `mutex lock failed` 直接 abort |
| Chaquopy + AIR 同进程 | `SIGSEGV`（已放弃 Python，改纯 Java） |
| `roleCon.role0.url` 改成别的娃 | 武器模型拼不出来 → 卡 56% |
| 删 `Local Store/res` | 卡 40% refreshUserData，客户端不会重建 |
| 删 `cache/app` | 需要长时间重新解包 |
| HTTP 头解析按行计数 | 请求永不应答 → "获取服务器列表失败" |
| `7.war.r0` 硬编码 | 战斗缺字段 → 进关卡卡死 |

细节全在 `交接文档.md`（755 行）里，遇到 AS3/协议问题先 grep 它。

---

## 9. ★★ 2026-11-25 11:44 实机日志定位（从这里开始，别重复排查）

### 日志事实

```
11:44:06.908  锁关卡 gamelevel2000-0                    （主城）
11:44:14.521  进关卡 pos=(127,82,-485) 已回 607+1265+1211  （主城正常）
11:44:32.704  进关卡 gr=0 in=1 next={"gr_id":"group0","in_id":"instance2"}   ← 1-2 的 HTTP 应答
（此后日志空白：没有 CSLeaveLevel(612)，也没有 CSLockLevel(604)，TCP 连接仍活着）
```

### 对照 Python 版同一流程

```
[03:42:54] [44444] 进关卡 gr=0 in=1 → next=...
[03:42:54] [TCP] recv CSLeaveLevel      ← 客户端收到 44444 应答后立刻发这个
[03:42:54] [TCP] send type=613
```

### 结论

**问题不在 TCP，而在 `cb.php?type=44444` 的应答内容**：客户端拿到我们的应答后没有继续（不离开当前关卡、不请求下一关）。

### 建议的三个实验（按顺序试，每个改一行）

1. **给 44444 也下发 `te.ta`**（Python 只在 `gr_id >= 2000` 时给，但值得试）
   `Http.java` 的 `case 44444:` 里无条件加：
   ```java
   Map<String,Object> ta = new LinkedHashMap<>();
   ta.put("role", ids); ta.put("master", 0); ta.put("masterArr", ids);
   ta.put("t", "2026-11-25 00:00:00"); ta.put("ceng", -1);
   ta.put("id", g); ta.put("prop", new LinkedHashMap<>());
   Map<String,Object> te = new LinkedHashMap<>();
   te.put("ta", ta); rd.put("te", te);
   ```
2. **核对 `gr_id`/`in_id` 的数值 vs 字符串**：Python 用 `int(info["gr_id"])`，客户端可能传 `"group0"` 形态；
   打印原始 `info` 全量 JSON 看清到底传了什么（现在只打印了解析后的 int）。
3. **比对 44444 应答的字节数**：Python 是 149 字节。抓包/打印我们实际发出的 JSON 全文，逐字段对比。

### 快速定位手段

在 `Http.java` 的 cb 入口加一行，把**原始 info JSON** 和**最终 payload JSON** 都打进日志：

```java
Log_.i("cb " + t + " info=" + Json.write(info));
Log_.i("cb " + t + " resp=" + Json.write(payload));
```

复现一次 1-2，对比这两行就能看出差在哪。

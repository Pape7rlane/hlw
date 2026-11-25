# 少年葫芦娃 一体包 · Codex 接手包

## 现状一句话

一体包（游戏 + Java 服务端 + GM 面板 + 内嵌存档）已能进主城、显示武器、改属性；
**唯一问题：进关卡后客户端不进入战斗状态（画面卡住）。**

对照实验已确认：同一个客户端换成 **Python 服务端就完全正常**，所以问题在 Java 服务端的行为差异。

## 先读

1. **CODEX_交接.md** ← 第 11 节是最新进展、已排除项、下一步实验（从这里开始）
2. 交接文档.md ← 历史踩坑全记录（AS3/协议/客户端行为）

## 快速开始

```bash
pkg install -y openjdk-17 python zip unzip
apt install -y smali          # baksmali / smali
bash setup_tools.sh           # 下载 r8 / android.jar
bash build_onepkg.sh          # 生成 /root/workspace/huluwa/build/signed.apk（约 8 分钟）
```

未注入的干净客户端在 `/sdcard/Download/DSH-Go/plain_client.apk`（md5 092ff606），
原始游戏包在 `/root/workspace/huluwa/游戏_当前安装版.apk`。

## 目录

```
javasrv/src/com/hlw/srv/   Java 服务端（Json/Store/Http/Tcp/Srv）
javasrv/src/com/hlw/gm/    GM 悬浮面板
javasrv/assets/            满级存档
tools/cmp_r0.py            并排起两个服务端、喂同一快照、diff r0
tools/probe_tcp.py         直连 TCP 发 604/606，dump 回包 hex
tools/run_both.sh          同时起 Python(8080) 与 Java(8081)
tools/headless_main/       让 Java 服务端在桌面跑的入口
python_server_reference.py Python 版服务端（已验证可玩，协议细节最全）
```

## 关键线索（省时间）

Python 版正常时，客户端在收到 `613` 后会发 `1270`（CWRoleState，角色状态上报）；
Java 版从来没收到 → 说明**关卡初始化没完成**，`MainWorldFun.nowGameState` 停在 1，
`ExitGameLevel()` 抛 "未初始化完就释放了" 被 catch 吞掉，于是选关卡死。

`nowGameState=2` 只在关卡 `gameComplete` 事件里赋值 —— 所以真正要查的是"为什么关卡没触发 gameComplete"。

## 兜底

双包方案（游戏包 + 服务端 APK）实机 100% 可用，见交接文档 11.7。

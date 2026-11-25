# 纯 Java 服务端（一体包）

把服务端和 GM 面板用 **Java** 实现并注入游戏 APK，不依赖 Python 运行时。

## 为什么不用 Python

Chaquopy 与 AIR 运行时在同一进程里会原生冲突：
- 后台线程 `Python.start()` → `mutex lock failed`（abort）
- 主线程 `Python.start()` → `SIGSEGV`

改用纯 Java 后与 AIR 无任何原生交互，体积还小 10 MB。

## 结构

```
src/com/hlw/srv/Json.java    极简 JSON 解析/序列化
src/com/hlw/srv/Store.java   存档（player_ud.json）+ 战斗快照
src/com/hlw/srv/Http.java    HTTP 8080（服务器列表/会话/cb.php 各 type）
src/com/hlw/srv/Tcp.java     TCP 9000（握手 116 / 进关卡 / 心跳）
src/com/hlw/srv/Srv.java     启动入口 + GM 调用的方法
src/com/hlw/gm/Gm.java       GM 面板（addContentView 覆盖在 AIR 之上）
```

## 注入点

`com/ada/ane/sdk/EntryActivity.smali` 的 `onCreate`：

```smali
invoke-static {p0}, Lcom/hlw/gm/Gm;->start(Landroid/app/Activity;)V
```

## 构建

`bash javasrv/build.sh`（需要 `BASE_APK` 环境变量）。

云端：把游戏原包放 Release，设 `BASE_APK_URL` 变量 + `KS_BASE64`/`KS_PASS`/`KS_ALIAS` 密钥，push 到 `onepkg-java` 即可。

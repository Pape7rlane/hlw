# 少年葫芦娃 · 单机服务端 APK

把原本跑在电脑/Proot 里的 Python 单机服务端，打包成手机 App（Chaquopy 内嵌 CPython）。

启动后在本机监听：

| 端口 | 用途 |
|---|---|
| HTTP 8080 | 服务器列表、会话、配置、进关卡、战斗结算 |
| TCP 9000 | 游戏二进制协议 |

游戏本体连的就是 `127.0.0.1`，所以装在同一台手机上即可。

## 编译

推送到 GitHub 后由 Actions 自动编译（见 `.github/workflows/build.yml`），
产物在 Actions 页面的 **Artifacts → huluwa-localserver-apk**。

> 第一次构建要下载 Gradle / Android SDK / Chaquopy 的 Python，大约 5-10 分钟。

## 结构

```
app/src/main/python/      Python 服务端（server.py + 数据种子）
app/src/main/java/...     MainActivity（启动界面）+ ServerService（前台服务跑 Python）
```

存档写在应用私有目录 `filesDir`（APK 内资源只读，首次启动会复制出来）。

# 一体包构建（游戏 + 服务端 + GM 面板）

把 Python 服务端和 GM 面板注入游戏 APK，产出一个独立安装包。

## 目录

```
src/com/hlw/gm/GmAuto.java      GM 面板 + 启动 Python 服务端
stubs/                          Chaquopy/Android 编译桩（仅编译期用）
prebuilt/lib/                   Chaquopy v7a 运行时（.so）
prebuilt/assets/chaquopy/       Python 运行时资源（app.imy 内含 server.py）
prebuilt/smali/chaquo/          Chaquopy Java 类（smali）
scripts/build.sh                完整构建流程
```

## 构建

1. 把原始游戏 APK 传到仓库 **Release**，得到直链
2. 仓库 Variables 里设置 `BASE_APK_URL` = 该直链
3. Secrets 里设置签名：`KS_BASE64`（keystore 的 base64）、`KS_PASS`、`KS_ALIAS`
4. push 到 `onepkg`（无卡密）或 `onepkg-key`（卡密版）触发构建

## 两个分支

| 分支 | 说明 |
|---|---|
| `onepkg` | 无卡密，装上即可玩 |
| `onepkg-key` | 卡密版，首启需输入激活码，一码一机 |

## 注意

- 游戏只带 `armeabi-v7a` 库，所以 Python 运行时也必须用 v7a
- 注入点是 `com/ada/ane/sdk/EntryActivity.smali` 的 `onCreate`
- GM 面板通过 `addContentView` 覆盖在 AIR 画面之上，不需要悬浮窗权限

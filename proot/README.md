# proot 版服务端与存档（备份分支）

开发时跑在 proot Ubuntu 里的服务端与存档，与 `main`（打进 APK 的版本）分开保存。

## 内容

| 文件 | 说明 |
|---|---|
| `server.py` | 功能最全的一版（灵葫/总战力/装备上限/VIP15/自定义属性/剧情重放/5422福利/装备外观过滤） |
| `player_ud.json` | 存档（102 关、760 道具、7 娃满配） |
| `player_states.json` | 各角色战斗快照 |
| `props_seed.json` | 756 种道具 id |
| `交接文档.md` | 全部踩坑记录与配置结构（含"操作红线"一节） |
| `patch_dats.py` / `patch_level.py` / `repack_level.py` | 关卡数据注入与重打包脚本 |

## 恢复

把这些文件放回一个目录，`python3 server.py` 即可（HTTP 8080 / TCP 9000）。
客户端数据目录不要裸删，见交接文档第 9 节。

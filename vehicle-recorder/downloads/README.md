# 行车记录仪测试安装包

## 最新版 1.2.0

<img src="../docs/assets/vehicle-recorder-logo.png" alt="行车记录仪图标" width="96" />

[下载 vehicle-recorder-1.2.0-debug.apk](./vehicle-recorder-1.2.0-debug.apk?raw=true)

- 初始默认横屏，可手动切换竖屏并记住选择；显示与后续录像方向同步。
- 预览占满应用窗口，默认居中铺满，可切为完整画面；仅预览裁切，不裁切保存视频。
- 默认收起菜单，只保留醒目菜单按钮和小型 REC 状态。点击展开，8 秒无触摸自动收起。
- 横竖屏切换先保存当前片段后继续，可能有短暂间隔。

[横竖屏/全屏/菜单说明](../docs/DISPLAY_MODES.md) · [使用说明](../README.md) · [开发文档](../docs/DEVELOPMENT.md) · [更新记录](../docs/CHANGELOG.md)

构建、Android lint 及自动化检查通过：[构建记录](https://github.com/kafeiwu2012/Family-Interaction-Platform-Design/actions/runs/40464350939)。R7sm 真机菜单交互、横竖屏方向及熄屏仍待回归。

**已核对：本包与此前提供的 1.1.1 测试包签名不同，不能直接覆盖安装。先备份录像并记录保存路径，再卸载旧版安装；应用专用目录随卸载删除。**

SHA-256：`d6ff355cacf28282a6cf3bcd48a7a73cdd69c8e7ab0ab066aee994327a0cf9a5`，亦见 SHA256SUMS。

## 历史版

- [1.1.1](./vehicle-recorder-1.1.1-debug.apk?raw=true)
- [1.1.0](./vehicle-recorder-1.1.0-debug.apk?raw=true)
- [1.0.0](./vehicle-recorder-1.0.0-debug.apk?raw=true)

Debug 包仅供测试；正式分发请自行长期保存 release 签名。

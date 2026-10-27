# 行车记录仪测试安装包

## 最新版 1.3.1

[下载 vehicle-recorder-1.3.1-debug.apk](./vehicle-recorder-1.3.1-debug.apk?raw=true)

**删除录像：齿轮 → 录像库 → 多选 → 全选当前 → 红色“删除所选” → 确认。保护录像自动跳过。**

- “删除所选”紧邻“全选当前”，窄屏分为两行，删除入口无需横向滑动寻找。
- 原“清空”只会撤销勾选，现明确更名为“取消选择”：选择数归零、勾选同步清除、文件保留。
- 无选择时禁用导出/分享/删除；保留单条和批量导出、保护及播放。

1.3.0 不升级也能删除：全选后向左滑操作条，点最右侧红色“删除”并确认；该版本的“清空”不会删除录像。

[录像管理说明](../docs/RECORDING_MANAGEMENT.md) · [使用说明](../README.md) · [更新记录](../docs/CHANGELOG.md) · [构建记录](https://github.com/kafeiwu2012/Family-Interaction-Platform-Design/actions/runs/40470672022)

编译、Android lint 和现有自动化检查通过，R7sm 实际点击及显示效果仍待确认。

**已核对本包与 1.3.0 测试签名不同。请先导出或用文件管理器备份重要录像、记录路径，再卸载旧版安装；应用专用目录会随卸载删除。**

SHA-256：`3b30f181465189e74cd8b44b6410d3b89bcbbb9d29eb7d2907a4f818c49c068a`，亦见 SHA256SUMS。

## 历史版

- [1.3.0](./vehicle-recorder-1.3.0-debug.apk?raw=true)
- [1.2.2](./vehicle-recorder-1.2.2-debug.apk?raw=true)
- [1.2.1](./vehicle-recorder-1.2.1-debug.apk?raw=true)
- [1.2.0](./vehicle-recorder-1.2.0-debug.apk?raw=true)
- [1.1.1](./vehicle-recorder-1.1.1-debug.apk?raw=true)
- [1.1.0](./vehicle-recorder-1.1.0-debug.apk?raw=true)
- [1.0.0](./vehicle-recorder-1.0.0-debug.apk?raw=true)

Debug 包仅供测试；正式分发请自行维护长期 release 签名。

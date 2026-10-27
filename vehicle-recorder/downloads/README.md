# 行车记录仪测试安装包

## 最新版 1.3.0

[下载 vehicle-recorder-1.3.0-debug.apk](./vehicle-recorder-1.3.0-debug.apk?raw=true)

**入口：齿轮 → 录像库 → 多选（或长按录像）**

- 多选、全选当前筛选、清空、批量删除；受保护录像自动跳过删除。
- 单条/批量导出：系统目录/SD 卡或本地路径，每次新建独立子目录，不主动删除源录像。
- 单条/批量 Android 分享，只读授权；浏览当前、默认及预存目录，不改变录制保存位置。
- 进度、取消及结果汇总。窄屏操作条可左右滚动。
- 保留前置竖屏方向修复、齿轮轻量点击反馈及全部录制功能。

[批量管理和导出说明](../docs/RECORDING_MANAGEMENT.md) · [使用说明](../README.md) · [更新记录](../docs/CHANGELOG.md) · [构建记录](https://github.com/kafeiwu2012/Family-Interaction-Platform-Design/actions/runs/40469129858)

编译、Android lint、文件操作及现有自动化检查通过；系统目录选择器、外置卡和分享接收应用仍待 R7sm 真机验收。导出期间未保护的原录像仍参与循环覆盖，重要录像先保护。

**本包与此前提供的 1.2.2 测试签名不同。升级前请使用文件管理器备份录像、记下保存路径，再卸载旧版安装；应用专用目录随卸载删除。**

SHA-256：`13e9b31a87d102ff38c2955e8344dba074de252221344d1a19f593599700f77a`，亦见 SHA256SUMS。

## 历史版

- [1.2.2](./vehicle-recorder-1.2.2-debug.apk?raw=true)
- [1.2.1](./vehicle-recorder-1.2.1-debug.apk?raw=true)
- [1.2.0](./vehicle-recorder-1.2.0-debug.apk?raw=true)
- [1.1.1](./vehicle-recorder-1.1.1-debug.apk?raw=true)
- [1.1.0](./vehicle-recorder-1.1.0-debug.apk?raw=true)
- [1.0.0](./vehicle-recorder-1.0.0-debug.apk?raw=true)

Debug 包仅供测试；正式分发请自行维护长期 release 签名。

# 行车记录仪测试安装包

## 最新版 1.2.2

[下载 vehicle-recorder-1.2.2-debug.apk](./vehicle-recorder-1.2.2-debug.apk?raw=true)

- 菜单入口改为圆圈内居中的齿轮，展开时显示关闭图标，保留 52dp 点击区域和中文读屏描述。
- 点击时按钮轻微上浮并放大后恢复，默认动画时间合计约 240ms；无循环动效，不对相机画面加动画。
- 系统省电模式或关闭动画时跳过，连续点击、切屏或熄屏时取消复位。仍有少量瞬时渲染开销，不承诺零功耗。
- 保留 1.2.1 前置竖屏预览方向修复及全部录像功能。

[菜单交互说明](../docs/MENU_INTERACTION.md) · [使用说明](../README.md) · [更新记录](../docs/CHANGELOG.md) · [构建记录](https://github.com/kafeiwu2012/Family-Interaction-Platform-Design/actions/runs/40466941601)

编译、Android lint 和现有自动化检查通过，实际交互和功耗表现仍待 R7sm 真机验证。

**本次已核对与此前提供的 1.2.1 测试签名不同。先备份录像、记录保存路径，再卸载旧版安装；应用专用目录随卸载删除。**

SHA-256：`26369edc3d63ac5e016af3c108ddbc80e82118b4ea1f78f8d05519f489bf5667`，亦见 SHA256SUMS。

## 历史版

- [1.2.1](./vehicle-recorder-1.2.1-debug.apk?raw=true)
- [1.2.0](./vehicle-recorder-1.2.0-debug.apk?raw=true)
- [1.1.1](./vehicle-recorder-1.1.1-debug.apk?raw=true)
- [1.1.0](./vehicle-recorder-1.1.0-debug.apk?raw=true)
- [1.0.0](./vehicle-recorder-1.0.0-debug.apk?raw=true)

Debug 包仅供测试；正式分发请自行维护长期 release 签名。

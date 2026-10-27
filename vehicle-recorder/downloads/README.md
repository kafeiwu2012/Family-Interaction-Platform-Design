# 行车记录仪测试安装包

## 最新版 1.2.1

[下载 vehicle-recorder-1.2.1-debug.apk](./vehicle-recorder-1.2.1-debug.apk?raw=true)

修复前置摄像头竖屏预览倒置：前置预览独立做方向镜像补偿，不再复用 MP4 方向元数据角度。保留前置横屏、后置显示及原录像方向计算。

安装后将“方向校正”先设为 0°，分别检查前置横/竖屏预览，再回放一段新保存的视频。编译、Android lint 及自动化回归测试已通过，R7sm 真机效果仍待验证。

[显示模式与修复说明](../docs/DISPLAY_MODES.md) · [使用说明](../README.md) · [更新记录](../docs/CHANGELOG.md) · [构建记录](https://github.com/kafeiwu2012/Family-Interaction-Platform-Design/actions/runs/40465739975)

**已核对：本包与此前提供的 1.2.0 测试签名不同，不能覆盖安装。先备份录像、记录保存路径，再卸载旧版安装；应用专用目录随卸载删除。**

SHA-256：`85ce36a013282eccc7d92016a81e8a8f51dd695aebe05f8df6e18f56e52c0c40`，亦见 SHA256SUMS。

## 历史版

- [1.2.0](./vehicle-recorder-1.2.0-debug.apk?raw=true)
- [1.1.1](./vehicle-recorder-1.1.1-debug.apk?raw=true)
- [1.1.0](./vehicle-recorder-1.1.0-debug.apk?raw=true)
- [1.0.0](./vehicle-recorder-1.0.0-debug.apk?raw=true)

Debug 包仅供测试，正式分发请自行维护长期 release 签名。

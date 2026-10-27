# Family Interaction Platform

仓库按功能子模块组织，包含家庭影像采集与 OPPO R7sm 行车记录仪。

| 子模块 | 内容 | 入口 |
| --- | --- | --- |
| `android-family-capture/` | Android 定时拍照、短录像、串行镜头交替及树莓派接收服务 | [源码与说明](android-family-capture/README.md) |
| `vehicle-recorder/` | OPPO R7sm 行车记录仪：循环录像、容量与路径设置、前后镜头切换、熄屏录制、GPU 预览、录制中设置及启动自动录像 | [源码与说明](vehicle-recorder/README.md) |

## 行车记录仪 · v1.1.1

<img src="vehicle-recorder/docs/assets/vehicle-recorder-logo.png" alt="行车记录仪图标" width="96" />

1.1.1 更新桌面、应用内及录制通知图标。

面向 Android 5–8 的原生行车记录仪，提供全屏预览、录像状态、三页设置与可按镜头或保护状态筛选的录像库。视频采用“前/后摄像头 + 日期时间”命名，支持片段保护和旧版录像管理。

- **[下载行车记录仪 APK（v1.1.1）](vehicle-recorder/downloads/vehicle-recorder-1.1.1-debug.apk?raw=true)**
- [安装与使用说明](vehicle-recorder/README.md)
- [开发文档：架构、性能优化、设置流程与真机验收](vehicle-recorder/docs/DEVELOPMENT.md)
- [版本更新记录](vehicle-recorder/docs/CHANGELOG.md)
- [安装包与 SHA-256 校验](vehicle-recorder/downloads/)

编译、Android lint 和自动化测试已通过；新版实际流畅度、熄屏和长时间稳定性仍需 R7sm 真机验收。修改编码或存储设置时会保存当前片段后继续，存在短暂间隔。

**升级至 v1.1.1：与此前提供的 v1.0.0 / v1.1.0 测试包签名不同。请先备份录像并记录保存路径，再卸载旧版安装；应用专用目录随卸载删除。**

## 家庭影像采集

- [最新测试 APK（v0.4.2）](android-family-capture/downloads/family-capture-0.4.2-debug.apk)
- [开发指南：构建环境、代码修改、测试与 CI](android-family-capture/docs/DEVELOPMENT.md)
- [模块架构：拍摄流程、录像、镜头交替及存储](android-family-capture/docs/ARCHITECTURE.md)
- [服务端接口：认证、远程配置、照片上传与状态](android-family-capture/docs/API.md)
- [部署说明](android-family-capture/docs/DEPLOY.md)
- [真机测试步骤](android-family-capture/docs/DEVICE-TEST.md)

GitHub Actions 工作流位于仓库根目录 `.github/workflows/`，构建路径已指向对应子模块。

开发者入口：[开发文档索引](docs/README.md) · [系统与模块组织](docs/SYSTEM.md) · [家庭采集接口](android-family-capture/docs/API.md) · [家庭采集开发指南](android-family-capture/docs/DEVELOPMENT.md) · [行车记录仪开发指南](vehicle-recorder/docs/DEVELOPMENT.md)。


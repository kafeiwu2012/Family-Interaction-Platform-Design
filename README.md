# Family Interaction Platform

仓库按功能子模块组织，包含家庭影像采集、OPPO R7sm 行车记录仪，以及 Windows 与 iPhone/iPad 局域网文件共享。

| 子模块 | 内容 | 入口 |
| --- | --- | --- |
| `android-family-capture/` | Android 定时拍照、短录像、串行镜头交替及树莓派接收服务 | [源码与说明](android-family-capture/README.md) |
| `vehicle-recorder/` | OPPO R7sm 行车记录仪：循环录像、容量与路径设置、前后镜头切换、熄屏录制、GPU 预览、录制中设置及启动自动录像 | [源码与说明](vehicle-recorder/README.md) |
| `LanBridge/` | Windows 与 iPhone/iPad 局域网文件共享：上传下载、文件预览、复制、移动、改名、删除与文本编辑，支持传输进度、分块校验及备份 | [软件下载](LanBridge/downloads/LanBridge-Windows.zip?raw=true) · [源码与说明](LanBridge/README.md) |

## LanBridge · Windows 与 iPhone/iPad 文件共享

**[下载 Windows 软件包（x64 / ARM64 EXE，含源码）](LanBridge/downloads/LanBridge-Windows.zip?raw=true)** · [使用说明](LanBridge/README.md) · [SHA-256 校验值](LanBridge/downloads/SHA256SUMS.txt)

解压后运行对应架构的 EXE，电脑和 iPhone/iPad 连接同一 Wi-Fi，在 Safari 打开程序窗口显示的网址即可使用。Windows 端自动打开操作界面。

上传采用分块 SHA-256 校验和失败重试，完成后提交文件；提供传输进度，删除文件和文本编辑前版本保留备份。iOS 通过系统选择器上传与下载，只能管理共享空间和主动选择的文件。

当前版本适用于可信局域网，使用 HTTP；已通过自动化测试和交叉编译，Windows 与 iPhone/iPad 真机兼容性仍待验证。

## 行车记录仪 · v1.3.1

<img src="vehicle-recorder/docs/assets/vehicle-recorder-logo.png" alt="行车记录仪图标" width="96" />

1.3.1 将红色“删除所选”放在“全选当前”旁，竖屏两行操作栏直接显示。原“清空”改名为“取消选择”，明确区分撤销勾选和删除文件。

1.3.0 补齐录像库多选、全选当前筛选、批量删除、导出和分享。入口：齿轮 → 录像库 → 多选；保护录像自动跳过批量删除，导出复制到独立子目录。

1.2.2 菜单入口改为居中的圆形齿轮，点击短暂上浮回弹；无持续动效，省电模式或关闭动画时跳过。

1.2.1 修复前置摄像头竖屏预览倒置，方向校正建议先使用 0°。

1.2.0 新增手动横竖屏（初始默认横屏）、铺满预览和折叠菜单；点击右上角醒目菜单标记展开，8 秒无触摸自动收起。

面向 Android 5–8 的原生行车记录仪，提供全屏预览、录像状态、三页设置与可按镜头或保护状态筛选的录像库。视频采用“前/后摄像头 + 日期时间”命名，支持片段保护和旧版录像管理。

- **[下载行车记录仪 APK（v1.3.1）](vehicle-recorder/downloads/vehicle-recorder-1.3.1-debug.apk?raw=true)**
- [安装与使用说明](vehicle-recorder/README.md)
- [开发文档：架构、性能优化、设置流程与真机验收](vehicle-recorder/docs/DEVELOPMENT.md)
- [横竖屏、全屏与折叠菜单说明](vehicle-recorder/docs/DISPLAY_MODES.md)
- [菜单图标与轻量点击反馈](vehicle-recorder/docs/MENU_INTERACTION.md)
- [批量删除、导出与分享说明](vehicle-recorder/docs/RECORDING_MANAGEMENT.md)
- [版本更新记录](vehicle-recorder/docs/CHANGELOG.md)
- [安装包与 SHA-256 校验](vehicle-recorder/downloads/)

编译、Android lint 和自动化测试已通过；新版实际流畅度、熄屏和长时间稳定性仍需 R7sm 真机验收。修改编码或存储设置时会保存当前片段后继续，存在短暂间隔。

**升级至 v1.3.1：已核对与此前提供的 v1.3.0 测试包签名不同。请先备份录像并记录保存路径，再卸载旧版安装；应用专用目录随卸载删除。**

## 家庭影像采集

- [最新测试 APK（v0.4.2）](android-family-capture/downloads/family-capture-0.4.2-debug.apk)
- [开发指南：构建环境、代码修改、测试与 CI](android-family-capture/docs/DEVELOPMENT.md)
- [模块架构：拍摄流程、录像、镜头交替及存储](android-family-capture/docs/ARCHITECTURE.md)
- [服务端接口：认证、远程配置、照片上传与状态](android-family-capture/docs/API.md)
- [部署说明](android-family-capture/docs/DEPLOY.md)
- [真机测试步骤](android-family-capture/docs/DEVICE-TEST.md)

GitHub Actions 工作流位于仓库根目录 `.github/workflows/`，构建路径已指向对应子模块。

开发者入口：[开发文档索引](docs/README.md) · [系统与模块组织](docs/SYSTEM.md) · [家庭采集接口](android-family-capture/docs/API.md) · [家庭采集开发指南](android-family-capture/docs/DEVELOPMENT.md) · [行车记录仪开发指南](vehicle-recorder/docs/DEVELOPMENT.md)。


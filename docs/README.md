# 开发文档

文档基于当前仓库代码编写。家庭采集模块基线为 v0.4.2，行车记录仪为 v1.1.0；目录迁移不改变 APK 版本。

| 文档 | 内容 |
| --- | --- |
| [系统范围与模块组织](SYSTEM.md) | 当前功能边界、仓库结构、后续子模块接入规则 |
| [模块架构](../android-family-capture/docs/ARCHITECTURE.md) | Android 类职责、拍摄流程、录像、交替及数据存储 |
| [服务端接口](../android-family-capture/docs/API.md) | 身份认证、请求响应、错误码、上传幂等 |
| [开发指南](../android-family-capture/docs/DEVELOPMENT.md) | 构建环境、源码修改入口、测试命令及 CI |
| [行车记录仪开发文档](../vehicle-recorder/docs/DEVELOPMENT.md) | GPU预览、录制中设置、循环存储、命名及真机回归 |
| [行车记录仪更新记录](../vehicle-recorder/docs/CHANGELOG.md) | 独立版本变更与交付记录 |
| [部署说明](../android-family-capture/docs/DEPLOY.md) | 树莓派/云主机、TLS、systemd及运维 |
| [真机验收](../android-family-capture/docs/DEVICE-TEST.md) | OPPO R7sm操作步骤、历史问题和复测要求 |

文档中的“已实现”表示存在代码，不等同于真机验收通过。录像、熄屏、主题显示仍须在目标固件复核。后续家庭物联网模块的具体需求尚未导入，不将推测功能列为已确认范围。

行车记录仪从1.0.0测试包升级到1.1.0存在签名差异，安装前必须按模块说明备份录像；不能沿用家庭采集APK的覆盖安装说明。

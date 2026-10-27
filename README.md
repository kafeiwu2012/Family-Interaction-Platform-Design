# Family Interaction Platform

仓库按功能子模块组织，后续功能可在根目录下增加独立目录。

| 子模块 | 内容 | 入口 |
| --- | --- | --- |
| `android-family-capture/` | Android 定时拍照、短录像、串行镜头交替及树莓派接收服务 | [源码与说明](android-family-capture/README.md) |

- [最新测试 APK（v0.4.2）](android-family-capture/downloads/family-capture-0.4.2-debug.apk)
- [部署说明](android-family-capture/docs/DEPLOY.md)
- [真机测试步骤](android-family-capture/docs/DEVICE-TEST.md)

GitHub Actions 工作流位于仓库根目录 `.github/workflows/`，构建路径已指向子模块。

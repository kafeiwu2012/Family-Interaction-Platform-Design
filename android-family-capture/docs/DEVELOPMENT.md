# 开发指南

## 环境与入口

先克隆仓库，再进入 `android-family-capture/`。以下命令均以该目录为工作目录。

| 项目 | 要求 |
| --- | --- |
| Android构建 | JDK17，Gradle wrapper8.9，AGP8.7.3 |
| SDK | compileSdk35，Build Tools35.0.0 |
| 运行基线 | minSdk22、targetSdk22，Java8源代码级别，无第三方Android运行时依赖 |
| 服务端 | Python3.7+标准库；CI使用Python3.9 |
| 设备 | OPPO R7sm Android5.1.1，ColorOS行为须实机验证 |

该APK是旧设备侧载测试版本，targetSdk22不符合现代Google Play上架要求。提高targetSdk必须同步设计运行时权限、通知及后台摄像头限制，不应仅改版本数字。

## 构建与安装

```sh
cd android
./gradlew assembleDebug lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android Studio打开模块内 `android/`。SDK位置用本地 `local.properties` 或环境变量配置，不提交机器绝对路径。debug签名只用于测试；升级安装要求签名一致，正式部署使用自己的release签名并保存密钥。

## 自动测试

服务端测试从模块目录执行：

```sh
python3 -m unittest discover -s server -v
```

纯Java测试：

```sh
mkdir -p /tmp/family-media-tests
javac -d /tmp/family-media-tests \
  android/app/src/main/java/org/family/capture/MediaNames.java \
  android/app/src/main/java/org/family/capture/MediaArchive.java \
  android/app/src/main/java/org/family/capture/LensSchedule.java \
  tests/java/org/family/capture/MediaArchiveTest.java \
  tests/java/org/family/capture/LensScheduleTest.java
java -cp /tmp/family-media-tests org.family.capture.MediaArchiveTest
java -cp /tmp/family-media-tests org.family.capture.LensScheduleTest
```

覆盖范围：认证/配置/状态、上传重试与冲突、中文名称和路径拒绝、ZIP内容完整性/保留源文件、镜头交替边界与延后切换。未覆盖真实相机、编码器、EGL、ColorOS熄屏、MediaPlayer解码及主题渲染；这些必须执行 [真机验收](DEVICE-TEST.md)。

## 修改入口与约束

- 参数/运行说明修改在MainActivity，保存键须与CaptureService保持一致；范围校验在两侧保持一致。
- 改拍摄时序时保持单一busy约束和相机释放缓冲，不恢复并发打开；时钟使用elapsedRealtime，文件命名使用设备墙上时间。
- 改录像参数时同时验证前摄/后摄、文件可播放及停止清理；保留具体失败阶段。不要让下一张照片覆盖最近录像错误。
- 更改媒体命名需同步MediaNames、服务端名称白名单、URL编码及补传兼容测试。
- 本地文件与上传队列分开；用户删除必须确认，导出不删除源文件。现有单设备令牌不能作为多设备认证设计。
- 接收服务只绑定回环，TLS交给Caddy；完整上线与systemd路径见 [部署说明](DEPLOY.md)。

## CI与交付

工作流位于仓库根 `.github/workflows/verify.yml`。server任务运行Python测试；android任务安装SDK、运行纯Java测试、assembleDebug和Lint，然后上传 `family-capture-debug` artifact。SDK安装已明确排除废弃的tools包。CI通过只说明所列检查通过，不等于R7sm实测通过。

源码更新时检查文档链接、模块工作目录及artifact路径。新APK应更新versionCode/versionName并验证API22签名，然后放入 `downloads/`，更新该目录SHA256SUMS。仓库历史APK使用调试签名，CI临时调试密钥可能不同；不能假设CI artifact可覆盖已安装的仓库APK。

发布说明应列出改动、自动检查、真机结果及未验证项。保持拍摄常驻通知、本机停止入口和显式本地启动；不增加隐蔽启动流程。

# Family Capture · 第一版源码

Android 5.1.1 / API 22 定时拍照 → HTTPS → 树莓派 3B（或 Linux 云主机）。仅用于知情且授权的拍摄。摄像头运行有常驻通知和本机停止按钮，不开机自启、不远程唤醒已停止的服务。

已实现源码：定时拍照、前后摄交替选择、熄屏期间 partial wake lock、JPEG 私有文件队列、断网重试、幂等上传、设备/管理员分离令牌、远程暂停/恢复/切摄/调间隔、在线状态和摄像头能力报告。Android 端没有第三方运行时依赖，所有调用以 API 22 为基线。

**尚未实现：分段录像、分别控制两路录像、实时视频、云存储适配器、推送告警、自动清理服务器历史文件、多设备管理。** 云主机可部署同一接收服务。API 22 无标准双摄并发枚举接口；第一版报告未知，不尝试同时打开两颗摄像头。

**验证状态：已编译 debug APK，执行 Android Lint 和服务端集成测试；APK 使用兼容 API 22 的 v1 签名。未在 OPPO R7sm 实测，不能据此宣称熄屏拍摄已适配成功。** ColorOS 可能限制后台相机、唤醒锁或杀死进程；硬件以真机结果为准。第一版面向 API 22，不承诺现代 Android 后台摄像头行为或应用商店上架兼容性。

- [构建及部署](docs/DEPLOY.md)
- [真机测试及验收](docs/DEVICE-TEST.md)
- `android/`：原生 Java 工程
- `server/`：Python 标准库服务及集成测试

运行测试：`cd server && python3 -m unittest -v`

测试 APK：[下载 v0.1.0 debug](downloads/family-capture-0.1.0-debug.apk) · [SHA256](downloads/SHA256SUMS)。

## v0.2.0 本地测试

[下载本地测试版 APK](downloads/family-capture-0.2.0-debug.apk)。勾选“本地测试”，无需填写服务地址/令牌，即可定时拍照、切换前后摄，并在应用内查看和删除测试照片。无需连接 iPhone 热点。照片存于应用私有目录，与补传队列分开，不自动上传；约256MiB上限。更改模式/间隔须先停止再启动。真机熄屏行为尚待测试。

# 构建与部署

项目现位于仓库子目录 `android-family-capture/`。以下相对路径命令均从该子目录执行，先在仓库根目录运行 `cd android-family-capture`。Android Studio 应打开该目录中的 `android/` 工程。

## Android

安装 Android Studio、JDK 17、Android SDK Platform 35 / Build Tools 35.0.0。工程使用 AGP 8.7.3，提供 Gradle 8.9 wrapper；Gradle 是构建工具，不是手机端依赖。SDK 35 编译不改变最低 API 22。

```sh
cd android
./gradlew assembleDebug lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

仓库 `downloads/` 目录提供测试用 debug APK；使用标准调试签名，非生产发布签名，不含服务地址或令牌。安装后需自行配置。正式部署前完成真机验收并使用自己的发布签名。

首次构建需要下载 Google Maven 工件。正式使用配置自己的 release keystore，备份密钥，不要将其提交 Git。targetSdk 22 是此遗留设备版本的明确选择；API 22 在安装时授予 CAMERA 权限。不要直接升级 targetSdk 后认为行为不变。

## 树莓派 3B / 云主机

推荐 Raspberry Pi OS Bullseye/Bookworm，Python >=3.7，无 pip 依赖。先确认系统时间准确、持久磁盘容量充足。以下以整个仓库克隆到 `/opt/family-capture`、当前目录为 `/opt/family-capture/android-family-capture` 为例：

```sh
sudo useradd --system --home /var/lib/family-capture --shell /usr/sbin/nologin familycapture
sudo install -d -o familycapture -g familycapture -m 700 /var/lib/family-capture
python3 -c 'import secrets; print(secrets.token_hex(32)); print(secrets.token_hex(32))'
sudo install -m 600 /dev/null /etc/family-capture.env
sudoedit /etc/family-capture.env
```

填入不同的随机令牌（不要使用文档示例值）：

```ini
DEVICE_TOKEN=第一个随机令牌
ADMIN_TOKEN=第二个随机令牌
CAPTURE_DATA=/var/lib/family-capture
PORT=8080
```

```sh
sudo cp server/family-capture.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now family-capture
sudo systemctl status family-capture
```

安装 Caddy，替换 `server/Caddyfile` 中域名并配置到 `/etc/caddy/Caddyfile`，启动 Caddy。DNS 指向服务端，公网 80/443 可达即可申请公共证书。只暴露 HTTPS，Python 仅监听回环地址。不要将 8080 直接暴露公网。局域网部署也应使用设备信任的有效证书；不要禁用证书校验。Android 5.1 的旧根证书库可能不信任当前证书链，必须在真机验证，必要时由设备管理者安装所需 CA。云主机使用同一流程。

App 填 HTTPS 地址和 DEVICE_TOKEN。本机输入的间隔用于启动，首次连接后由服务端配置覆盖（默认 60 秒）。所有设备配置均持久化于 SQLite，服务器重启保留。令牌保存在手机应用私有 SharedPreferences；API 22 不提供现代硬件安全存储保证，丢失手机时应轮换令牌。

## 远程控制

在受信任的管理终端设置 `BASE` 和 `ADMIN_TOKEN` 环境变量；不要将真实令牌写入共享脚本或仓库。

```sh
curl --fail "$BASE/admin/config" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"camera":"front","enabled":true,"interval":60}'
curl --fail "$BASE/admin/status" -H "Authorization: Bearer $ADMIN_TOKEN"
```

camera 为 front/back；enabled=false 暂停拍照但维持控制连接；enabled=true 恢复。正在进行的单张拍摄允许完成，下一张使用新摄像头。手机正常联网约每 5 秒拉取配置，积压上传/超时会增加延迟；失败指数退避最长 60 秒。本机停止后远程不能重启。

状态 `desired` 是期望配置，`reported` 是手机最近回报，`received_at` 为服务器时间，超过 90 秒标记离线。上传文件保存在 `/var/lib/family-capture/photos`，通过 SSH/SFTP 下载；不提供公开图片 URL。状态通过 API 和手机通知查看，尚无短信/邮件推送。

## 队列及运维

手机队列位于应用私有 files/queue。超过约 256 MiB 或可用空间不足 32 MiB 时暂停拍摄，保留旧文件；并非无限缓存。上传每轮最多三张，按文件名时间排序。服务器用文件名+SHA256 保证重试幂等，收到 201 才删除手机文件。文件原子重命名并 fsync 后记录数据库；响应丢失时可安全补传。卸载/清数据会删除尚未上传文件。

服务器每次上传最多 16 MiB，低磁盘空间返回 507。部署前设置磁盘监控和人工归档策略；第一版无服务器保留期自动删除。备份数据目录时应停止服务或使用 SQLite backup API，并同时备份 photos。令牌轮换后重启服务，在本机停止服务并更新设备令牌。需要保密静态媒体时使用磁盘加密并限制系统账户访问。

v0.3.0：新版照片使用前/后摄及日期时间命名，上传路径使用UTF-8百分号编码。Android与`server/server.py`需同步升级并重启接收服务；服务端保留旧文件名接收能力。短录像循环与双摄诊断仅用于本地测试，视频不进入上传队列。ZIP导出通过API 19已有的系统文件选择器保存，无需额外存储权限。

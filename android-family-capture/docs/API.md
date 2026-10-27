# 接收服务HTTP接口

以 `server/server.py` 为准。Python服务仅监听 `127.0.0.1:8080`（端口可配置），外部经Caddy HTTPS访问。当前单设备模型；不提供公开照片下载接口、视频上传或多租户隔离。

## 认证及请求约定

请求头：`Authorization: Bearer <TOKEN>`。`DEVICE_TOKEN` 与 `ADMIN_TOKEN` 必须不同，启动时各至少32字符。设备令牌不能访问管理员端点。客户端请求不自动跟随重定向。

所有带正文的请求必须有正确的Content-Length；不支持Transfer-Encoding/chunked。JSON正文最大16384字节，JPEG最大16777216字节。响应为JSON。

| 方法/路径 | 令牌 | 成功响应 |
| --- | --- | --- |
| GET `/api/config` | DEVICE_TOKEN | 200，当前期望配置 |
| POST `/api/status` | DEVICE_TOKEN | 200，`{"ok":true}` |
| PUT `/api/upload/{name}` | DEVICE_TOKEN | 201，`{"ok":true,"sha256":"…"}` |
| POST `/admin/config` | ADMIN_TOKEN | 200，`{"ok":true}` |
| GET `/admin/status` | ADMIN_TOKEN | 200，期望配置及最近回报 |

## 配置

GET `/api/config` 示例；POST `/admin/config` 使用相同对象，并须恰好包含这三个字段：

```json
{"camera":"back","enabled":true,"interval":60}
```

- `camera`：`front` 或 `back`。
- `enabled`：JSON布尔值，false暂停新增照片，仍保持配置拉取/补传。
- `interval`：10–86400的整数秒数；布尔值不作为整数接受。

上传模式联网后应用服务端配置。本地录像循环、自动交替和本机启停不通过该接口管理。配置持久化不表示手机已执行；应检查reported字段。

## 状态

设备POST `/api/status` 发送对象，客户端目前包含：`camera`、`enabled`、`interval`、`status`、`queue_bytes`、`capabilities`。服务端附加自身的 `received_at`（Unix秒）。目前只校验对象类型和正文大小，不逐字段强制schema。

GET `/admin/status`：

```json
{
  "desired":{"camera":"back","enabled":true,"interval":60},
  "reported":{
    "camera":"back",
    "status":"已拍摄 back",
    "queue_bytes":0,
    "received_at":1793000000.0,
    "online":false
  }
}
```

示例时间仅用于说明字段。online基于服务器接收时间，最近90秒内有回报为true；没有回报时为false。这不是实时推送、摄像头健康保证或录像成功证明。

## 照片上传及幂等

上传原始JPEG字节，建议Content-Type为image/jpeg。name作为单独路径段按UTF-8百分号编码，尤其中文及正号；不要将 `+0800` 中的正号变为空格。允许两种文件名：

```text
前摄_2026-10-27_14-30-00-123_+0800_a1b2c3d4.jpg
1793000000000_back_12345678-1234-1234-1234-123456789abc.jpg
```

新名支持前摄/后摄、毫秒和时区；旧名支持front/back。其他名称、目录穿越及非JPG被拒绝。当前JPEG校验只检查首尾标记，并非完整解码验证。

服务端计算SHA256，在SQLite写事务内检查同名记录。首次写临时文件、fsync、原子替换后记录哈希；相同name和相同内容重试仍返回201，不重复生成文件；同名不同内容返回409。手机只在201后删除本地队列文件。

## 错误处理

| 状态码 | 含义 | 客户端处理 |
| --- | --- | --- |
| 400 | 非法配置、正文、名称或JPEG | 修正请求；不删除待传文件 |
| 401 | 缺少或错误令牌 | 检查设备/管理员令牌及轮换配置 |
| 404 | 未知路径 | 检查HTTPS基础地址和版本 |
| 409 | 同名不同哈希 | 保留文件，检查名称冲突 |
| 503 | 文件或数据库操作失败 | 保留待重试，检查服务端存储 |
| 507 | 可用磁盘不足 | 清理/扩容服务端，保留手机队列 |

Caddy还可能对大正文返回413或产生网关错误。TLS失败通常发生在收到HTTP状态之前。服务端日志不记录请求正文或令牌；运维需结合systemd与代理日志排查。当前手机将非201上传统一按失败重试，不自动清除永久错误文件。

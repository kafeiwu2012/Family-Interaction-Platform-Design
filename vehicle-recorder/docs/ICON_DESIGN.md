# 行车记录仪图标 · 1.1.1

![应用图标](assets/vehicle-recorder-logo.png)

图形使用相机轮廓、镜头中的道路及红色录制指示点。深蓝底配青绿色，和应用界面配色一致；不加入小尺寸难以辨认的文字。

## 资源

- `docs/assets/vehicle-recorder-logo.svg`：可编辑矢量设计源文件。
- `docs/assets/vehicle-recorder-logo.png`：512×512 预览及文档图。
- `res/mipmap-mdpi` 到 `mipmap-xxxhdpi`：48/72/96/144/192 px PNG，供 R7sm 等旧版桌面使用。
- `res/mipmap-anydpi-v26/ic_launcher.xml`：Android 8 起的自适应图标，前景主体放在安全区域，适配圆形及圆角遮罩。
- `res/drawable/ic_stat_recorder.xml`：独立白色透明通知小图标，避免彩色桌面图标在状态栏成为方块。

Manifest 的 icon 和 roundIcon 均绑定 ic_launcher；主界面标题栏展示同一桌面资源，录制通知使用单色资源。

## 修改与生成

安装设计工具依赖 Pillow 后，在本项目目录运行：

```sh
python3 tools/render_icon.py
```

此依赖只用于设计时生成 PNG，不打包进 Android 应用。SVG、渲染脚本和自适应前景以相同几何元素维护；改图形时同步更新三者。

1.1.1 只更新图标、标题栏图标及版本标识，不修改录像逻辑，不以动态动画增加后台负担。

## 验收

已检查所有旧版资源尺寸、透明通道和 XML 格式；APK 构建及 lint 由 GitHub Actions 检查。真机确认桌面图标、最近任务和录制通知显示；若桌面仍缓存旧图标，退出桌面重进或重启手机刷新。

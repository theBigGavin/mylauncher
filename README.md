# MyLauncher

Zune / Metro 风格的 Android 第三方桌面:白底撞色锐利壁纸、超大细体时钟、单色化图标 + 超粗 App 名称竖排列表,竖屏横屏自适应。

## 功能

- 注册为系统 Launcher(HOME),`fullUser` 方向,竖横屏自适应布局
  - 竖屏:居中细体大时钟 + 竖行 App 列表
  - 横屏:时钟左下,图标一列垂线对齐、名称一列右对齐
- 主屏最多 20 个 App,底部计数,可滚动;首启默认加载常用应用(相机/浏览器/图库/设置/微信/Kimi/DeepSeek 等,按包名 + 名称匹配)
- 无图标、无界面的系统壳应用自动过滤;应用抽屉与选择器可切换"显示系统应用"
- App 图标取真实图标做**单色化**(亮度→alpha、纯白),内存 + 磁盘双缓存
- 长按菜单:替换应用(全量选择器,与主屏同款样式)/ 修改名称 / 移除
- 长按后拖动 = 自定义排序,DataStore 持久化
- **长按主屏空白处**打开设置页:图标大小、字号实时可调,图标可隐藏,可设为默认桌面,可恢复默认布局
- **空白处上滑**拉出应用抽屉,点选启动
- 默认壁纸:生成式 62° 锐利撞色斜带 + 细锐线 + 硬边圆环(Canvas 直绘,无模糊)

## 构建

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

要求:JDK 17、Android SDK(compileSdk 35,minSdk 26)。

## 目录

- `app/src/main/java/com/mylauncher/` — 源码(data / icons / ui)
- `design/` — 设计稿与效果图生成脚本(`mockup.html` 为可交互原型)
- `docs/screenshots/` — 模拟器实机验证截图

## 截图

| 竖屏主屏 | 长按菜单 | 横屏 |
|---|---|---|
| ![](docs/screenshots/shot_portrait.png) | ![](docs/screenshots/verify_menu_longpress.png) | ![](docs/screenshots/verify_landscape.png) |

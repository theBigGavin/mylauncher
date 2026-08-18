# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

MyLauncher — Zune/Metro 风格的 Android 桌面(注册为系统 HOME)。竖屏:居中细体大时钟 + 竖行 App 列表;横屏:时钟左下、列表右列。纯 Kotlin + Jetpack Compose,单模块 `:app`,包名 `com.mylauncher`。

**代码注释与 UI 文案均为中文**,新代码保持一致。

## 构建与运行

- JDK 17 + Android SDK(compileSdk 35,minSdk 26)。`gradle.properties` 里 `org.gradle.java.home` 指向本机 zulu-17 的绝对路径 — 若换机器/JDK 需改此处。
- 构建:`./gradlew :app:assembleDebug`
- 安装:`adb install -r app/build/outputs/apk/debug/app-debug.apk`
- 项目**没有测试**和 lint 配置,无 test source set。
- **版本制度**:versionName 用语义化三段 `主.次.补丁`(新功能=次+1、纯修复=补丁+1);versionCode 单调 +1(本次 1.8.2 / code 42)。每次对外交付(含功能批次)必须在同一次提交里升版本号——用户会依据版本号判断是否已更新,漏升视为 bug。
- **发版流程(红线,详见 docs/release-policy.md)**:release 包发布一律由 Gavin 本人执行,AI/agent 不执行 `./gradlew :app:assembleRelease`、不打 tag、不 `gh release create`。AI 职责边界:只改代码 + 同一次提交里升版本号 + `git commit && git push origin main`;交付说明写清版本号即可,由 Gavin 编译签名发布。原因:release 签名密钥库只在 Gavin 机器上,AI 侧无密钥库,assembleRelease 会产出 debug 签名包,无法覆盖安装正式签名的旧版本(丢数据)。
- 设计工具链:`design/render_mockup.py`(PIL 渲染效果图,依赖 Python + Pillow);`design/mockup.html` 为可交互原型,浏览器直接打开。

## 架构

三个包,职责清晰:

- **`data/`** — 数据层
  - `AppRepository`:经 `LauncherApps.getProfiles + getActivityList` 查询全部可启动应用(主用户 + 应用分身,排除自身,过滤 `icon == 0` 的无图标/无界面系统壳应用),按系统 Locale 排序,暴露 `StateFlow<List<AppEntry>?>`(null = 未加载完)。`AppEntry.user` 标记所属用户;`AppEntry.component` 主用户 = `"pkg/activity"`、分身 = `"pkg/activity#userId"`(全局唯一键,持久化/图标缓存);分身 label 加"·分身"后缀。**启动主用户走 startActivity,分身走 `LauncherApps.startMainActivity` 定位到对应用户**(普通 startActivity 只会拉起主用户实例)。`LauncherApps` 查询失败时回退主用户 PackageManager 查询。**manifest 里有 `<queries>`(LAUNCHER + HOME intent)——没有它 Android 11+ 只能查到系统应用,用户应用全部不可见**,改 manifest 时别删。另有 `REQUEST_DELETE_PACKAGES`(抽屉"删除"用,ColorOS 等 ROM 的卸载确认页要求调用方持有,否则 UninstallerActivity 直接 finish)。
  - `DefaultApps`:首启默认列表,按槽位顺序(电话/短信/相机/浏览器/图库/设置/微信/Kimi/DeepSeek)先包名候选后 label 关键词匹配,不足用非系统应用补足(`DEFAULT_COUNT = 9`)。**注意各厂商包名差异**(如相机可能是 `com.android.camera2`),新设备匹配不上时优先补包名。
  - `HomeStore`:DataStore Preferences(`mylauncher_home`)。条目序列化为 `component\tcustomName` 每行一个,槽位数 maxApps(4–100 可配置,`MAX_APPS = 100` 上限、`DEFAULT_MAX_APPS = 20` 默认);另有 favorites(收藏组件集合,每行一个,收藏在抽屉置顶)、iconSize(24–56dp)、fontSize(18–40sp)、showIcons、initialized 标志。
- **`icons/MonoIcon.kt`** — 图标单色化管线:真实图标 → Bitmap → 亮度转形状取舍、输出**不透明**纯白(`lum^2.1 * 1.2` 曲线,亮部≥0.45 纯白、0.30-0.45 过渡带抗锯齿、以下透明;**与 `design/render_mockup.py` 的半透明版已分叉,需同步 mockup**)。内存 LruCache + 磁盘缓存(cacheDir/mono_icons,key = MD5(component#versionCode@sizePx))。`rememberMonoIcon()` 先同步命中缓存,未命中回 IO 线程转换。
- **`ui/`** — 全部自绘,不依赖 Material(BasicText/BasicTextField/Canvas)
  - `HomeScreen`:总装配 + 唯一状态持有者。首启未初始化时用 `DefaultApps.pick` 生成默认列表;监听 PACKAGE_ADDED/REMOVED/CHANGED 广播刷新应用列表;已卸载条目静默剔除并回写。浮层状态(renameIndex/picker/showSettings/drawerOpen)全部在此管理。**壁纸层挂两个手势**:detectTapGestures 长按空白开设置(守卫:其他浮层未开),detectVerticalDragGestures 空白上滑开抽屉(拖拽被列表消费时不触发)。守卫顺序靠 Compose Main pass 子先父后的分发顺序,改手势逻辑时保持。
  - `AppList` + `AppRow`:手写手势系统 `awaitPressOutcome`(点击/长按/左右滑/取消,基于 `AwaitPointerEventScope.withTimeoutOrNull`,注意其抛的是 Compose 的 PointerEventTimeoutCancellationException)。**超时判长按前检查累计位移:>0.4×slop 按滚动意图取消**——否则慢速滚动(sub-slop 位移持续整个长按窗口)会误触长按动作(抽屉行直接启动应用)。**行手势触发区 = 整行宽(桌面/抽屉一致,不再限 icon+名称)**;桌面行:点击→启动,长按→替换选择器(移动→拖动排序),左滑→滑出 改名/移除(内容左移、按钮右侧、行背景淡入)。**三个列表的共用件都在这文件:AppRow(行内容)/ RowAction / RevealButton / RevealButtonBar(操作按钮条)/ awaitPressOutcome(手势判定)**;抽屉/选择器共用 AppListOverlay(见 AppDrawer.kt)。`LIST_H_MARGIN = 80.dp` 是所有列表界面的统一左右边距;行内容起点用 `PORTRAIT_ROW_MARGIN`(2/3,设置页同步)。
  - `AppDrawer` + `AppListOverlay`(同文件):抽屉与选择器共用的全屏列表层(标题 + 左上返回按钮 + 系统应用开关 + 搜索框 + 可滚动 LazyColumn)。**关闭方式 = 返回按钮/返回键/边缘返回/空白下滑;空白点按不关闭**(误触频繁,用户反馈取消——修过的坑)。搜索框整框可点聚焦(内衬区会穿透到手势层)、竖屏用行内容同款窄边距。抽屉行左滑露出右侧三个文字按钮(放入桌面/删除/信息,内容不位移只淡入——抽屉行内容靠左,位移会被裁出屏幕);**行首有收藏星标(Canvas 自绘),点按切换收藏、收藏置顶**;**行内手势用自定义 awaitPressOutcome,行内标准 detectTapGestures 在 LazyColumn 里收不到长按(已实测)**。`RowAction` 是抽屉操作的数据结构。
  - `Controls.kt`:共享自绘控件 `MiniSlider` / `MiniSwitch`(白色系,壁纸深底风格)。
  - `Wallpaper`:生成式 Canvas 壁纸(62° 硬边斜带 + -24° 细线 + 右上圆环)。**色板 `BANDS` 与 `mockup.html` / `render_mockup.py` 完全一致,改色必须三处同步**。
  - `ClockWidget` / `RenameDialog` / `SettingsScreen`:各自独立的浮层组件;Popup 一律手写 `PopupPositionProvider` 定位。`SubPage`(SubPage.kt)是**三个子页面(抽屉/选择器/设置)共用的页面模板**:固定头部 = 返回按钮贴左缘 16dp + 标题居中 26sp + 副标题居中,距顶 = 状态栏 + 24dp,内容走 content 槽 —— 三页基础样式由此统一,改头部只改这一处。`SettingsScreen` 是全屏设置页(长按空白打开),**Zune 扁平风格:白字直接铺在壁纸上**,含"设为默认桌面"(打开系统 `Settings.ACTION_HOME_SETTINGS` 页面——RoleManager 角色对话框在部分国产 ROM 上读不到 extra 会闪退,ACTION_CHOOSER 不持久化,只有系统设置页能真正改默认;状态在 ON_RESUME 时重新检测,见 LifecycleEventObserver)、`currentDefaultHome` 检测(必须用 resolveActivity,queryIntentActivities 返回全部候选无法判断默认)、通知角标开关 + 通知使用权入口。
  - 功德木鱼(`HomeScreen` 边缘按下即 `knockNow` **放音+功德+1+冒泡即时触发**(纯点击边缘也冒泡);无边缘敲击的返回手势在返回回调补 功德+1/冒泡/声音;`grantMerit` 是敲击/补敲/自动积累共用的 +1 冒泡入口;**功德文字可自定义**(HomeStore `merit_label`,读取侧空白回退"功德",冒泡显示"文字+N";设置页输入框用本地编辑态,否则删空瞬间被默认值回填——修过的坑);**自动积累功德**(`auto_merit_enabled` + 连续时长上限 10..600s,`LaunchedEffect` 每秒 grantMerit 一次,时长用尽自动关闭开关);功德按日持久化在 HomeStore `merit_history`,保留 365 天,总功德/每日统计直接汇总此表):**边缘检测挂在根容器上**(背景层会被列表行/滚动区遮挡,边缘敲击时有时无——修过的坑);**去重按手势不按全局时长**——边缘敲击记 `edgeKnockPending` 标记,返回完成回调 1.5s 窗口内只消费不补敲(同一手势两条路径),已消费/超窗的旧标记不影响后续手势,快速连滑每个手势都响;`KnockSound.play` 不 stop 上一击(59ms 短音自然衰减即止,stop+play 相邻有 SoundPool 流 ID 复用竞态会偶发吃掉新一击——修过的坑),仅 80ms 物理防重入兜底双调/双上报。冒泡各自独立 Animatable,天然并发。
  - `badges/BadgeService.kt`:`NotificationListenerService` 统计各包通知数 → `BadgeStore.counts`(StateFlow)。manifest 里注册了 service + POST_NOTIFICATIONS;需用户在系统"通知使用权"中授权(设置页有入口 + `isBadgeListenerEnabled` 检测)。

## 关键约定

- **视觉即契约**:所有视觉常量(壁纸色板、图标单色化曲线、时钟字号比例 min(26vw,120px) 等)在 Kotlin 与 design/ 工具链之间是重复的,改任何一处都要同步另一处。design/ 是视觉的"源",实机截图验证见 `docs/screenshots/`。
- **手势闭包防过期**:`pointerInput` 手势闭包不随数据变化重启,内部取回调必须经 `rememberUpdatedState`(见 AppList.kt 的 currentItems 等)。
- **操作按钮的命中规则**(桌面行与抽屉行同一套,修过两次坑):
  - 操作按钮 Row 必须画在内容层之上(后绘 = 命中优先)——`fillMaxSize` 的内容层即使透明也会挡掉按钮的点击;桌面竖屏内容只左移 ~12dp 边缘余量,按钮区大部分仍被内容层盖住,不置顶就永远点不中。
  - `RevealButton` 未启用时**完全不挂 clickable**(`enabled=false` 的 clickable 仍会消费按下,吞掉行右侧起始的左滑手势)。
  - 行手势 `awaitFirstDown(requireUnconsumed=false)` 后,若 down 已被子按钮消费,静候抬手即结束——不处理也不消费,否则会抢按钮的点击。
  - 抽屉/桌面行展开后 3s 无操作自动收起(`LaunchedEffect(revealed)` + delay)。
- 浮层(菜单/设置)是 Popup,主屏拖拽/滚动是自实现,改动手势逻辑前先读 `AppList.kt` 的 `awaitPressOutcome` 注释。

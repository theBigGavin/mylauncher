# 待办任务

## 功德气泡锚点:时间文字顶部

- 状态:已完成(2026-08-14)
- 实现:`ClockWidget.kt` 中锚点改为时间 BasicText 自身的 `onGloballyPositioned` 顶部中心(`timeAnchorX/Y`),横屏 Start 对齐也正确;日期 Box 包装已移除。

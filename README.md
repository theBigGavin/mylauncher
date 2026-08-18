<div align="center">

# 📱 MyLauncher

**A Zune/Metro-style minimal Android launcher**

White-on-color sharp wallpaper · oversized thin clock · monochrome icons with a bold vertical app list — adaptive in both portrait and landscape.

[简体中文](README_CN.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2.0.21-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![GitHub Stars](https://img.shields.io/github/stars/theBigGavin/mylauncher?style=social&label=Stars)](https://github.com/theBigGavin/mylauncher)

🏠 **Made by Gavin's Lab** — a one-person company run by 7 AI agents on a kanban board: [company site](https://www.hermes.cc.cd) · [live transparency office](https://www.hermes.cc.cd/opc/)

</div>

![MyLauncher](img/mylauncher-landscape.jpg)

## ✨ Features

- **🏠 System launcher (HOME)** — Registered as a system launcher (`fullUser` orientation) with fully adaptive portrait/landscape layouts. Portrait: a centered thin large clock with a vertical app list. Landscape: clock bottom-left, app icons in one vertical line, names right-aligned.
- **📱 Up to 20 apps on the home screen** — with a bottom counter and scrolling; first launch preloads common apps (camera / browser / gallery / settings / WeChat / Kimi / DeepSeek…, matched by package name + label).
- **🧹 Smart filtering** — icon-less, UI-less system shell apps are auto-filtered; the app drawer and picker can toggle "show system apps".
- **⚪ Monochrome icons** — real app icons converted to pure-white monochrome (brightness → alpha), with in-memory + on-disk dual caching.
- **📋 Long-press menu** — replace app (full picker, same style as the home screen) / rename / remove.
- **🔄 Drag to reorder** — long-press then drag for custom ordering, persisted via DataStore.
- **🎛️ Live-adjustable settings** — long-press blank space to open settings: icon size and font size adjust in real time, icons can be hidden, set as default home, restore default layout.
- **📂 App drawer** — swipe up from blank space to pull out the drawer, tap to launch.
- **🖼️ Generative wallpaper** — 62° sharp two-tone diagonal bands + fine lines + hard-edge rings, drawn directly on Canvas (no blur, no bitmap).
- **🪬 Merit wooden fish easter egg** — tap the screen edge to knock (real sound, rapid-fire friendly); each edge knock or completed back gesture adds +1 merit with a rising bubble. Customizable merit text (default "功德"), optional auto-accumulate: +1 every second for a configurable continuous duration (10s–600s, default 10s), then the switch turns itself off. Stats shown in settings: fastest knock rate and all-time peak daily merit. Daily merits are persisted locally, 365-day history.
- **🔔 Notification badges** — after granting system "notification access", per-package notification counts are shown as badges (toggleable in settings).

## 📸 Screenshots

![MyLauncher portrait home screen](img/mylauncher-portrait.jpg)

## 🚀 Build & install

**Prerequisites**: JDK 17 + Android SDK (compileSdk 35, minSdk 26).

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Tech stack**: pure Kotlin 2.0 + Jetpack Compose, single `:app` module, package `com.mylauncher`. All UI is self-drawn (no Material dependency).

## 📦 Releases

APKs are published on the [Releases page](https://github.com/theBigGavin/mylauncher/releases) — the latest is **v1.5.3**. Versioning follows semantic versioning `major.minor.patch` (major = breaking change / milestone, minor = new feature, patch = bug fix); `versionCode` increments monotonically with every release.

## 🗂️ Project structure

```
app/src/main/java/com/mylauncher/   # Source code (data / icons / ui)
design/                             # Mockups & render scripts (mockup.html is an interactive prototype)
docs/                               # Notes & screenshots
```

## 🤝 Contributing

Issues and PRs are welcome — this is an open, transparent one-person company built in public.

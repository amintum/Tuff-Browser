<p align="center">
  <img src="art/logo.png" width="112" height="112" alt="Tuff Browser Logo" />
</p>

<h1 align="center">Tuff Browser</h1>

<p align="center">
  <strong>Fast, tiny, and private Android browser. Under 2 MB. Zero bloat.</strong>
</p>

<p align="center">
  <a href="#-features"><img src="https://img.shields.io/badge/APK%20Size-~1.6%20MB-brightgreen.svg" alt="APK Size" /></a>
  <a href="#-tech-specs"><img src="https://img.shields.io/badge/Android-7.0%2B-blue.svg" alt="Android Support" /></a>
  <a href="#-privacy-first"><img src="https://img.shields.io/badge/Trackers-0-success.svg" alt="Zero Trackers" /></a>
  <a href="#-standalone-download-manager"><img src="https://img.shields.io/badge/Downloader-Built--in-orange.svg" alt="Built-in Downloader" /></a>
  <a href="#-license"><img src="https://img.shields.io/badge/License-Custom%20Attribution-red.svg" alt="License" /></a>
</p>

---

## Why Tuff?

Most mobile browsers are 150 MB+ giants packed with news feeds and background trackers. **Tuff Browser** strips all of that away:

- **~1.6 MB APK**: Installs instantly, uses minimal RAM, and runs smoothly on any device.
- **Own Downloader**: Doesn't rely on Android's buggy system download manager. Real-time speeds, pause/resume, and notification controls.
- **Custom ROM & GSI Ready**: The ideal low-storage choice for custom ROMs and GSIs, saving hundreds of megabytes on system partitions while remaining modern and completely reliable.
- **Zero Telemetry**: No analytics, no crash reporters phoning home, no account sign-in.
- **1-Hand UI**: Bottom search bar option and a fitted quick toolbar designed for tall screens.

---

## Key Features

### 🧭 Clean & Ergonomic Interface
- **Omnibar Position**: Switch between **Bottom** (easy thumb reach) and **Top** with one tap.
- **Fitted 9-Button Quick Bar**: All essentials fit in a single row without horizontal scrolling:
  - Back / Forward
  - Tab Management
  - Zoom Out / In with floating percentage indicator (e.g. `80% zoom`)
  - Desktop Site toggle with live active highlight
  - Download current page
  - History page
  - **Fire button** (Nuclear wipe)
  - Refresh
  - In-page search ("Find in page") with match counters
- **Nuclear Data Wipe (🔥)**: One tap closes tabs and wipes cookies, web cache, DOM storage, and history instantly.

### 📥 Built-in Downloader
Unlike browsers that pass links to Android's often-restricted system downloader:
- **Live Speed**: Shows real-time transfer speed (KB/s, MB/s) and progress percentage.
- **Pause & Resume**: Supports HTTP Range headers so you never lose partial downloads.
- **Notification Actions**: Pause, resume, or cancel straight from your notification tray.

### 📱 Custom ROM & GSI Ready
- **Low-Storage Choice**: Takes up under 2 MB—saving massive amounts of space on system partitions compared to heavy stock browsers.
- **Modern with Zero Issues**: Full modern web compatibility and hardware-accelerated rendering without bloat, background services, or crashes.
- **Completely Self-Contained**: Needs no proprietary services or dependencies; runs seamlessly on fully de-googled and clean systems.
- **Reliable Downloading**: Never breaks even on minimal systems where default system download components are missing or stripped.

### 🛡️ Privacy by Default
- Choose between **Brave Search**, **DuckDuckGo**, or your own **Custom Search URL**.
- Transparent privacy advisories: Google is marked as *Unsafe · Not recommended* with a rectangular `[Why?]` badge explaining ad tracking and profiling.

---

## Tech Specs

| Detail | Specification |
| :--- | :--- |
| **APK Size** | ~1.65 MB (Release, R8 minified) |
| **Compatibility** | Android 7.0+ (API 24+) |
| **Engine** | Hardware-accelerated System WebView (Chromium) |
| **Telemetry / Ads** | None (0%) |

---

## Building from Source

```bash
git clone https://github.com/amintum/Tuff-Browser.git
cd Tuff-Browser
gradle assembleRelease
```

The APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 📄 License

This project is licensed under the **Tuff Browser Public License**:

- **Open Source Projects & Forks**: Free to use, modify, and redistribute, provided that **clear, visible credit to Amintum** and a link back to [this repository](https://github.com/amintum/Tuff-Browser) is preserved.
- **Closed Source / Commercial Projects**: You **must open an issue** on this repository and request explicit permission before using, bundling, or deriving from this code.
- **Derived Works**: Must retain the license and author attribution in about pages/documentation.

See the full [LICENSE](LICENSE) for details.

---

## 👤 Author

Developed by **[Amintum](https://github.com/amintum)**  
Repository: **[Tuff-Browser](https://github.com/amintum/Tuff-Browser)**

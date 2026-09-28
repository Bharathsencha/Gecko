# Gecko AdFree Browser

A lightweight, privacy-focused Android web browser built on Mozilla GeckoView, featuring embedded content blocking, dark web rendering, free in-browser proxy relays, hardware-encrypted password management, and clean search filters.

Author: Gibbly Gobbly <gibblygobbly@proton.me>  
License: GNU General Public License v3.0 (GPLv3)

---

## Overview

Gecko AdFree Browser is engineered from the ground up to deliver a clean, fast, and privacy-respecting browsing experience on Android. By embedding native content filters, dynamic dark themes, and proxy routing directly into GeckoView runtime sessions, the browser prevents cross-site tracking, removes intrusive advertisements, and safeguards network traffic without requiring external extensions or third-party accounts.

---

## Core Features

- Mozilla GeckoView 122 Engine: High-performance rendering engine with isolated process architecture and WebExtension support.
- Native Content Shield: Network-level request cancellation for ads, trackers, telemetry beacons, and cryptominers.
- Cosmetic Element Eradication: Dynamic DOM observation and CSS rule injection that eliminates blank ad slots, popups, and GDPR/OneTrust consent overlays.
- Official Dark Reader Dynamic Engine: Integrated standalone Dark Reader library for high-contrast OLED pitch-black theme generation without media distortion.
- Clean Web & AI Overview Blocker: Automatic search query sanitization (`&udm=14`) and DOM eradication of generative AI clutter on search results.
- Free In-Browser VPN Relays: 10 country proxy relays (US, Germany, Japan, UK, Netherlands, Singapore, Canada, France, Australia, India) with automatic disconnection on browser exit.
- HTTPS-Only Mode: Automatic HTTPS default routing and security warning dialogs when navigating to unencrypted HTTP sites.
- 3-Tier Cookie Shield: Configurable cookie isolation policies (Block 3rd-Party Trackers, Block All 3rd-Party Cookies, Block All Cookies).
- Hardware-Backed Password Vault: AES-256-GCM encryption backed by Android Keystore for secure local credential storage and auto-fill.
- Clean Reader View: Distraction-free article reader powered by Mozilla Readability.

---

## Embedded Open-Source Components & Upstream Repositories

This project integrates and builds upon the following open-source projects and libraries:

| Component | Upstream Project & Repository | License |
| :--- | :--- | :--- |
| Mozilla GeckoView | [https://github.com/mozilla/geckoview](https://github.com/mozilla/geckoview) <br> [https://hg.mozilla.org/mozilla-central/](https://hg.mozilla.org/mozilla-central/) | Mozilla Public License 2.0 |
| Dark Reader | [https://github.com/darkreader/darkreader](https://github.com/darkreader/darkreader) | MIT License |
| Mozilla Readability | [https://github.com/mozilla/readability](https://github.com/mozilla/readability) | Apache License 2.0 |
| uBlock Origin Filter Syntax | [https://github.com/gorhill/uBlock](https://github.com/gorhill/uBlock) | GNU General Public License v3.0 |
| AdGuard Filter Lists | [https://github.com/AdguardTeam/AdguardFilters](https://github.com/AdguardTeam/AdguardFilters) | GNU General Public License v3.0 |
| The Firebog Blocklists | [https://github.com/WaLLy3K/wally3k.github.io](https://github.com/WaLLy3K/wally3k.github.io) | MIT License |

---

## Building from Source

### Prerequisites

- Android SDK (API Level 34)
- JDK 17 or higher
- Gradle 8.4+

### Build Steps

1. Clone the repository:
   ```bash
   git clone git@github.com:Bharathsencha/Gecko.git
   cd Gecko
   ```

2. Assemble the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

3. Install on a connected Android device:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

---

## License

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See the [LICENSE](LICENSE) file for full details.

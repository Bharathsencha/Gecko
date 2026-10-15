# Gecko

<img src="logo.png" alt="Gecko logo" width="120" />

A privacy-first Android browser built on Mozilla GeckoView: ad and tracker blocking, uniform anti-fingerprinting, a built-in Tor and free-proxy VPN, private tabs that leave nothing behind, and a video and audio downloader.

Author: Gibbly Gobbly <gibblygobbly@proton.me>
License: GNU General Public License v3.0 or later

---

## Features

- **Engine:** Mozilla GeckoView 157 with strict Enhanced Tracking Protection, Global Privacy Control and cookie-banner rejection.
- **Ad blocking:** uBlock Origin 1.75 bundled, plus a native request filter.
- **Anti-fingerprinting:** Gecko's fingerprinting protection with every resistFingerprinting target enabled (uniform user agent, screen, timezone, fonts, canvas and WebGL), except that sites still see your light or dark theme. WebRTC never exposes local or real addresses.
- **VPN:**
  - *Tor*: an embedded Tor client, started only while connected, with a choice of exit country.
  - *Free relay*: public SOCKS5 relays fetched fresh at connect time; each one is proven by a real request through it before use.
  - Both fail closed, and downloads use the same route.
- **Private tabs:** in-memory only, no history or session saved, screenshots and recent-apps previews blocked, no page titles on the lock screen.
- **Incognito keyboard:** every text field, in the app and on web pages, asks the keyboard not to learn or store what you type.
- **Downloads:** an optional download button (off by default) saves video and audio from the page you are on: direct, HLS and split streams, remuxed on device. Audio is saved as a real `.m4a` or `.opus` file.
- **Password vault:** AES-256-GCM with the key held in the Android Keystore. Opening it needs your fingerprint, the phone's screen lock or a 4-digit app PIN (checked against a hardware-held key, with lockouts).
- **Clear button:** wipes history, cookies, cache and tabs behind one of five animations (Fire, Flood, Sweep, Shred, Vortex), chosen in Settings.
- **Start page:** block compositions, patterns, solid colours, gradients or your own photo, plus a built-in designer for making and naming your own block layouts.
- **Look:** neo-brutalist light and dark themes with the Space Grotesk typeface. Pages render at the screen's full resolution and refresh rate.
- **Reader view, night mode, per-site settings, HTTPS-only mode.**

## Install

Download an APK from the Releases page, or build one:

```bash
git clone git@github.com:Bharathsencha/Gecko.git
cd Gecko
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Requirements: Android 8.0 (API 26) or newer, on arm64 or x86_64. To build you need JDK 17 or newer and the Android SDK with platform 37.1. Release builds are signed with the key named in a git-ignored `keystore.properties`; without it they fall back to the debug key.

On first launch Gecko offers to become the default browser. You can also do this later in Settings, under Default browser.

## Privacy and terms

Gecko has no accounts, analytics, ads or servers of its own, and everything it keeps stays on your phone. The [privacy policy](PRIVACY.md) lists exactly what is stored and every network request the app makes on its own. Use of the app is covered by the [terms of use](TERMS.md).

## Credits

Gecko is built on these open-source projects. Thank you to their authors.

| Component | Used for | License |
| :--- | :--- | :--- |
| [Mozilla GeckoView](https://mozilla.github.io/geckoview/) | Web engine | MPL 2.0 |
| [uBlock Origin](https://github.com/gorhill/uBlock) | Ad and tracker blocking. Bundled with one change: a site exemption added to `js/start.js` (marked in the file) | GPL 3.0 |
| [Tor](https://www.torproject.org/) | Anonymous network for the VPN | BSD 3-Clause |
| [tor-android](https://github.com/guardianproject/tor-android) | Tor packaging for Android | BSD 3-Clause |
| [jtorctl](https://github.com/guardianproject/jtorctl) | Tor control protocol | BSD 3-Clause |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Video and audio extraction | Unlicense |
| [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) | yt-dlp runtime for Android | GPL 3.0 |
| [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | Stream extraction | GPL 3.0 |
| [OkHttp](https://square.github.io/okhttp/) | Native HTTP client | Apache 2.0 |
| [Coil](https://coil-kt.github.io/coil/) | Image loading | Apache 2.0 |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | Engine configuration parsing | Apache 2.0 |
| [Jetpack Compose and AndroidX](https://developer.android.com/jetpack) | User interface | Apache 2.0 |
| [Material Icons](https://fonts.google.com/icons) | Icons | Apache 2.0 |
| [Space Grotesk](https://github.com/floriankarsten/space-grotesk) | Typeface | SIL Open Font License 1.1 (`app/src/main/assets/licenses/SpaceGrotesk-OFL.txt`) |

### Ideas from other projects

These parts of Gecko follow ideas or approaches from other projects. No code was copied from them; the implementations are Gecko's own.

| Project | What Gecko took from it |
| :--- | :--- |
| [DuckDuckGo Fire Button](https://duckduckgo.com/duckduckgo-help-pages/privacy/fire-button/) | The one-tap clear button with a fire animation |
| [Firefox private browsing](https://support.mozilla.org/kb/private-browsing-firefox-android) | The mask icon for private mode |
| [Brave Shields](https://brave.com/shields/) | The per-site protection panel |
| [Cat-Catch](https://github.com/xifangczy/cat-catch) | The approach for detecting media on pages |
| [Mozilla Readability](https://github.com/mozilla/readability) | The approach for reader view |
| [Tor Browser](https://support.torproject.org/glossary/fingerprinting/) | Uniform anti-fingerprinting settings |
| [EasyList](https://easylist.to/), [AdGuard filters](https://github.com/AdguardTeam/AdguardFilters), [Firebog](https://firebog.net/), [d3ward's ad block test](https://github.com/d3ward/toolz) | Sources for the built-in blocklist |

Earlier development versions of Gecko (before October 2026) bundled the fire animation file from the [DuckDuckGo Android app](https://github.com/duckduckgo/Android) (Apache License 2.0). It is no longer included.

The same lists are shown in the app under Settings, then About.

Gecko is not affiliated with Mozilla, the Tor Project, Brave, DuckDuckGo or any project listed above. Firefox, Tor, Brave and DuckDuckGo are trademarks of their respective owners.

## License

Copyright (C) 2024-2026 Gibbly Gobbly

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for details.

Bundled components keep their own licenses, listed above. The license text for uBlock Origin is in `app/src/main/assets/extensions/ublock_origin/LICENSE.txt`.

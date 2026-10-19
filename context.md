# System Context: Gecko AdFree Browser

## 1. System Overview and Goals
Gecko AdFree Browser is a privacy-hardened Android web browser built on Mozilla GeckoView 122+ and Jetpack Compose. The core mission is to provide desktop-class web browsing on mobile devices without telemetry, advertisement surveillance, intrusive artificial intelligence summaries, or commercial tracker scripts.

### Core Architecture Principles
- Zero Native Telemetry: No third-party analytics SDKs (no Firebase, no Mixpanel, no Google Play Services dependencies).
- Isolated Web Engine: GeckoView runs as an independent multi-process web rendering engine rather than relying on Android system WebView.
- Hardware-Enforced Security: User credentials and autofill data are encrypted using AES-256-GCM via the Android Keystore provider (`AndroidKeyStore`).
- Modular Runtime Shields: Network request interception, cosmetic DOM removal, and Dark Reader dynamic script injection are coordinated through native GeckoView delegates and WebExtension ports.
- High-Utility Mobile Interface: An ergonomic, single-handed Android Compose UI focused on density, fast response times, and edge-to-edge system insets.

---

## 2. Technology Stack and Versions
- Programming Language: Kotlin 1.9 / JVM 17
- Android SDK: Target SDK 34, Compile SDK 34, Minimum SDK 26 (Android 8.0 Oreo)
- Rendering Engine: `org.mozilla.geckoview:geckoview-omni:122.0.20240205133611`
- UI Toolkit: Android Jetpack Compose with Compose BOM `2023.10.01` and Material 3
- Concurrency: Kotlin Coroutines (`kotlinx.coroutines`) and Reactive Streams (`StateFlow`, `SharedFlow`)
- Media Extraction: `youtubedl-android:0.18.1` (yt-dlp native binary wrapper) and `ffmpeg:0.18.1`
- Media Parsing Fallback: `com.github.teamnewpipe:NewPipeExtractor:v0.24.4`
- Security and Crypto: Android Keystore Provider with `AES/GCM/NoPadding`
- Storage: Encrypted and scoped SharedPreferences, JSON serializations, Room/File cache buffers

---

## 3. Directory and Package Layout
```
app/src/main/java/com/browser/adfree/
|-- MainActivity.kt                     # Engine bootstrapping, lifecycle, delegate wiring
|-- download/                           # Media detection, stream extraction, and muxing
|   |-- BrowserDownloadManager.kt       # Download queue coordinator and notification hook
|   |-- DownloadForegroundService.kt    # Android Foreground service for background downloads
|   |-- UniversalVideoExtractor.kt      # Orchestrator across yt-dlp and NewPipeExtractor
|   |-- VideoAudioMuxer.kt              # FFmpeg video/audio track combiner
|   |-- VideoQualityParser.kt           # Stream format selector and resolution resolver
|   +-- YouTubeStreamExtractor.kt       # Direct NewPipe stream extractor fallback
|-- media/
|   +-- MediaPlaybackService.kt         # Background audio playback and media session notifications
|-- model/                              # Core domain data classes and entities
|   |-- BrowserPreferences.kt           # User configurations (Shields, Search, Themes)
|   |-- DownloadItem.kt                 # Download task models and progress states
|   |-- FavoriteItem.kt                 # Speed dial bookmarks and custom icons
|   |-- HistoryItem.kt                  # History timeline records
|   |-- SavedLogin.kt                   # Credential vault item
|   |-- TabModel.kt                     # Tab descriptor and session metadata
|   +-- UrlSanitizer.kt                 # Search URL filter (udm=14, tracker removal)
|-- shields/
|   +-- ShieldsRepository.kt            # Filter lists, cosmetic CSS, Dark Reader script manager
|-- storage/
|   |-- BrowserPreferencesRepository.kt # Reactive preferences store via StateFlow
|   |-- FavoritesRepository.kt          # Bookmark store with CRUD operations
|   |-- HistoryRepository.kt            # History log with full-text search and clearing
|   |-- SecurePasswordRepository.kt     # Hardware Keystore AES-256-GCM credential vault
|   +-- TabSessionRepository.kt         # Tab restoration on startup and backgrounding
|-- tabs/
|   +-- TabManager.kt                   # Tab collection, GeckoSession creation, active tab state
|-- ui/
|   |-- BrowserMainScreen.kt            # Top-level Compose scaffold and screen routing
|   |-- components/                     # Reusable Compose widgets and bottom sheets
|   |   |-- BottomBar.kt                # Ergonomic bottom navigation toolbar
|   |   |-- BrowserSettingsSheet.kt     # Granular browser configuration sheet
|   |   |-- GeckoViewContainer.kt       # AndroidView wrapper binding GeckoView to GeckoSession
|   |   |-- SafariHomePage.kt           # Speed dial, favorites, and clean start page
|   |   |-- SafariReaderView.kt         # Distraction-free article reader mode
|   |   |-- TabSwitcherSheet.kt         # Vertical/grid tab cards with close and new tab actions
|   |   |-- TopAddressBar.kt            # Search input, HTTPS indicator, shield quick toggle
|   |   +-- VideoDownloadSheet.kt       # Quality resolution picker for detected media streams
|   |-- theme/
|   |   |-- Color.kt                    # Monochromatic and OLED dark surface color palette
|   |   +-- Theme.kt                    # Compose Material 3 theme wrapper
|   +-- vpn/
|       |-- FreeVpnManager.kt           # SOCKS5/HTTP proxy relay coordinator
|       +-- VpnPingManager.kt           # Relay latency measurement engine
```

---

## 4. Key Subsystem Workflows

### 4.1 GeckoView Runtime and Session Management
- `GeckoRuntime`: Instantiated as a singleton in `MainActivity.kt`. Configured with web extensions, content blocking preferences, and private browsing profiles.
- `GeckoSession`: Every tab (`TabModel`) owns or lazily instantiates a `GeckoSession`.
- Session Delegates:
  - `ProgressDelegate`: Emits page loading percentage and security changes to Compose UI.
  - `NavigationDelegate`: Handles URL changes, back/forward history capabilities, and external intent redirects.
  - `ContentDelegate`: Captures page title, favicon updates, and full-screen requests.
  - `PromptDelegate`: Intercepts JavaScript alert/confirm/prompt dialogs and renders native Compose dialogs.
  - `MediaDelegate`: Signals media playback state for picture-in-picture and background audio.
- Compose Integration: Managed via `AndroidView` inside `GeckoViewContainer.kt`. It connects the active `GeckoSession` to the Android surface when the active tab switches.

### 4.2 Content Shields and Script Injection
- Network Filter: GeckoView native content blocking rules loaded from Firebog and AdGuard blocklists.
- Cosmetic Filter: Dynamic CSS stylesheets injected via content scripts that collapse blank ad placeholders and GDPR consent banners.
- Clean Search and AI Blocker: `UrlSanitizer.kt` intercepts search engine URLs (Google, Bing, DuckDuckGo) and automatically appends clean web parameters (`&udm=14` for Google Web search) while stripping tracking parameters (`utm_*`, `fbclid`, `gclid`).
- Dark Reader Integration: Injects standalone `darkreader.js` into web pages on demand, calculating dynamic color inversion without inverting photos or video frames.

### 4.3 Media Extraction and Download Pipeline
- Interception: When a webpage containing media is loaded, `UniversalVideoExtractor` inspects the page URL and DOM video elements.
- Resolution Parsing: Leverages yt-dlp (via `youtubedl-android`) to extract separated audio/video streams, formats, and thumbnails.
- Downloading: Dispatched to `DownloadForegroundService` using Kotlin Coroutines, providing progress notifications with pause, resume, and cancel capabilities.
- Post-Processing: Uses `VideoAudioMuxer` to invoke native FFmpeg commands, combining adaptive video-only and audio-only streams into standardized MP4 containers.

### 4.4 Hardware-Backed Credential Vault
- Cryptographic Architecture:
  - Master Key: Generated in the Android Keystore using `KeyGenParameterSpec` with AES-256 in GCM mode without padding (`AES/GCM/NoPadding`).
  - Storage: Ciphertext and random 12-byte initialization vectors (IV) are stored securely on local flash storage.
  - Autofill Pipeline: The browser observes DOM form submissions via GeckoView content delegates, detects username/password inputs, prompts the user via a native Compose dialog, and offers autofill on subsequent visits.

### 4.5 VPN and Relay Network Architecture
- Relays: 10 geographical egress endpoints (US, Germany, Japan, UK, Netherlands, Singapore, Canada, France, Australia, India).
- Mechanism: Configures runtime proxy settings directly on GeckoView network sessions (`GeckoRuntimeSettings.Builder.proxyHost` / `proxyPort`).
- Latency Engine: `VpnPingManager` evaluates TCP handshakes and round-trip times to each relay, reporting real-time millisecond latency to the country selection sheet.

---

## 5. Engineering Invariants and Guardrails
- Memory Conservation: Background tabs not viewed within 15 minutes must hibernate their associated surface buffers while preserving navigation history state.
- Zero Memory Leaks in Compose: Always unbind delegates when tabs are destroyed. Never store Activity references inside ViewModels, Repositories, or Singletons.
- Responsive Inset Management: Every screen layout must respect `imePadding()` to ensure bottom bars, search bars, and dialogs move above the on-screen keyboard.

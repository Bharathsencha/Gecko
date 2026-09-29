// Tracking Protection & Privacy
user_pref("privacy.trackingprotection.fingerprinting.enabled", true);
user_pref("privacy.trackingprotection.enabled", true);
user_pref("toolkit.telemetry.enabled", false);
user_pref("datareporting.healthreport.uploadEnabled", false);

// Hardware Video Acceleration & Smooth Playback on Android (60fps/120fps)
user_pref("media.hardware-video-decoding.enabled", true);
user_pref("media.hardware-video-decoding.failed", false);
user_pref("media.android-media-codec.enabled", true);
user_pref("media.android-media-codec.preferred", true);
user_pref("media.webm.enabled", true);
user_pref("media.mp4.enabled", true);
user_pref("media.mediasource.enabled", true);
user_pref("media.mediasource.mp4.enabled", true);
user_pref("media.mediasource.webm.enabled", true);
user_pref("media.mediasource.vp9.enabled", true);
user_pref("media.benchmark.vp9.fps", 120);
user_pref("media.benchmark.frames", 1000);
user_pref("media.cache_size", 65536);
user_pref("media.memory_cache_max_size", 65536);
user_pref("media.autoplay.default", 0);
user_pref("media.autoplay.blocking_policy", 0);
user_pref("layout.frame_rate", 0);
user_pref("gfx.webrender.all", true);
user_pref("layers.acceleration.force-enabled", true);
user_pref("layers.offmainthreadcomposition.enabled", true);
user_pref("layers.offmainthreadcomposition.async-animations", true);

// Back-Forward Cache (bfcache) & Scroll Restoration
user_pref("browser.sessionhistory.max_total_viewers", 8);
user_pref("dom.history.preserve_scroll_position", true);
user_pref("docshell.scroll_restoration.enabled", true);
user_pref("fission.bfcacheInParent", true);
user_pref("browser.sessionhistory.bfcache.cache_control_no_store", true);

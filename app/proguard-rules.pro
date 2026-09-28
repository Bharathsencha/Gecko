# ProGuard rules for GeckoView, Compose, and Kotlin

# ── GeckoView ──────────────────────────────────────────────────────────────
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keepclassmembers class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.**
-dontwarn mozilla.**
-dontwarn java.beans.**
-dontwarn javax.**
-dontwarn sun.misc.**
-dontwarn org.yaml.snakeyaml.**

# ── Kotlin ─────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Jetpack Compose ────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Coil (image loading) ───────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── OkHttp ────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── App model classes (serialized to/from JSON) ────────────────────────────
-keep class com.browser.adfree.model.** { *; }
-keep class com.browser.adfree.storage.** { *; }

# ── Annotations ───────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ── Prevent stripping of used reflection targets ───────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Remove debug logging in release ──────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

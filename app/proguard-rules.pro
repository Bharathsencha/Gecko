# ProGuard rules for GeckoView, Compose, and Kotlin
 
# GeckoView (JNI and Reflection)
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keepclassmembers class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.**
-dontwarn mozilla.**
-dontwarn java.beans.**
-dontwarn javax.**
-dontwarn sun.misc.**
-dontwarn org.yaml.snakeyaml.**

# App model & storage classes (JSON serialization / reflection)
-keep class com.browser.adfree.model.** { *; }
-keep class com.browser.adfree.storage.** { *; }

# Annotations & Signature Attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Prevent stripping of used reflection targets
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# youtubedl-android (yt-dlp), FFmpeg & Apache Commons Compress/Jackson rules
-keep class com.yausername.youtubedl_android.** { *; }
-keep class io.github.junkfood02.youtubedl.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.yausername.youtubedl_android.**
-dontwarn io.github.junkfood02.youtubedl.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-dontwarn com.fasterxml.jackson.**

# NewPipeExtractor & Rhino JavaScript Engine
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.** { *; }

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Embedded Tor: TorService is started by class name and talks to the native
# daemon through JNI and the jtorctl control library.
-keep class org.torproject.jni.** { *; }
-keep class net.freehaven.tor.control.** { *; }

# GeckoView reads assets/gecko/geckoview-config.yaml (the anti-fingerprinting
# prefs) through SnakeYAML, which fills DebugConfig's fields by reflection.
-keep class org.mozilla.gecko.util.DebugConfig { *; }
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

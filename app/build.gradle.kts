import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.browser.adfree"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.browser.adfree"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    // Release key lives outside the repo; keystore.properties (git-ignored) points at it.
    // Without it, release builds fall back to the debug key for local testing.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            // Gecko's crash reporter (crash reporting is off in this app)
            excludes += setOf("**/libcrashtools.so", "**/libcrashhelper.so")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Mozilla GeckoView Engine
    implementation("org.mozilla.geckoview:geckoview:157.0.20261005135250")

    // youtubedl-android (yt-dlp wrapper) for video/audio extraction across 1800+ sites
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")

    // Embedded Tor for the in-browser VPN. Started on the first connect.
    implementation("info.guardianproject:tor-android:0.4.9.14")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Fingerprint / screen-lock prompt for the password vault
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")

    // NewPipeExtractor for YouTube stream extraction fallback
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.4")

    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")

    // SwipeRefreshLayout for smooth native pull-to-refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")

    // Coil for Dynamic Favicons & Animated Wallpapers (.gif, .png, .jpg, .webp)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")


    // Networking (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// The privacy policy and terms ship inside the app, copied from the repository root
// so there is one source of truth.
val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.file("PRIVACY.md"), rootProject.file("TERMS.md"))
    into(layout.projectDirectory.dir("src/main/assets/legal"))
}
tasks.named("preBuild") { dependsOn(copyLegalDocs) }

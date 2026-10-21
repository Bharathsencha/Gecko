import java.util.Properties
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as CompressZipFile

buildscript {
    dependencies { classpath("org.apache.commons:commons-compress:1.27.1") }
}

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
        // Release builds from CI pass these from the git tag (v1.2.3 -> 1.2.3, 10203).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
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

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // android.* stubs return defaults instead of throwing in JVM tests.
        unitTests.isReturnDefaultValues = true
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
    // Argon2id for the vault passcode
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
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

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in JVM tests.
    testImplementation("org.json:json:20180813")
}

// The privacy policy and terms ship inside the app, copied from the repository root
// so there is one source of truth.
val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.file("PRIVACY.md"), rootProject.file("TERMS.md"))
    into(layout.projectDirectory.dir("src/main/assets/legal"))
}
tasks.named("preBuild") { dependsOn(copyLegalDocs) }

// yt-dlp's bundled Python ships things nothing here runs: a 6 MB static
// library, CPython's test modules, curses, sqlite and the like. Drop them from
// the merged native libs. Entries are copied raw so symlinks and modes survive.
val pythonDrop = Regex(
    "(\\.a$|/include/|/test/|/tests/|/idlelib/|/tkinter/|/turtledemo/|/lib2to3/|/ensurepip/|/pydoc_data/|" +
        "_testcapi|_testinternalcapi|_testbuffer|_testmultiphase|_testimportmultiple|_xxtestfuzz|_testclinic|" +
        "_testsinglephase|xxlimited|/curses/|_curses|libncurses|libreadline|readline\\.cpython|/share/man/|" +
        "/pkgconfig/|/unittest/|/venv/|/sqlite3/|_sqlite3|libsqlite3)"
)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("NativeLibs") }.configureEach {
    doLast {
        outputs.files.asFileTree.matching { include("**/libpython.zip.so") }.forEach { lib ->
            val trimmed = File(lib.parentFile, "libpython.zip.trim")
            CompressZipFile.builder().setFile(lib).get().use { zip ->
                ZipArchiveOutputStream(trimmed).use { out ->
                    zip.entries.asSequence().filterNot { pythonDrop.containsMatchIn(it.name) }.forEach { entry ->
                        out.addRawArchiveEntry(entry, zip.getRawInputStream(entry))
                    }
                }
            }
            lib.delete()
            trimmed.renameTo(lib)
        }
    }
}

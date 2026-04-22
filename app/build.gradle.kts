import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.apollo)
}

android {
    namespace = "tv.brunstad.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "tv.brunstad.app"
        minSdk = 23
        targetSdk = 36
        versionCode = (findProperty("buildNumber") as? String)?.toInt() ?: 2300258
        versionName = "5.6.98"
    }

    val localProps = Properties().also { props ->
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    }
    signingConfigs {
        create("release") {
            storeFile = localProps.getProperty("signing.storeFile")?.let { file(it) }
            storePassword = localProps.getProperty("signing.storePassword")
            keyAlias = localProps.getProperty("signing.keyAlias")
            keyPassword = localProps.getProperty("signing.keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    bundle {
        language {
            // App has an in-app language switcher, so all locales must be present on device.
            // Without this, only the device system language APK is installed from the bundle.
            enableSplit = false
        }
    }
}

apollo {
    service("bccmedia") {
        packageName.set("tv.brunstad.app.graphql")
        mapScalarToKotlinString("UUID")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Apollo GraphQL
    implementation(libs.apollo.runtime)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Navigation
    implementation(libs.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Secure token storage
    implementation(libs.security.crypto)

    // HTTP client
    implementation(libs.okhttp)

    // QR code generation
    implementation(libs.zxing)

    // Google TV Continue Watching
    implementation(libs.tvprovider)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

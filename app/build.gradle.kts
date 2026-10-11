import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.moneymanager"
    compileSdk = 37

    defaultConfig {
        // Never change applicationId: Android treats a new id as a different app (your data would not carry over).
        applicationId = "app.moneymanager"
        minSdk = 26
        targetSdk = 37
        // Bump versionCode for each build you install; equal is allowed too, lower is not.
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The :core parser tests also run on a phone/emulator (./gradlew connectedDebugAndroidTest), because
    // Android's regex engine (ICU) is stricter than desktop Java's and can reject patterns the JVM accepts.
    sourceSets {
        getByName("androidTest") {
            kotlin.directories.add("../core/src/test/kotlin")
            resources.directories.add("../core/src/test/resources")
        }
    }

    signingConfigs {
        // Your app's own key in keystore/ (kept out of git; GitHub Actions restores it from a secret).
        // The same key must sign every build, or Android refuses to install the update over your data.
        // Without the file (a fresh clone), Android's standard debug key is used: the build works, but the
        // APK can't update the app already on your phone.
        val projectKey = rootProject.file("keystore/debug.keystore")
        if (projectKey.exists()) {
            getByName("debug") {
                storeFile = projectKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        } else {
            logger.warn("keystore/debug.keystore not found: signing with the default debug key (this APK can't update your installed app).")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Optional faster build: minified, same signing key, same applicationId — installs over the debug APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

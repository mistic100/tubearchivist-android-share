import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

android {
    namespace = "fr.strangeplanet.tubearchivist.share"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.strangeplanet.tubearchivist.share"
        minSdk = 30
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCode").getOrElse("1").toInt()
        versionName = providers.gradleProperty("versionName").getOrElse("1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull != null) {
                storeFile = file(checkNotNull(providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull!!))
                storePassword = checkNotNull(providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull!!)
                keyAlias = checkNotNull(providers.environmentVariable("ANDROID_KEY_ALIAS").orNull!!)
                keyPassword = checkNotNull(providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull!!)
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all {
        this.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                if (this@all.buildType.name == "release") {
                    output.outputFileName = "TubeArchivistShare-${this@all.versionName}.apk"
                } else {
                    output.outputFileName = "TubeArchivistShare-debug.apk"
                }
            }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

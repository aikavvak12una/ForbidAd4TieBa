plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.forbidad4tieba.hook"
    buildFeatures {
        buildConfig = true
    }
    val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
    val releaseSigningProvided = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }
    val releaseSigningPartiallyProvided = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).any { !it.isNullOrBlank() }

    if (releaseSigningProvided) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    } else if (releaseSigningPartiallyProvided) {
        project.logger.warn(
            "[TBHook] Release signing properties are incomplete; release build will be unsigned."
        )
    }

    compileSdk = 37

    val moduleVersionCode = 31
    val minSupportedUserSettingsVersionCode = 20

    defaultConfig {
        applicationId = "com.forbidad4tieba.hook"
        minSdk = 26
        targetSdk = 36
        versionCode = moduleVersionCode
        versionName = "26070602"
        buildConfigField(
            "int",
            "MIN_SUPPORTED_USER_SETTINGS_VERSION_CODE",
            minSupportedUserSettingsVersionCode.toString()
        )
    }

    androidResources {
        localeFilters += listOf("zh")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            if (signingConfig == null) {
                project.logger.lifecycle(
                    "[TBHook] :app:release uses unsigned output (no release keystore configured)."
                )
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    packaging {
        jniLibs {
            // LSPosed loads module JNI libs from base.apk!/lib/<abi>, which requires STORED entries.
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    implementation(libs.dexkit)
    compileOnly(libs.xposed.api)
    testImplementation(libs.json)
    testImplementation(libs.junit)
}

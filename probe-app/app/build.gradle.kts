plugins {
    id("com.android.application")
}

android {
    namespace = "org.fellow99.pnpmprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.fellow99.pnpmprobe"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        // 版本名 = 被测 pnpm 版本（gradle.properties PROBE_APP_VERSION）
        versionName = providers.gradleProperty("PROBE_APP_VERSION").get()
        // 仅 arm64：验证 exec 型产物（x86_64 模拟器经 ndk_translation 可装可跑，abilist 含 arm64-v8a）
        ndk { abiFilters += "arm64-v8a" }
    }

    // 与容器 AAR 同口径：exec 要求物理落盘 nativeLibraryDir（D1）
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
    }
}

dependencies {
    // 容器 AAR（jni/arm64-v8a/libpnpm_cli.so 随 APK 落 nativeLibraryDir；D8 纯二进制无 assets）
    implementation(files("libs/pnpm-cli-release.aar"))
}

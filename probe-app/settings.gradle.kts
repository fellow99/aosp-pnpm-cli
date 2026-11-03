pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "pnpm-cli-probe-app"
include(":app")
// probe-app —— 首产探针载体（specs/003-cli/test-cases.md TC-C00…C16 的 App 内可执行子集）
// 独立 Gradle 工程（不在 aosp-pnpm-cli settings.gradle 内——000 的单模块红线不动）。
// 依赖本仓容器产物 AAR：pnpm-cli-release.aar 放到 app/libs/ 后构建。
plugins {
    id("com.android.application") version "9.2.1" apply false
}

plugins {
    alias(libs.plugins.android.library)
    id("com.vanniktech.maven.publish.base")
}

// pnpm-cli：exec 型 pnpm 命令行（libpnpm_cli.so，arm64-v8a PIE）。
// 版本号 = 工具上游（pnpm 12+）版本（由 pnpm-cli.lock.json 锁定，独立 semver）。
// 构建口径：Rust 独立二进制交叉编译（cargo-ndk）。
// 【运行期依赖】package lifecycle scripts 是 JS，由 PATH 上的 node 执行——
//   消费方必须同时引入 aosp-node-cli 并注入 PATH（本 AAR 不是孤岛，README 必须声明）。
// 二进制不入库：src/main/jniLibs/ 由 tools/provision.mjs 投放。
// 本模块不含任何 Kotlin/Java 源——它只是 AAR 容器。

apply(from = rootProject.file("publish.gradle.kts"))

// 多版本发布：gradle publishToMavenCentral -PCLI_VERSION=12.0.0 -PRELEASE=true
version = (providers.gradleProperty("CLI_VERSION").orNull
    ?: error("pnpm-cli 需要 CLI_VERSION（= pnpm 上游版本）；当前未设置"))

afterEvaluate {
    val publishing = extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java)
    publishing.publications.forEach { pub ->
        (pub as Any).javaClass.methods
            .first { it.name == "setVersion" && it.parameterCount == 1 }
            .invoke(pub, version.toString())
    }
}

android {
    namespace = "org.fellow99.pnpmcli"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    packaging {
        jniLibs {
            // exec 型工具与 dlopen 库（node-pty）相反：必须物理落盘（extractNativeLibs=true）。
            // targetSdk≥29 的 SELinux 禁止从可写数据目录 exec，nativeLibraryDir 是唯一合规
            // 执行位；useLegacyPackaging=false 时库直接从 APK mmap、不落盘，execve 会 ENOENT。
            useLegacyPackaging = true
        }
    }
}

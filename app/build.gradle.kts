import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// 发布签名:密钥库在仓库外(~/.android/mylauncher-keys),凭据在 gitignored 的 keystore.properties;
// 文件缺失时回退 debug 签名,保证其他机器/CI 也能构建 release
val releaseProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mylauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mylauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.3.7"
    }

    signingConfigs {
        create("release") {
            if (releaseProps.isNotEmpty()) {
                storeFile = file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // 交付物极致瘦身:debug 与 release 都开启 R8 压缩 + 资源收缩
        // (debug 保持 debuggable,数据备份迁移依赖 run-as)
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.window:window:1.3.0")
}

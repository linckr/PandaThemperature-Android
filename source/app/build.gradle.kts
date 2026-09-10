import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release 签名材料从仓库根目录的 keystore.properties 读取。
 * 该文件已被 .gitignore 排除，签名文件与口令都不入库、不进源码压缩包；
 * 文件不存在时 release 变体退回未签名构建，保证 assembleRelease 仍可用于编译验证。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

android {
    namespace = "com.example.pandatemperature"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.pandatemperature"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 无 keystore.properties 时为 null，Gradle 会产出未签名 release APK。
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Material Icons Extended - 包含所有 Material Icons（如 Bluetooth, Thermostat, WaterDrop 等）
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.accompanist.swiperefresh)
    
    // Room 数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Google Play Services Location（用于GPS定位）
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    /**
     * com.google.android.gms:play-services-location 经由 play-services-base
     * 传递引入 androidx.fragment:fragment:1.0.0。该版本早于
     * ComponentActivity.registerForActivityResult 所要求的 1.3.0，
     * 导致 release 变体的 lintVitalRelease 直接失败（235 条同源错误）。
     *
     * 本项目不使用 Fragment，因此这里只提升版本下限、不新增直接依赖，
     * 既满足 lint 要求，也避免把 2018 年的旧库打进发布包。
     */
    constraints {
        implementation("androidx.fragment:fragment:1.8.9") {
            because("play-services-base 传递依赖 fragment:1.0.0，低于 ActivityResult API 要求的 1.3.0")
        }
    }
}
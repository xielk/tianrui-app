plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "xiaochao.com"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "xiaochao.com"
        minSdk = 24
        targetSdk = 36
        versionCode = 130
        versionName = "1.3.0"
        val tpnsAccessId = (project.findProperty("TPNS_ACCESS_ID") as? String).orEmpty()
        val tpnsAccessKey = (project.findProperty("TPNS_ACCESS_KEY") as? String).orEmpty()
        val tpnsSecretKey = (project.findProperty("TPNS_SECRET_KEY") as? String).orEmpty()
        val tpnsServerSuffix = (project.findProperty("TPNS_SERVER_SUFFIX") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: "tpns.sh.tencent.com"
        buildConfigField("String", "TPNS_ACCESS_ID", "\"$tpnsAccessId\"")
        buildConfigField("String", "TPNS_ACCESS_KEY", "\"$tpnsAccessKey\"")
        buildConfigField("String", "TPNS_SECRET_KEY", "\"$tpnsSecretKey\"")

        manifestPlaceholders["XG_ACCESS_ID"] = tpnsAccessId
        manifestPlaceholders["XG_ACCESS_KEY"] = tpnsAccessKey
        manifestPlaceholders["XG_SERVER_SUFFIX"] = tpnsServerSuffix

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 修复：使用更兼容的方式修改 APK 文件名，避免 ClassCastException
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val mainOutput = output as? com.android.build.api.variant.impl.VariantOutputImpl
            mainOutput?.outputFileName?.set("app-${android.defaultConfig.versionCode}.apk")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    // implementation(libs.androidx.material.icons.extended) // 移除全量图标库
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation("com.amap.api:map2d:6.0.0")
    implementation("no.nordicsemi.android:ble:2.7.4")
    implementation("com.github.xuexiangjys:XUpdate:2.1.5")
    implementation("com.tencent.tpns:tpns:1.4.4.7-release")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

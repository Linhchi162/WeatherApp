plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.weatherapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.weatherapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    // Google Play Services Location (thêm mới)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Coroutines (thêm mới)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("org.json:json:20231013")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation ("androidx.work:work-runtime-ktx:2.9.1")

    implementation ("androidx.compose.material:material:1.5.0") // Đảm bảo bạn có phiên bản Compose Material
    implementation ("com.google.accompanist:accompanist-swiperefresh:0.27.0")
    implementation ("com.google.accompanist:accompanist-pager:0.25.1")

    implementation ("androidx.compose.ui:ui:1.6.4")
    implementation ("androidx.compose.material3:material3:1.2.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Hoặc phiên bản bạn dùng
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // <<-- Đảm bảo dòng này có và cùng phiên bản
}



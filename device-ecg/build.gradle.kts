plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "android.boot.device.ecg"
    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
//    coordinates("io.github.zhangwenxue", "ble-common", "1.0.0-alpha2")
    implementation(libs.client)
    implementation(libs.ble.common)
    implementation(project(":device-api"))
    implementation(project(":bluetoothx"))
    implementation(libs.android.common)
    implementation(libs.scanner)
//    implementation(libs.androidx.bluetooth)
    implementation("com.github.mik3y:usb-serial-for-android:3.7.2")
//    implementation(libs.usb.serial4android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
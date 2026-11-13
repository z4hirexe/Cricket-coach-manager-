plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cricketcoach.manager"
    compileSdk = 34

    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        applicationId = "com.cricketcoach.manager"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "2.6.0-beta3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.maxHeapSize = "768m"
                it.jvmArgs("-XX:+UseSerialGC")
            }
        }
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.google.code.gson:gson:2.10.1")
    // Google Filament (Apache-2.0) - physically based real-time 3D renderer + glTF loader
    implementation("com.google.android.filament:filament-android:1.75.1") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "androidx.annotation")
    }
    implementation("com.google.android.filament:gltfio-android:1.75.1") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "androidx.annotation")
    }
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    testImplementation("junit:junit:4.13.2")
    // UI screenshot previews rendered from the real Compose screens (Robolectric native graphics + Roborazzi)
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

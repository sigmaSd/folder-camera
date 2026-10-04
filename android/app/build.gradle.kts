plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "org.foldercamera"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.sigmasd.foldercamera"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        val path = providers.environmentVariable("FOLDER_CAMERA_KEYSTORE").orNull
        if (path != null) create("production") {
            storeFile = file(path)
            storePassword = providers.environmentVariable("FOLDER_CAMERA_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("FOLDER_CAMERA_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("FOLDER_CAMERA_KEY_PASSWORD").get()
            enableV1Signing = true; enableV2Signing = true; enableV3Signing = true
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("production")?.let { signingConfig = it }
        }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

dependencyLocking { lockAllConfigurations() }

tasks.withType<Test>().configureEach {
    val testTemporary = rootProject.layout.buildDirectory.dir("test-tmp").get().asFile
    systemProperty("java.io.tmpdir", testTemporary.absolutePath)
    doFirst { testTemporary.mkdirs() }
}

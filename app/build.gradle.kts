plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}
// Manual signing only: never put keystore passwords or keys in source control.
// In CI these variables are absent, so release candidates are unsigned.
val releaseSigningValues = listOf(
  "BT_RELEASE_STORE_FILE", "BT_RELEASE_STORE_PASSWORD",
  "BT_RELEASE_KEY_ALIAS", "BT_RELEASE_KEY_PASSWORD"
).map { System.getenv(it)?.takeIf(String::isNotBlank) }
val manualReleaseSigning = releaseSigningValues.all { it != null }
require(releaseSigningValues.all { it == null } || manualReleaseSigning) {
  "Provide all four BT_RELEASE_* signing environment variables or none of them"
}

android {
  namespace = "dev.budgettracker.android"
  compileSdk = 36
  defaultConfig {
    applicationId = "dev.budgettracker.android"
    minSdk = 26
    targetSdk = 36
    versionCode = 9
    versionName = "0.9.0-rc.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  signingConfigs {
    if (manualReleaseSigning) {
      create("manualRelease") {
        storeFile = file(requireNotNull(releaseSigningValues[0]))
        storePassword = requireNotNull(releaseSigningValues[1])
        keyAlias = requireNotNull(releaseSigningValues[2])
        keyPassword = requireNotNull(releaseSigningValues[3])
      }
    }
  }
  buildTypes {
    release {
      if (manualReleaseSigning) signingConfig = signingConfigs.getByName("manualRelease")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }
}
dependencies {
  val bom = platform("androidx.compose:compose-bom:2025.10.01")
  implementation(bom)
  androidTestImplementation(bom)
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  debugImplementation("androidx.compose.ui:ui-tooling")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

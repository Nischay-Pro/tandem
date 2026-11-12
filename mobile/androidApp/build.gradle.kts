plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.baselineProfile)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.composeCompiler)
}

val nightlyKeystorePath =
  providers.gradleProperty("tandemNightlyKeystorePath").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEYSTORE")
val nightlyKeystorePassword =
  providers.gradleProperty("tandemNightlyKeystorePassword").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
val nightlyKeyAlias =
  providers.gradleProperty("tandemNightlyKeyAlias").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEY_ALIAS")
val nightlyKeyPassword =
  providers.gradleProperty("tandemNightlyKeyPassword").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEY_PASSWORD")
val nightlySigningValues =
  listOf(nightlyKeystorePath, nightlyKeystorePassword, nightlyKeyAlias, nightlyKeyPassword)
val nightlySigningConfigured = nightlySigningValues.all { !it.isNullOrBlank() }
require(nightlySigningConfigured || nightlySigningValues.all { it.isNullOrBlank() }) {
  "Android nightly signing requires the keystore path, store password, key alias, and key password."
}

android {
  namespace = "app.tandemforactual.android"
  compileSdk = 37

  defaultConfig {
    applicationId = "app.tandemforactual.android"
    minSdk = 26
    targetSdk = 36
    versionCode = providers.gradleProperty("tandemVersionCode").orNull?.toIntOrNull() ?: 1
    versionName = providers.gradleProperty("tandemVersionName").orNull ?: "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  if (nightlySigningConfigured) {
    signingConfigs {
      create("nightly") {
        storeFile = file(nightlyKeystorePath!!)
        storePassword = nightlyKeystorePassword
        keyAlias = nightlyKeyAlias
        keyPassword = nightlyKeyPassword
      }
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      if (nightlySigningConfigured) {
        signingConfig = signingConfigs.getByName("nightly")
      }
    }

    create("nonMinifiedRelease") {
      applicationIdSuffix = ".profile"
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
  }

  buildFeatures {
    buildConfig = true
  }

  baselineProfile {
    saveInSrc = true
  }
}

dependencies {
  coreLibraryDesugaring(libs.desugarJdkLibs)
  implementation(project(":composeApp"))
  implementation(project(":data"))
  implementation(project(":api-client"))
  implementation(libs.room3Runtime)
  implementation(libs.coroutinesCore)
  implementation(libs.workRuntimeKtx)
  implementation(libs.activityCompose)
  implementation(libs.coreSplashScreen)
  implementation(libs.profileInstaller)
  implementation(libs.composeRuntimeAndroid)
  implementation(libs.composeUiAndroid)
  implementation(libs.composeFoundationAndroid)
  implementation(libs.composeMaterialAndroid)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidxTestRunner)
  androidTestImplementation(libs.androidxTestExtJunit)
  add("baselineProfile", project(":baselineprofile"))
}

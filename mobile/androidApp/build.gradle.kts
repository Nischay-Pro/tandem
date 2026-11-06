plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "app.tandemforactual.android"
  compileSdk = 37

  defaultConfig {
    applicationId = "app.tandemforactual.android"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
  }

  buildFeatures {
    buildConfig = true
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
  implementation(libs.composeRuntimeAndroid)
  implementation(libs.composeUiAndroid)
  implementation(libs.composeFoundationAndroid)
  implementation(libs.composeMaterialAndroid)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidxTestRunner)
  androidTestImplementation(libs.androidxTestExtJunit)
}

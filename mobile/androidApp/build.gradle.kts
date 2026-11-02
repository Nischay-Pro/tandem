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
  implementation(libs.room3Runtime)
  implementation(libs.activityCompose)
  implementation(libs.composeRuntimeAndroid)
  implementation(libs.composeUiAndroid)
  implementation(libs.composeFoundationAndroid)
  implementation(libs.composeMaterialAndroid)
}

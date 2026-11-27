plugins {
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.ktlint)
}

android {
  namespace = "app.tandemforactual.wearable"
  compileSdk = 37

  defaultConfig {
    minSdk = 26
  }
}

dependencies {
  api(project(":watch-protocol"))
  api(libs.playServicesWearable)
  implementation(libs.coroutinesPlayServices)
  implementation(libs.coroutinesCore)
}

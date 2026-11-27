plugins {
  alias(libs.plugins.androidTest)
  alias(libs.plugins.baselineProfile)
  alias(libs.plugins.ktlint)
}

android {
  namespace = "app.tandemforactual.android.baselineprofile"
  compileSdk = 37

  defaultConfig {
    minSdk = 28
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  targetProjectPath = ":androidApp"
}

dependencies {
  implementation(libs.benchmarkMacroJunit4)
  implementation(libs.androidxTestRunner)
  implementation(libs.androidxTestExtJunit)
}

baselineProfile {
  useConnectedDevices = true
}

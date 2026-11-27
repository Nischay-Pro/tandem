plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.ktlint)
}

kotlin {
  jvm()
  android {
    namespace = "app.tandemforactual.watch"
    compileSdk = 37
    minSdk = 26
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(libs.serializationJson)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

ktlint {
  filter {
    include("**/src/**/*.kt", "**/*.kts")
  }
}

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktlint)
}

kotlin {
  android {
    namespace = "org.actualbudget.companion.ui"
    compileSdk = 37
    minSdk = 26
  }
  iosArm64 {
    binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }
  iosSimulatorArm64 {
    binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":data"))
      implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
      implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
      implementation("org.jetbrains.compose.ui:ui:1.12.2")
      implementation("org.jetbrains.compose.material:material:1.12.2")
    }
    androidMain.dependencies {
      implementation(libs.activityCompose)
    }
  }
}

ktlint {
  filter {
    include("**/src/**/*.kt", "**/*.kts")
  }
}

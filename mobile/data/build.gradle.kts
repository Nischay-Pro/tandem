import org.gradle.api.tasks.testing.Test

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room3)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.kover)
}

kotlin {
  jvm()
  android {
    namespace = "app.tandemforactual.data"
    compileSdk = 37
    minSdk = 26

    withHostTest {
      isIncludeAndroidResources = true
    }
    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      execution = "HOST"
    }
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.room3Runtime)
      implementation(libs.sqliteBundled)
      implementation(libs.coroutinesCore)
      implementation(libs.serializationJson)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    getByName("androidHostTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.robolectric)
      }
    }
    getByName("androidDeviceTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.androidxTestRunner)
        implementation(libs.androidxTestExtJunit)
      }
    }
    getByName("iosSimulatorArm64Test") {
      dependencies {
        implementation(kotlin("test"))
      }
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

dependencies {
  add("kspAndroid", libs.room3Compiler)
  add("kspJvm", libs.room3Compiler)
  add("kspIosArm64", libs.room3Compiler)
  add("kspIosSimulatorArm64", libs.room3Compiler)
}

room3 {
  schemaDirectory("$projectDir/schemas")
}

ktlint {
  filter {
    include("**/src/**/*.kt", "**/*.kts")
    // Source-root-relative filters cannot see KSP's generated directory prefix.
    exclude("**/*_Impl.kt", "**/TandemDatabaseConstructor.kt")
  }
}

kover {
  reports {
    filters {
      excludes {
        classes(
          "app.tandemforactual.data.database.*_Impl*",
          "app.tandemforactual.data.database.TandemDatabaseConstructor*",
          "app.tandemforactual.data.store.LocalBudgetRepository$*",
        )
      }
    }
    verify {
      rule {
        minBound(100)
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
}

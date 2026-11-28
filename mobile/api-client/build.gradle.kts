plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.kover)
}

kotlin {
  jvm()
  android {
    namespace = "app.tandemforactual.api.client"
    compileSdk = 37
    minSdk = 26

    withHostTest {
      isIncludeAndroidResources = true
    }
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":data"))
      implementation(libs.room3Runtime)
      implementation(libs.coroutinesCore)
      implementation(libs.ktorClientCore)
      implementation(libs.ktorClientEncoding)
      api(libs.serializationJson)
      api(libs.ktorClientCore)
      implementation(libs.coroutinesCore)
    }
    androidMain.dependencies {
      implementation(libs.ktorClientOkhttp)
    }
    iosMain.dependencies {
      implementation(libs.ktorClientDarwin)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.ktorClientMock)
      implementation(libs.ktorClientJava)
      implementation(libs.sqliteBundled)
      implementation(libs.coroutinesCore)
      implementation(libs.serializationJson)
    }
    getByName("androidHostTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.okhttpTls)
        implementation(libs.okhttpMockWebServer)
      }
    }
  }
}

tasks.configureEach {
  if (name == "koverGenerateArtifactAndroid") {
    dependsOn("testAndroidHostTest")
  }
}

ktlint {
  filter {
    include("**/src/**/*.kt", "**/*.kts")
    exclude(
      "**/app/tandemforactual/api/generated/**",
      "**/Api.kt",
      "**/Auth.kt",
      "**/Services.kt",
      "**/Models.kt",
    )
  }
}

kover {
  reports {
    verify {
      rule {
        minBound(100)
      }
    }
  }
}

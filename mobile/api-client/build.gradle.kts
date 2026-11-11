plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.openapiKmpGen)
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
      implementation(libs.ktorClientCore)
      implementation(libs.ktorClientEncoding)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.ktorClientMock)
    }
    getByName("androidHostTest") {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}

tasks.configureEach {
  if (name == "koverGenerateArtifactAndroid") {
    dependsOn("testAndroidHostTest")
  }
}

kmpgen {
  spec(packageName = "app.tandemforactual.api.generated") {
    specFile = file("../../docs/api/openapi-v1.json")
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
    filters {
      excludes {
        // The generated API surface is checked by route/method/header contract tests.
        // Its compiler-generated default-argument bridges are not meaningful line coverage.
        classes("app.tandemforactual.api.generated.**")
      }
    }
    verify {
      rule {
        minBound(100)
      }
    }
  }
}

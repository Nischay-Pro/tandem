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
    compileSdk = 36
    minSdk = 26
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
        // Kover treats the generated, compile-time @SerialName metadata line as executable.
        classes("app.tandemforactual.api.generated.models.ApiV1HealthReady200Response*")
      }
    }
    verify {
      rule {
        minBound(100)
      }
    }
  }
}

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
    namespace = "org.actualbudget.companion.api.client"
    compileSdk = 36
    minSdk = 26
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.ktorClientMock)
    }
  }
}

kmpgen {
  spec(packageName = "org.actualbudget.companion.api.generated") {
    specFile = file("../../docs/api/openapi-v1.json")
  }
}

ktlint {
  filter {
    exclude { element -> element.file.path.contains("/build/generated/kmpgen/") }
  }
}

kover {
  reports {
    filters {
      excludes {
        // Kover treats the generated, compile-time @SerialName metadata line as executable.
        classes("org.actualbudget.companion.api.generated.models.ApiV1HealthReady200Response*")
      }
    }
    verify {
      rule {
        minBound(100)
      }
    }
  }
}

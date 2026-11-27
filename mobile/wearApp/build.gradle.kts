plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktlint)
}

val nightlyKeystorePath =
  providers.gradleProperty("tandemNightlyKeystorePath").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEYSTORE")
val nightlyKeystorePassword =
  providers.gradleProperty("tandemNightlyKeystorePassword").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
val nightlyKeyAlias =
  providers.gradleProperty("tandemNightlyKeyAlias").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEY_ALIAS")
val nightlyKeyPassword =
  providers.gradleProperty("tandemNightlyKeyPassword").orNull
    ?: System.getenv("TANDEM_ANDROID_NIGHTLY_KEY_PASSWORD")
val nightlySigningConfigured =
  listOf(nightlyKeystorePath, nightlyKeystorePassword, nightlyKeyAlias, nightlyKeyPassword).all { !it.isNullOrBlank() }

android {
  namespace = "app.tandemforactual.wear"
  compileSdk = 37

  defaultConfig {
    applicationId = "app.tandemforactual.android"
    minSdk = 30
    targetSdk = 37
    versionCode = providers.gradleProperty("tandemVersionCode").orNull?.toIntOrNull() ?: 1
    versionName = providers.gradleProperty("tandemVersionName").orNull ?: "0.1.0"
  }

  if (nightlySigningConfigured) {
    signingConfigs {
      create("nightly") {
        storeFile = file(nightlyKeystorePath!!)
        storePassword = nightlyKeystorePassword
        keyAlias = nightlyKeyAlias
        keyPassword = nightlyKeyPassword
      }
    }
  }

  buildTypes {
    getByName("debug") {
      applicationIdSuffix = ".localtest"
    }

    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      if (nightlySigningConfigured) {
        signingConfig = signingConfigs.getByName("nightly")
      }
    }
  }

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  implementation(project(":watch-protocol"))
  implementation(libs.activityCompose)
  implementation(libs.composeUiAndroid)
  implementation(libs.composeFoundationAndroid)
  implementation(libs.wearComposeMaterial3)
  implementation(libs.wearComposeFoundation)
  implementation(libs.wearComposeNavigation)
  implementation(libs.wearInput)
  implementation(libs.wearRemoteInteractions)
  implementation(libs.wearTiles)
  implementation(libs.wearProtolayout)
  implementation(libs.wearProtolayoutMaterial3)
  implementation(libs.wearProtolayoutExpression)
  implementation(libs.wearComplicationsDataSource)
  implementation(libs.concurrentFuturesKtx)
  implementation(libs.workRuntimeKtx)
  implementation(libs.coroutinesCore)
  implementation(libs.playServicesWearable)
  implementation(libs.coroutinesPlayServices)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.wearTilesTesting)
}

val robolectricSdk by configurations.creating { isTransitive = false }

dependencies {
  robolectricSdk(libs.robolectricSdk)
}

val prepareRobolectricSdk by tasks.registering(Sync::class) {
  from(robolectricSdk)
  into(layout.buildDirectory.dir("robolectric-sdk"))
}

tasks.withType<Test>().configureEach {
  dependsOn(prepareRobolectricSdk)
  val sdkDirectory = prepareRobolectricSdk.map { it.destinationDir.absolutePath }
  jvmArgumentProviders.add(
    CommandLineArgumentProvider {
      listOf("-Drobolectric.offline=true", "-Drobolectric.dependency.dir=${sdkDirectory.get()}")
    },
  )
}

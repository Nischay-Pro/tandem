plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.baselineProfile)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ksp)
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
val nightlySigningValues =
  listOf(nightlyKeystorePath, nightlyKeystorePassword, nightlyKeyAlias, nightlyKeyPassword)
val nightlySigningConfigured = nightlySigningValues.all { !it.isNullOrBlank() }
require(nightlySigningConfigured || nightlySigningValues.all { it.isNullOrBlank() }) {
  "Android nightly signing requires the keystore path, store password, key alias, and key password."
}

android {
  namespace = "app.tandemforactual.android"
  compileSdk = 37

  androidResources {
    noCompress += "tflite"
  }

  defaultConfig {
    applicationId = "app.tandemforactual.android"
    minSdk = 26
    targetSdk = 37
    versionCode = providers.gradleProperty("tandemVersionCode").orNull?.toIntOrNull() ?: 1
    versionName = providers.gradleProperty("tandemVersionName").orNull ?: "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    create("nonMinifiedRelease") {
      applicationIdSuffix = ".profile"
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
  }

  buildFeatures {
    buildConfig = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  baselineProfile {
    saveInSrc = true
  }
}

dependencies {
  coreLibraryDesugaring(libs.desugarJdkLibs)
  implementation(project(":composeApp"))
  implementation(project(":data"))
  implementation(project(":api-client"))
  implementation(project(":watch-protocol"))
  implementation(project(":wearable-play"))
  implementation(libs.room3Runtime)
  implementation(libs.coroutinesCore)
  implementation(libs.workRuntimeKtx)
  implementation(libs.activityCompose)
  implementation(libs.windowManager)
  implementation(libs.coreSplashScreen)
  implementation(libs.biometric)
  implementation(libs.browser)
  implementation(libs.fragment)
  implementation(libs.sqlcipher)
  implementation(libs.profileInstaller)
  implementation(libs.taskerPluginLibrary)
  implementation(libs.appfunctions)
  ksp(libs.appfunctionsCompiler)
  implementation(libs.composeRuntimeAndroid)
  implementation(libs.composeUiAndroid)
  implementation(libs.composeFoundationAndroid)
  implementation(libs.composeMaterialAndroid)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.windowTesting)
  testImplementation(libs.appfunctionsTesting)
  testImplementation(libs.workTesting)
  testImplementation(libs.sqliteFramework)
  androidTestImplementation(libs.androidxTestRunner)
  androidTestImplementation(libs.androidxTestExtJunit)
  add("baselineProfile", project(":baselineprofile"))
}

val robolectricSdk by configurations.creating { isTransitive = false }
val robolectricSdk35 by configurations.creating { isTransitive = false }

dependencies {
  robolectricSdk(libs.robolectricSdk)
  robolectricSdk35(libs.robolectricSdk35)
}

val prepareRobolectricSdk by tasks.registering(Sync::class) {
  from(robolectricSdk, robolectricSdk35)
  into(layout.buildDirectory.dir("robolectric-sdk"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
  systemProperty("kotlinx.coroutines.stacktrace.recovery", "false")
  dependsOn(prepareRobolectricSdk)
  val sdkDirectory = prepareRobolectricSdk.map { it.destinationDir.absolutePath }
  jvmArgumentProviders.add(
    CommandLineArgumentProvider {
      listOf("-Drobolectric.offline=true", "-Drobolectric.dependency.dir=${sdkDirectory.get()}")
    },
  )
}

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
  val sqlcipherSources = layout.projectDirectory.dir("src/nativeInterop/sqlcipher")
  listOf(
    Triple(iosArm64(), "iphoneos", "arm64-apple-ios16.0"),
    Triple(iosSimulatorArm64(), "iphonesimulator", "arm64-apple-ios16.0-simulator"),
  ).forEach { (target, sdk, triple) ->
    val targetName = target.name.replaceFirstChar(Char::uppercase)
    val libraryDirectory = layout.buildDirectory.dir("sqlcipher/${target.name}")
    val buildSqlcipher =
      tasks.register<Exec>("buildTandemSqlcipher$targetName") {
        onlyIf { System.getProperty("os.name").startsWith("Mac") }
        inputs.dir(sqlcipherSources)
        outputs.dir(libraryDirectory)
        commandLine(
          "sh",
          sqlcipherSources.file("build-ios.sh").asFile.absolutePath,
          sdk,
          triple,
          libraryDirectory.get().asFile.absolutePath,
        )
      }
    target.compilations.getByName("main").cinterops.create("tandemSqlcipher") {
      definitionFile.set(project.file("src/nativeInterop/cinterop/tandemSqlcipher.def"))
      includeDirs(sqlcipherSources)
      extraOpts("-libraryPath", libraryDirectory.get().asFile.absolutePath)
    }
    tasks.matching { it.name == "cinteropTandemSqlcipher$targetName" }.configureEach { dependsOn(buildSqlcipher) }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.room3Runtime)
      implementation(libs.sqliteBundled)
      implementation(libs.coroutinesCore)
      implementation(libs.serializationJson)
      implementation(libs.cryptographyCore)
      implementation(libs.cryptographyOptimal)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    getByName("androidHostTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.robolectric)
        implementation(libs.sqliteFramework)
      }
    }
    getByName("androidDeviceTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.androidxTestRunner)
        implementation(libs.androidxTestExtJunit)
        implementation(libs.sqlcipher)
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
          // Kover attributes the tested default method's signature-only line to its synthetic bridge.
          "app.tandemforactual.data.store.BudgetSyncRemote",
          "app.tandemforactual.data.store.BudgetSyncRemote\$*",
          "app.tandemforactual.data.database.ActualBudgetFiles",
          "app.tandemforactual.data.database.ActualBudgetFiles\$*",
          "app.tandemforactual.data.store.LocalBudgetRepository$*",
          "app.tandemforactual.data.store.LocalBudgetLifecycleKt$*",
          "app.tandemforactual.data.store.LocalBudgetMutationQueueKt$*",
          "app.tandemforactual.data.store.LocalBudgetObserversKt$*",
          "app.tandemforactual.data.store.LocalBudgetQueriesKt$*",
          "app.tandemforactual.data.store.LocalBudgetSnapshotsKt$*",
          "app.tandemforactual.data.store.LocalChangePagesKt$*",
          "app.tandemforactual.data.store.LocalRecordSupportKt$*",
          "app.tandemforactual.data.store.LocalReferenceRemapsKt$*",
          "app.tandemforactual.data.store.LocalScheduleActionQueueKt$*",
          "app.tandemforactual.data.store.LocalWriteOutboxKt$*",
          "app.tandemforactual.data.store.LocalWriteResultsKt$*",
          "app.tandemforactual.data.store.LocalWriteValidationKt$*",
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

val robolectricSdk by configurations.creating { isTransitive = false }

dependencies {
  robolectricSdk(libs.robolectricSdk)
}

val prepareRobolectricSdk by tasks.registering(Sync::class) {
  from(robolectricSdk)
  into(layout.buildDirectory.dir("robolectric-sdk"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
  dependsOn(prepareRobolectricSdk)
  val sdkDirectory = prepareRobolectricSdk.map { it.destinationDir.absolutePath }
  jvmArgumentProviders.add(
    CommandLineArgumentProvider {
      listOf("-Drobolectric.offline=true", "-Drobolectric.dependency.dir=${sdkDirectory.get()}")
    },
  )
}

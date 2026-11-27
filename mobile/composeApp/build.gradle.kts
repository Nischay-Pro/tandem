import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktlint)
}

val repositoryRoot = rootProject.projectDir.parentFile
val generatedTheSvgResources = layout.buildDirectory.dir("generated/thesvg-icon-pack/composeResources")
val combinedComposeResources =
  tasks.register<Sync>("combineCommonComposeResources") {
    dependsOn("generateTheSvgIconPack")
    from(generatedTheSvgResources)
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    into(layout.buildDirectory.dir("generated/combinedComposeResources"))
  }
val usePreGeneratedIconPack =
  providers.environmentVariable("TANDEM_PREGENERATED_ICON_PACK").map { it == "true" }.orElse(false)
val generateTheSvgIconPack =
  tasks.register<Exec>("generateTheSvgIconPack") {
    group = "build setup"
    description = "Creates offline Compose merchant resources from pinned catalogs and documented brand sources."
    enabled = !usePreGeneratedIconPack.get()
    workingDir = repositoryRoot
    commandLine(
      "node",
      repositoryRoot.resolve("scripts/generate-thesvg-icon-pack.mjs").absolutePath,
      "--output",
      generatedTheSvgResources.get().asFile.absolutePath,
    )
    inputs.files(
      repositoryRoot.resolve("scripts/generate-thesvg-icon-pack.mjs"),
      repositoryRoot.resolve("mobile/branding/tandem-mark.svg"),
      repositoryRoot.resolve("mobile/branding/tandem-mark-light.svg"),
      repositoryRoot.resolve("mobile/composeApp/third_party/merchant-icons/panera-bread-wordmark.svg"),
      repositoryRoot.resolve("mobile/composeApp/third_party/Apache-2.0.txt"),
      repositoryRoot.resolve("package.json"),
      repositoryRoot.resolve("pnpm-lock.yaml"),
    )
    inputs.files(
      fileTree(repositoryRoot.resolve("node_modules/.pnpm/@thesvg+icons@4.0.4/node_modules/@thesvg/icons")),
      fileTree(repositoryRoot.resolve("node_modules/.pnpm/@rei+cedar-icons@2.6.1/node_modules/@rei/cedar-icons")),
      fileTree(repositoryRoot.resolve("node_modules/.pnpm/@resvg+resvg-js@2.6.2/node_modules/@resvg/resvg-js")),
    )
    outputs.dir(generatedTheSvgResources)
  }

tasks.configureEach {
  if (
    name == "copyAndroidMainComposeResourcesToAndroidAssets" ||
    name == "prepareComposeResourcesTaskForCommonMain" ||
    name == "copyNonXmlValueResourcesForCommonMain" ||
    name == "convertXmlValueResourcesForCommonMain"
  ) {
    dependsOn(generateTheSvgIconPack)
  }
}

kotlin {
  android {
    namespace = "app.tandemforactual.ui"
    compileSdk = 37
    minSdk = 26

    androidResources {
      enable = true
    }

    withHostTest {
      isIncludeAndroidResources = true
    }
  }
  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
      // The whole-program call-graph passes outgrew the macOS runners' memory: hours of garbage collection, then out of memory.
      if (buildType == NativeBuildType.RELEASE) {
        freeCompilerArgs += "-Xdisable-phases=DevirtualizationAnalysis,RemoveRedundantCallsToStaticInitializersPhase,EscapeAnalysis"
      }
    }
  }

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.ktorClientMock)
    }
    getByName("iosSimulatorArm64Test") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.coroutinesTest)
      }
    }
    commonMain.dependencies {
      implementation(project(":data"))
      implementation(libs.room3Runtime)
      implementation(project(":api-client"))
      implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
      implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
      implementation(libs.composeAnimation)
      implementation(libs.composeIconsLucide)
      implementation(libs.composeResources)
      implementation("org.jetbrains.compose.ui:ui:1.12.2")
      implementation("org.jetbrains.compose.material:material:1.12.2")
    }
    androidMain.dependencies {
      implementation(libs.activityCompose)
      implementation(libs.litert)
      implementation(libs.windowManager)
    }
    getByName("androidHostTest") {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.room3Runtime)
        implementation(libs.sqliteFramework)
        implementation(libs.composeUiTestJunit4)
        implementation(libs.composeUiTestManifest)
        implementation(libs.espressoCore)
        implementation(libs.robolectric)
        implementation(libs.windowTesting)
        implementation(libs.konsist)
      }
    }
  }
}

ktlint {
  filter {
    exclude { element -> element.file.path.contains("/build/generated/") }
    include("**/src/**/*.kt", "**/*.kts")
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

compose.resources {
  packageOfResClass = "app.tandemforactual.ui.generated.resources"
  customDirectory(
    sourceSetName = "commonMain",
    directoryProvider = combinedComposeResources.map { it.destinationDirectory.get() },
  )
}

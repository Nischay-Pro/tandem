plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktlint)
}

val repositoryRoot = rootProject.projectDir.parentFile
val generatedTheSvgResources = layout.buildDirectory.dir("generated/thesvg-icon-pack/composeResources")
val generateTheSvgIconPack =
  tasks.register<Exec>("generateTheSvgIconPack") {
    group = "build setup"
    description = "Creates offline Compose merchant resources from pinned catalogs and documented brand sources."
    workingDir = repositoryRoot
    commandLine(
      "node",
      repositoryRoot.resolve("scripts/generate-thesvg-icon-pack.mjs").absolutePath,
      "--output",
      generatedTheSvgResources.get().asFile.absolutePath,
    )
    doFirst {
      val requiredPackages =
        listOf(
          repositoryRoot.resolve("node_modules/.pnpm/@thesvg+icons@4.0.4/node_modules/@thesvg/icons/package.json"),
          repositoryRoot.resolve("node_modules/.pnpm/@rei+cedar-icons@2.6.1/node_modules/@rei/cedar-icons/package.json"),
          repositoryRoot.resolve("node_modules/.pnpm/@resvg+resvg-js@2.6.2/node_modules/@resvg/resvg-js/package.json"),
        )
      val missingPackages = requiredPackages.filterNot { it.isFile }
      if (missingPackages.isNotEmpty()) {
        throw GradleException(
          "The bundled merchant icon resources need the pinned Node packages. " +
            "From the repository root, run `mise install` and `pnpm install --frozen-lockfile`, " +
            "then retry the Gradle task.",
        )
      }
    }
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
    getByName("iosSimulatorArm64Test") {
      dependencies {
        implementation(kotlin("test"))
      }
    }
    commonMain.dependencies {
      implementation(project(":data"))
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
    }
    getByName("androidHostTest") {
      dependencies {
        implementation(kotlin("test"))
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

compose.resources {
  packageOfResClass = "app.tandemforactual.ui.generated.resources"
  customDirectory(
    sourceSetName = "commonMain",
    directoryProvider = generatedTheSvgResources,
  )
}

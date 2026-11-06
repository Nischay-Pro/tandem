plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidTest) apply false
  alias(libs.plugins.baselineProfile) apply false
  alias(libs.plugins.jetbrainsCompose) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.openapiKmpGen) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.kover) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.room3) apply false
}

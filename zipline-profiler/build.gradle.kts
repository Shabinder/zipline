import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
}

kotlin {
  jvm()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) linuxX64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) macosX64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) macosArm64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) iosArm64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) iosX64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) iosSimulatorArm64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) tvosArm64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) tvosSimulatorArm64()
  if (!providers.gradleProperty("ziplineJvmJsOnly").isPresent) tvosX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.zipline)
        api(libs.okio.core)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))
      }
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty()))
}

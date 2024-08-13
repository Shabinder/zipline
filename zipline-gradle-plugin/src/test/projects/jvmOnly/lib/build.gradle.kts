import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  kotlin("multiplatform")
  id("io.github.shabinder")
}

kotlin {
  jvm()

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val bindAndTakeJvm by tasks.creating(JavaExec::class) {
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.BindAndTakeJvmKt")
}

zipline {
  mainFunction.set("")
}

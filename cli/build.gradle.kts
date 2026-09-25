import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":semantic-clangd"))
    implementation(project(":workspace-satisfactory"))
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

application {
    mainClass.set("dev.maxin.factorylens.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

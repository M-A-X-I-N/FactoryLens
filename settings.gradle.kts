rootProject.name = "FactoryLens"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.0"
        id("org.jetbrains.intellij.platform") version "2.19.0"
    }
}

include(":core")
include(":semantic-clangd")
include(":cli")

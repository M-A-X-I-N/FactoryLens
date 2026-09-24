import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

subprojects {
    repositories {
        mavenCentral()
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":semantic-clangd"))

    // Rider supplies Kotlin stdlib. Do not bundle another copy into the plugin.
    compileOnly(kotlin("stdlib"))

    intellijPlatform {
        rider("2026.2.2")
    }
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

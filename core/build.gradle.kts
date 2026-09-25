import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}


tasks.test {
    useJUnitPlatform()
}

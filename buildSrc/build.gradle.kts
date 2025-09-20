plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
//    toolchain {
//        languageVersion.set(JavaLanguageVersion.of(25))
//    }
}

kotlin {
    jvmToolchain(24)
}

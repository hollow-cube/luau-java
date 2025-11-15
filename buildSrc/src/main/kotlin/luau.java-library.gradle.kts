plugins {
    `java-library`

    `maven-publish`
    signing
    alias(libs.plugins.nmcp)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.annotations)

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testImplementation(libs.junit.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.8.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.GRAAL_VM
    }

    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

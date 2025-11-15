plugins {
    `java-library`
    alias(libs.plugins.spotless)

    `maven-publish`
    signing
    alias(libs.plugins.nmcp)
    alias(libs.plugins.nmcp.aggregation)
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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions)
        .addStringOption("source", "25")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

signing {
    isRequired = System.getenv("CI") != null

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
    useInMemoryPgpKeys(privateKey, keyPassphrase)

    sign(publishing.publications)
}

spotless {
    java {
        target("src/**/*.java")
        targetExclude("src/generated/**/*.java")

        removeUnusedImports()

        prettier(
            mapOf(
                "prettier" to "3.6.2",
                "prettier-plugin-java" to "2.7.7",
            )
        ).config(
            mapOf(
                "plugins" to listOf("prettier-plugin-java"),
                "parser" to "java",
                "tabWidth" to 4,
                "maxLineLength" to 80,
            )
        )
    }
}

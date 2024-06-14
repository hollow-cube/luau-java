plugins {
    id("java")
}

group = "net.hollowcube"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:24.1.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

sourceSets {
    main {
        java.srcDir(file("src/main/java"))
        java.srcDir(file("src/generated/java"))
    }
}

tasks.test {
    useJUnitPlatform()

    jvmArgs("--enable-preview")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"

    options.compilerArgs.add("--enable-preview")
}

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    `java-library`

    `maven-publish`
    signing
}

group = rootProject.group
version = rootProject.version
description = rootProject.description

val artifactName = "luau-natives-${getOsName()}-${getArchName()}"

val buildProjectDir = file(layout.buildDirectory.file("root").get())

task<Copy>("copyForModification") {
    description = "Copy project to the build directory for modification"
    from(layout.projectDirectory)
    exclude("build", "cmake-build-debug", "build.gradle.kts")
    into(buildProjectDir)
}

task("luauStaticToShared") {
    description = "Make Luau.Compiler and Luau.VM compile as shared libraries"
    dependsOn("copyForModification")

    doLast {
        val target = buildProjectDir.resolve("luau/CMakeLists.txt")
        target.writeText(
            target.readText()
                .replace("add_library(Luau.Compiler STATIC)", "add_library(Luau.Compiler SHARED)")
                .replace("add_library(Luau.VM STATIC)", "add_library(Luau.VM SHARED)")
        )
    }
}

task<Exec>("prepNative") {
    dependsOn("luauStaticToShared")
    workingDir = file(layout.buildDirectory)
    standardOutput = System.out
    logging.captureStandardOutput(LogLevel.INFO)
    logging.captureStandardError(LogLevel.ERROR)

    mkdir(workingDir)
    commandLine(
        "cmake",
        "-DLUAU_EXTERN_C=ON",
        "-DLUAU_BUILD_CLI=OFF",
        "-DLUAU_BUILD_TESTS=OFF",
        "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
        "-B", ".",
        "-S", buildProjectDir
    )
}

task<Exec>("buildNative") {
    workingDir = file(layout.buildDirectory)
    standardOutput = System.out
    logging.captureStandardOutput(LogLevel.INFO)
    logging.captureStandardError(LogLevel.ERROR)

    dependsOn("prepNative")
    commandLine("cmake", "--build", ".")
}

task<Copy>("copyNative") {
    dependsOn("buildNative")

    from(file(layout.buildDirectory).resolve("lib"))
    into(file(layout.buildDirectory).resolve("nres/net/hollowcube/luau/${getOsName()}/${getArchName()}"))
}

tasks.withType<ProcessResources> {
    dependsOn("copyNative")
}
tasks.withType<Jar> {
    dependsOn("copyNative")

    archiveBaseName = artifactName
}

sourceSets.main {
    resources.srcDirs.clear()
    resources.srcDir(file(layout.buildDirectory).resolve("nres"))
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishing.publications.create<MavenPublication>("native") {
    groupId = project.group.toString()
    artifactId = artifactName
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)

        val commonPomConfig: Action<MavenPom> by project.ext
        commonPomConfig.execute(this)
    }
}

signing {
//    isRequired = System.getenv("CI") != null
    isRequired = false

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
    useInMemoryPgpKeys(privateKey, keyPassphrase)

    sign(publishing.publications)
}

fun getOsName(): String {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    return when {
        os.isMacOsX -> "macos"
        os.isLinux -> "linux"
        os.isWindows -> "windows"
        else -> throw IllegalStateException("Unsupported operating system: ${os.name}")
    }
}

fun getArchName(): String {
    val arch = DefaultNativePlatform.getCurrentArchitecture()
    return when {
        arch.isAmd64 -> "x64"
        arch.isArm64 -> "arm64"
        else -> throw IllegalStateException("Unsupported architecture: ${arch.name}")
    }
}

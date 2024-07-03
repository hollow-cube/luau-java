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
    include("luau/**", "src/**", "CMakeLists.txt")
    into(buildProjectDir)
}

task("luauStaticToShared") {
    description = "Make Luau.Compiler and Luau.VM compile as shared libraries"
    dependsOn("copyForModification")

    val targetFile = buildProjectDir.resolve("luau/CMakeLists.txt")
    inputs.file(targetFile)
    outputs.file(targetFile)

    doLast {
        val originalText = targetFile.readText()
        val modifiedText = originalText
            .replace("add_library(Luau.Compiler STATIC)", "add_library(Luau.Compiler SHARED)")
            .replace("add_library(Luau.VM STATIC)", "add_library(Luau.VM SHARED)")

        if (originalText != modifiedText) {
            targetFile.writeText(modifiedText)
        }
    }
}

task<Exec>("prepNative") {
    dependsOn("luauStaticToShared")
    workingDir = file(layout.buildDirectory).resolve("cmake")
    standardOutput = System.out

    inputs.dir(buildProjectDir)
    outputs.dir(file(layout.buildDirectory))

    doFirst { mkdir(workingDir) }

    val cmake: String? by project.extra
    val args = mutableListOf(
        cmake ?: throw IllegalStateException("cmake not found on path"),
        "-DLUAU_EXTERN_C=ON",
        "-DLUAU_BUILD_CLI=OFF",
        "-DLUAU_BUILD_TESTS=OFF",
        "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
        "-B", ".",
        "-S", buildProjectDir
    )
    if (getOsName() == "windows") args += listOf(
        "-DCMAKE_WINDOWS_EXPORT_ALL_SYMBOLS=TRUE",
        "-DBUILD_SHARED_LIBS=TRUE",
    )
    commandLine(args)
}

task<Exec>("buildNative") {
    dependsOn("prepNative")
    workingDir = file(layout.buildDirectory).resolve("cmake")
    standardOutput = System.out

    inputs.dir(workingDir)
    outputs.dir(workingDir.resolve("lib"))

    val cmake: String? by project.extra
    commandLine(
        cmake ?: throw IllegalStateException("cmake not found on path"),
        "--build", "."
    )
}

task<Copy>("copyNative") {
    dependsOn("buildNative")

    var libPath = "cmake/lib" // todo it should definitely be a release build. need to do that.
    if (getOsName() == "windows") libPath += "/Debug"
    from(file(layout.buildDirectory).resolve(libPath))
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
    isRequired = System.getenv("CI") != null

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

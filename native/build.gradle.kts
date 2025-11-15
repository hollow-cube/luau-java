plugins {
    id("luau.java-library")
}

group = rootProject.group
version = rootProject.version
description = rootProject.description

val artifactName = "luau-natives-${platformOs}-${platformArch}"
val buildType: String by project.extra { "Debug" }

tasks.register<Exec>("cmakeConfigure") {
    workingDir = file(layout.buildDirectory).resolve("cmake-build")
    standardOutput = System.out

    // Configuration does not rely on sources, notably.
    inputs.property("buildType", buildType)
    inputs.files(
        layout.projectDirectory.file("luau/CMakeLists.txt"),
        layout.projectDirectory.file("CMakeLists.txt")
    )

    outputs.dir(workingDir)
    outputs.file(workingDir.resolve("CMakeCache.txt"))

    doFirst { mkdir(workingDir) }

    val args = mutableListOf<String>(
        cmakeExecutable ?: throw IllegalStateException("cmake not found on path"),
        "-DCMAKE_BUILD_TYPE=${buildType}", "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
        // We don't control the library so warnings are kinda irrelevant. can just suppress.
        "-DCMAKE_CXX_FLAGS=-w", "-DCMAKE_C_FLAGS=-w",
        // Just need the libraries themselves, not the extras.
        "-DLUAU_BUILD_CLI=OFF", "-DLUAU_BUILD_TESTS=OFF",
        "-DLUAU_EXTERN_C=ON",
        "-B", ".",
        "-S", layout.projectDirectory.asFile.absolutePath
    )
    if (platformOs == "windows") args += listOf(
        "-DCMAKE_WINDOWS_EXPORT_ALL_SYMBOLS=TRUE",
        "-DBUILD_SHARED_LIBS=TRUE",
    )
    commandLine(args)
}

tasks.register<Exec>("cmakeBuild") {
    dependsOn("cmakeConfigure")
    workingDir = file(layout.buildDirectory).resolve("cmake-build")
    standardOutput = System.out

    // CMake build files input
    inputs.dir(workingDir.resolve("CMakeFiles"))
    inputs.dir(workingDir.resolve("luau/CMakeFiles"))

    // GlobalRef & Luau source inputs
    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.dir(layout.projectDirectory.dir("luau"))

    outputs.dir(
        workingDir.resolve(
            if (platformOs == "windows") "lib/${buildType}"
            else "lib"
        )
    )

    commandLine(
        cmakeExecutable ?: throw IllegalStateException("cmake not found on path"),
        "--build", "."
    )
}

tasks.register<Copy>("copyNativeResources") {
    dependsOn("cmakeBuild")

    val nativeResources = "nativeResources/net/hollowcube/luau/${platformOs}/${platformArch}"

    var libPath = "cmake-build/lib"
    if (platformOs == "windows") libPath += "/${buildType}"
    from(file(layout.buildDirectory).resolve(libPath))
    into(file(layout.buildDirectory).resolve(nativeResources))
}

tasks.withType<ProcessResources> {
    dependsOn("copyNativeResources")
}
tasks.withType<Jar> {
    dependsOn("copyNativeResources")
    archiveBaseName = artifactName
}

sourceSets.main {
    val nativeResources = file(layout.buildDirectory).resolve("nativeResources");
    resources.srcDir(nativeResources)
}

publishing.publications.create<MavenPublication>("native") {
    groupId = project.group.toString()
    artifactId = artifactName
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)
        configureMavenPom(this)
    }
}

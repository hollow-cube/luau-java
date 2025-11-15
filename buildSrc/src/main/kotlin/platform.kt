import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import java.io.File

val platformOs by lazy {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    when {
        os.isMacOsX -> "macos"
        os.isLinux -> "linux"
        os.isWindows -> "windows"
        else -> throw IllegalStateException("Unsupported operating system: ${os.name}")
    }
}

val platformArch by lazy {
    val arch = DefaultNativePlatform.getCurrentArchitecture()
    when {
        arch.isAmd64 -> "x64"
        arch.isArm64 -> "arm64"
        else -> throw IllegalStateException("Unsupported architecture: ${arch.name}")
    }
}

val cmakeExecutable by lazy {
    var name = "cmake"
    if (getCurrentOperatingSystem().isWindows) name += ".exe"
    val pathDirs = System.getenv("PATH").split(File.pathSeparator)
    val executable = pathDirs.map { File(it, name) }
        .find { it.exists() && it.canExecute() }
    executable?.absolutePath
}

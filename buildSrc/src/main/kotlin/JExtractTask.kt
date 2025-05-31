import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import javax.inject.Inject

@CacheableTask
abstract class JExtractTask @Inject constructor(
    private val javaToolchains: JavaToolchainService
) : Exec() {

    @get:InputFile
    @PathSensitive(value = PathSensitivity.RELATIVE)
    val header: RegularFileProperty = project.objects.fileProperty()

    @get:Input
    val targetPackage: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val extraArgs: ListProperty<String> = project.objects.listProperty(String::class.java)

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty().convention(project.provider {
        val pkg = targetPackage.get().replace(".", "/")
        project.layout.projectDirectory.dir("src/generated/java/$pkg")
    })

    @TaskAction
    override fun exec() {
        val launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(23))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get()

        val jdkHome = launcher.metadata.installationPath.asFile
        val jextract = jdkHome.resolve("bin/jextract")
        if (!jextract.exists()) {
            throw IllegalStateException("jextract not found at ${jextract.absolutePath}")
        }

        commandLine(
            listOf(
                jextract,
                header.get(), "--output", "src/generated/java",
                "--target-package", targetPackage.get()
            ) + extraArgs.get()
        )
        workingDir = this.project.layout.projectDirectory.asFile
        super.exec()
    }
}
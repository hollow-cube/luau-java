plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation(project(":native"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

application {
    mainClass = "net.hollowcube.luau.ForceLoadAll"
    applicationDefaultJvmArgs = listOf("-agentlib:native-image-agent=config-output-dir=./agent-out/")
}

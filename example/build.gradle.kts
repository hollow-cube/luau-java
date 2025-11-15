plugins {
    id("luau.java-binary")
}

dependencies {
    implementation(project(":"))
    implementation(project(":native"))
}

application {
    mainClass = "net.hollowcube.luau.ForceLoadAll"
    applicationDefaultJvmArgs = listOf("-agentlib:native-image-agent=config-output-dir=./agent-out/")
}

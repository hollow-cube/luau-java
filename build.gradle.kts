plugins {
    id("luau.java-library")
    id("luau.jextract")
}

group = "dev.hollowcube"
version = System.getenv("TAG_VERSION") ?: "dev"
description = "Java bindings for Luau"

dependencies {
    testImplementation(project(":native"))
}

sourceSets {
    main {
        java.srcDir(file("src/main/java"))
        java.srcDir(file("src/generated/java"))
    }
}

tasks.withType<Jar> {
    archiveBaseName = "luau"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing.publications.create<MavenPublication>("luau") {
    groupId = project.group.toString()
    artifactId = "luau"
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)

        configureMavenPom(this)
    }
}

// Multi publishing setup

nmcpAggregation {
    centralPortal {
        username = System.getenv("SONATYPE_USERNAME")
        password = System.getenv("SONATYPE_PASSWORD")
        publishingType = "AUTOMATIC"
    }
}

dependencies {
    if (System.getenv("LUAU_PUBLISH_ROOT") != null)
        nmcpAggregation(rootProject)
    if (System.getenv("LUAU_PUBLISH_NATIVES") != null)
        nmcpAggregation(project(":native"))
}

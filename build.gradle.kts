import io.github.gradlenexus.publishplugin.NexusPublishExtension

plugins {
    `java-library`

    `maven-publish`
    signing
    alias(libs.plugins.nexuspublish)
}

group = "dev.hollowcube"
version = "1.0.0"
description = "Luau bindings for Java"

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

configure<NexusPublishExtension> {
    this.packageGroup.set("dev.hollowcube")

    repositories.sonatype {
        nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
        snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))

        if (System.getenv("SONATYPE_USERNAME") != null) {
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

allprojects {
    extra["commonPomConfig"] = Action<MavenPom> {
        description.set(project.description)
        url.set("https://github.com/hollow-cube/luau-java")

        licenses {
            license {
                name.set("MIT")
                url.set("https://github.com/hollow-cube/luau-java/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mworzala")
                name.set("Matt Worzala")
                email.set("matt@hollowcube.dev")
            }
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/hollow-cube/luau-java/issues")
        }

        scm {
            connection.set("scm:git:git://github.com/hollow-cube/luau-java.git")
            developerConnection.set("scm:git:git@github.com:hollow-cube/luau-java.git")
            url.set("https://github.com/hollow-cube/luau-java")
            tag.set(System.getenv("TAG_VERSION") ?: "HEAD")
        }

        ciManagement {
            system.set("Github Actions")
            url.set("https://github.com/hollow-cube/luau-java/actions")
        }
    }
}

publishing.publications.create<MavenPublication>("luau") {
    groupId = project.group.toString()
    artifactId = "luau"
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)

        val commonPomConfig: Action<MavenPom> by project.extra
        commonPomConfig.execute(this)
    }
}

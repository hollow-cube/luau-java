import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

fun Project.configureMavenPom(pom: MavenPom) = with(pom) {
    description.set(rootProject.description)
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

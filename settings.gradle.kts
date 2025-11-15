//pluginManagement {
//    dependencies {
//        classpath("com.palantir.javaformat:gradle-palantir-java-format:2.82.0")
//    }
//}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

rootProject.name = "luau-java"

include("native")
include("example")

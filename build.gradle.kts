import io.github.krakowski.jextract.JextractTask
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem

plugins {
    `java-library`

    `maven-publish`
    signing
    alias(libs.plugins.nmcp)
    alias(libs.plugins.nmcp.aggregation)

    id("io.github.krakowski.jextract") version "0.5.0"
}

group = "dev.hollowcube"
version = System.getenv("TAG_VERSION") ?: "dev"
description = "Luau bindings for Java"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.annotations)

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).run {
        encoding = "UTF-8"
        addStringOption("source", "23")
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

allprojects {
    extra["cmake"] = findExecutable("cmake")

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

signing {
    isRequired = System.getenv("CI") != null

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
    useInMemoryPgpKeys(privateKey, keyPassphrase)

    sign(publishing.publications)
}

tasks.named<JextractTask>("jextract") {
    dependsOn(":native:prepNative")

    outputDir.set(project.layout.projectDirectory.dir("src/generated/java"))

    // Note: we use the header from the build directory here because
    // we need to catch "luau_ext_free" which is added by native/build.gradle.kts
    val nativeBuild = project(":native").layout.buildDirectory.dir("root/luau").get().asFile.absolutePath
    header("$nativeBuild/Compiler/include/luacode.h") {
        targetPackage = "net.hollowcube.luau.internal.compiler";
        definedMacros.addAll("LUA_API=\"extern \\\"C\\\"\"")
        functions.addAll("luau_compile", "luau_ext_free")
        structs.addAll("lua_CompileOptions")
    }

    val native = project(":native").layout.projectDirectory.dir("luau").asFile.absolutePath
    header("$native/VM/include/lua.h") {
        targetPackage = "net.hollowcube.luau.internal.vm"

        constants.addAll(
            "LUA_REGISTRYINDEX", "LUA_ENVIRONINDEX", "LUA_GLOBALSINDEX",
            "LUA_UTAG_LIMIT", "LUA_LUTAG_LIMIT", "LUA_MEMORY_CATEGORIES",
        )
        functions.addAll(
            "lua_newstate", "lua_close", "lua_newthread",
            "lua_mainthread", "lua_resetthread", "lua_isthreadreset",

            "lua_absindex", "lua_gettop", "lua_settop",
            "lua_pushvalue", "lua_remove", "lua_insert",
            "lua_replace", "lua_checkstack", "lua_rawcheckstack",
            "lua_xmove", "lua_xpush",

            "lua_isnumber", "lua_isstring", "lua_iscfunction",
            "lua_isLfunction", "lua_isuserdata", "lua_type",
            "lua_typename", "lua_equal", "lua_rawequal",
            "lua_lessthan", "lua_tovector", "lua_tonumberx",
            "lua_tointegerx", "lua_tounsignedx", "lua_toboolean",
            "lua_tolstring", "lua_namecallatom", "lua_objlen",
            "lua_tocfunction", "lua_tolightuserdata", "lua_tolightuserdatatagged",
            "lua_touserdata", "lua_touserdatatagged", "lua_userdatatag",
            "lua_lightuserdatatag", "lua_tothread", "lua_tobuffer",
            "lua_topointer",

            "lua_pushnil", "lua_pushnumber", "lua_pushinteger",
            "lua_pushunsigned", "lua_pushvector", "lua_pushlstring",
            "lua_pushcclosurek", "lua_pushboolean", "lua_pushthread",
            "lua_pushlightuserdatatagged", "lua_newuserdatatagged",
            "lua_newuserdatadtor", "lua_newbuffer",

            "lua_gettable", "lua_getfield", "lua_rawgetfield",
            "lua_rawget", "lua_rawgeti", "lua_createtable",
            "lua_setreadonly", "lua_getreadonly", "lua_setsafeenv",
            "lua_getmetatable", "lua_getfenv",

            "lua_settable", "lua_setfield", "lua_rawsetfield",
            "lua_rawset", "lua_rawseti", "lua_setmetatable",
            "lua_setfenv",

            "luau_load", "lua_call", "lua_pcall",

            "lua_yield", "lua_break", "lua_resume",
            "lua_resumeerror", "lua_status", "lua_isyieldable",
            "lua_getthreaddata", "lua_setthreaddata", "lua_costatus",
            "lua_debugtrace",

            "lua_gc", "lua_setmemcat", "lua_totalbytes",

            "lua_error", "lua_next", "lua_rawiter",
            "lua_concat", "lua_clock", "lua_clonefunction",
            "lua_cleartable",

            "lua_ref", "lua_unref",

            "lua_callbacks"
        )
        typedefs.addAll("lua_Alloc", "lua_CFunction")
        structs.addAll("lua_Callbacks")
    }

    header("$native/VM/include/lualib.h") {
        targetPackage = "net.hollowcube.luau.internal.vm"

        functions.addAll(
            "luaL_register",
            "luaL_getmetafield", "luaL_callmeta", "luaL_typeerrorL",
            "luaL_argerrorL", "luaL_newmetatable",

            "luaL_checklstring", "luaL_optlstring", "luaL_checknumber",
            "luaL_optnumber", "luaL_checkboolean", "luaL_optboolean",
            "luaL_checkinteger", "luaL_optinteger", "luaL_checkunsigned",
            "luaL_optunsigned", "luaL_checkvector", "luaL_optvector",
            "luaL_checkbuffer", "luaL_checkstack", "luaL_checktype",
            "luaL_checkany", "luaL_checkudata", "luaL_checkoption",

            "luaL_tolstring",

            "luaL_where",

            "luaopen_base", "luaopen_coroutine", "luaopen_table",
            "luaopen_os", "luaopen_string", "luaopen_bit32",
            "luaopen_buffer", "luaopen_utf8", "luaopen_math",
            "luaopen_debug", "luaL_openlibs",

            "luaL_newstate", "luaL_sandbox", "luaL_sandboxthread",
        )
        structs.addAll("luaL_Reg")
    }

    // Remaining functions to run through jextract
    //# LUA_API const char* lua_tostringatom(lua_State* L, int idx, int* atom);
    //# LUA_API void lua_pushstring(lua_State* L, const char* s);
    //# LUA_API const char* lua_pushvfstring(lua_State* L, const char* fmt, va_list argp);
    //# LUA_API LUA_PRINTF_ATTR(2, 3) const char* lua_pushfstringL(lua_State* L, const char* fmt, ...);
    //# LUA_API void lua_pushcclosurek(lua_State* L, lua_CFunction fn, const char* debugname, int nup, lua_Continuation cont);
    //# LUA_API uintptr_t lua_encodepointer(lua_State* L, uintptr_t p);
    //# LUA_API void lua_setuserdatatag(lua_State* L, int idx, int tag);
    //# typedef void (*lua_Destructor)(lua_State* L, void* userdata);
    //# LUA_API void lua_setuserdatadtor(lua_State* L, int tag, lua_Destructor dtor);
    //# LUA_API lua_Destructor lua_getuserdatadtor(lua_State* L, int tag);
    //# LUA_API void lua_setuserdatametatable(lua_State* L, int tag, int idx);
    //# LUA_API void lua_getuserdatametatable(lua_State* L, int tag);
    //# LUA_API void lua_setlightuserdataname(lua_State* L, int tag, const char* name);
    //# LUA_API const char* lua_getlightuserdataname(lua_State* L, int tag);
    //# LUA_API void lua_clonefunction(lua_State* L, int idx);
    //# LUA_API void lua_cleartable(lua_State* L, int idx);
}

fun findExecutable(name_: String): String? {
    var name = name_
    if (getCurrentOperatingSystem().isWindows) name += ".exe"
    val pathDirs = System.getenv("PATH").split(File.pathSeparator)
    val executable = pathDirs.map { File(it, name) }
        .find { it.exists() && it.canExecute() }
    return executable?.absolutePath
}


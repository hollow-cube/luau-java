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
    extra["cmake"] = findExecutable("cmake")
    extra["jextract"] = findExecutable("jextract")

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

task<JExtractTask>("jextractLuacode") {
    header = file("native/luau/Compiler/include/luacode.h")
    targetPackage = "net.hollowcube.luau.internal.compiler"
    extraArgs = listOf(
        "--define-macro", "LUA_API=\"extern \\\"C\\\"\"",
        "--include-function", "luau_compile",
        "--include-struct", "lua_CompileOptions",
    )
}

task<JExtractTask>("jextractLua") {
    header = file("native/luau/VM/include/lua.h")
    targetPackage = "net.hollowcube.luau.internal.vm"
    extraArgs = listOf(
        "--include-constant", "LUA_REGISTRYINDEX",
        "--include-constant", "LUA_ENVIRONINDEX",
        "--include-constant", "LUA_GLOBALSINDEX",

        "--include-constant", "LUA_UTAG_LIMIT",
        "--include-constant", "LUA_LUTAG_LIMIT",
        "--include-constant", "LUA_MEMORY_CATEGORIES",

        "--include-function", "lua_newstate",
        "--include-function", "lua_close",
        "--include-function", "lua_newthread",
        "--include-function", "lua_mainthread",
        "--include-function", "lua_resetthread",
        "--include-function", "lua_isthreadreset",

        "--include-function", "lua_absindex",
        "--include-function", "lua_gettop",
        "--include-function", "lua_settop",
        "--include-function", "lua_pushvalue",
        "--include-function", "lua_remove",
        "--include-function", "lua_insert",
        "--include-function", "lua_replace",
        "--include-function", "lua_checkstack",
        "--include-function", "lua_rawcheckstack",
        "--include-function", "lua_xmove",
        "--include-function", "lua_xpush",

        "--include-function", "lua_isnumber",
        "--include-function", "lua_isstring",
        "--include-function", "lua_iscfunction",
        "--include-function", "lua_isLfunction",
        "--include-function", "lua_isuserdata",
        "--include-function", "lua_type",
        "--include-function", "lua_typename",
        "--include-function", "lua_equal",
        "--include-function", "lua_rawequal",
        "--include-function", "lua_lessthan",
        "--include-function", "lua_tovector",
        "--include-function", "lua_tonumberx",
        "--include-function", "lua_tointegerx",
        "--include-function", "lua_tounsignedx",
        "--include-function", "lua_toboolean",
        "--include-function", "lua_tolstring",
        "--include-function", "lua_objlen",
        "--include-function", "lua_tocfunction",
        "--include-function", "lua_tolightuserdata",
        "--include-function", "lua_tolightuserdatatagged",
        "--include-function", "lua_touserdata",
        "--include-function", "lua_touserdatatagged",
        "--include-function", "lua_userdatatag",
        "--include-function", "lua_lightuserdatatag",
        "--include-function", "lua_tothread",
        "--include-function", "lua_tobuffer",
        "--include-function", "lua_topointer",

        "--include-function", "lua_pushnil",
        "--include-function", "lua_pushnumber",
        "--include-function", "lua_pushinteger",
        "--include-function", "lua_pushunsigned",
        "--include-function", "lua_pushvector",
        "--include-function", "lua_pushlstring",
        "--include-typedef", "lua_CFunction",
        "--include-function", "lua_pushcclosurek",
        "--include-function", "lua_pushboolean",
        "--include-function", "lua_pushthread",
        "--include-function", "lua_pushlightuserdatatagged",
        "--include-function", "lua_newuserdatatagged",
        "--include-function", "lua_newuserdatadtor",
        "--include-function", "lua_newbuffer",

        "--include-function", "lua_gettable",
        "--include-function", "lua_getfield",
        "--include-function", "lua_rawgetfield",
        "--include-function", "lua_rawget",
        "--include-function", "lua_rawgeti",
        "--include-function", "lua_createtable",
        "--include-function", "lua_setreadonly",
        "--include-function", "lua_getreadonly",
        "--include-function", "lua_setsafeenv",
        "--include-function", "lua_getmetatable",
        "--include-function", "lua_getfenv",

        "--include-function", "lua_settable",
        "--include-function", "lua_setfield",
        "--include-function", "lua_rawsetfield",
        "--include-function", "lua_rawset",
        "--include-function", "lua_rawseti",
        "--include-function", "lua_setmetatable",
        "--include-function", "lua_setfenv",

        "--include-function", "luau_load",
        "--include-function", "lua_call",
        "--include-function", "lua_pcall",

        "--include-function", "lua_yield",
        "--include-function", "lua_break",
        "--include-function", "lua_resume",
        "--include-function", "lua_resumeerror",
        "--include-function", "lua_status",
        "--include-function", "lua_isyieldable",
        "--include-function", "lua_getthreaddata",
        "--include-function", "lua_setthreaddata",
        "--include-function", "lua_costatus",

        "--include-function", "lua_gc",
        "--include-function", "lua_setmemcat",
        "--include-function", "lua_totalbytes",

        "--include-function", "lua_error",
        "--include-function", "lua_next",
        "--include-function", "lua_rawiter",
        "--include-function", "lua_concat",
        "--include-function", "lua_clock",
        "--include-function", "lua_clonefunction",
        "--include-function", "lua_cleartable",

        "--include-function", "lua_ref",
        "--include-function", "lua_unref",

        "--include-struct", "lua_Callbacks",
        "--include-function", "lua_callbacks",
    )
}

task<JExtractTask>("jextractLualibs") {
    header = file("native/luau/VM/include/lualib.h")
    targetPackage = "net.hollowcube.luau.internal.vm"
    extraArgs = listOf(
        "--include-struct", "luaL_Reg",
        "--include-function", "luaL_register",
        "--include-function", "luaL_getmetafield",
        "--include-function", "luaL_callmeta",
        "--include-function", "luaL_typeerrorL",
        "--include-function", "luaL_argerrorL",
        "--include-function", "luaL_newmetatable",

        "--include-function", "luaL_checklstring",
        "--include-function", "luaL_optlstring",
        "--include-function", "luaL_checknumber",
        "--include-function", "luaL_optnumber",
        "--include-function", "luaL_checkboolean",
        "--include-function", "luaL_optboolean",
        "--include-function", "luaL_checkinteger",
        "--include-function", "luaL_optinteger",
        "--include-function", "luaL_checkunsigned",
        "--include-function", "luaL_optunsigned",
        "--include-function", "luaL_checkvector",
        "--include-function", "luaL_optvector",
        "--include-function", "luaL_checkbuffer",
        "--include-function", "luaL_checkstack",
        "--include-function", "luaL_checktype",
        "--include-function", "luaL_checkany",
        "--include-function", "luaL_checkudata",
        "--include-function", "luaL_checkoption",

        "--include-function", "luaL_where",

        "--include-function", "luaopen_base",
        "--include-function", "luaopen_coroutine",
        "--include-function", "luaopen_table",
        "--include-function", "luaopen_os",
        "--include-function", "luaopen_string",
        "--include-function", "luaopen_bit32",
        "--include-function", "luaopen_buffer",
        "--include-function", "luaopen_utf8",
        "--include-function", "luaopen_math",
        "--include-function", "luaopen_debug",
        "--include-function", "luaL_openlibs",

        "--include-function", "luaL_newstate",

        "--include-function", "luaL_sandbox",
        "--include-function", "luaL_sandboxthread",
    )
}

// Remaining functions to run through jextract
//# LUA_API const char* lua_tostringatom(lua_State* L, int idx, int* atom);
//# LUA_API const char* lua_namecallatom(lua_State* L, int* atom);
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

task("jextract") {
    dependsOn("jextractLuacode")
    dependsOn("jextractLua")
    dependsOn("jextractLualibs")
}

fun findExecutable(name: String): String? {
    val pathDirs = System.getenv("PATH").split(File.pathSeparator)
    val executable = pathDirs.map { File(it, name) }
        .find { it.exists() && it.canExecute() }
    return executable?.absolutePath
}

@CacheableTask
abstract class JExtractTask : Exec() {

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
        workingDir = this.project.layout.projectDirectory.asFile

        val jextract: String? by project.extra
        commandLine(
            listOf(
                jextract ?: throw IllegalStateException("jextract not found in PATH"),
                header.get(), "--output", "src/generated/java",
                "--target-package", targetPackage.get()
            ) + extraArgs.get()
        )
        super.exec()
    }
}

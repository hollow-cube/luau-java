import io.github.krakowski.jextract.JextractTask

plugins {
    alias(libs.plugins.jextract)
}

tasks.named<JextractTask>("jextract") {
    outputDir.set(project.layout.projectDirectory.dir("src/generated/java"))

    // Note: we use the header from the build directory here because
    // we need to catch "luau_ext_free" which is added by native/build.gradle.kts
    val nativeBuild = project(":native").projectDir.resolve("luau")
    header("$nativeBuild/Compiler/include/luacode.h") {
        targetPackage = "net.hollowcube.luau.internal.compiler";

        functions.addAll(
            "luau_compile", "luau_ext_free",
            "luau_set_compile_constant_nil",
            "luau_set_compile_constant_boolean",
            "luau_set_compile_constant_number",
            "luau_set_compile_constant_vector",
            "luau_set_compile_constant_string",
        )
        typedefs.addAll(
            "lua_LibraryMemberTypeCallback",
            "lua_LibraryMemberConstantCallback",
        )
        structs.addAll("lua_CompileOptions")
    }

    header("$nativeBuild/VM/include/lua.h") {
        targetPackage = "net.hollowcube.luau.internal.vm"

        constants.addAll(
            "LUA_REGISTRYINDEX", "LUA_ENVIRONINDEX", "LUA_GLOBALSINDEX",
            "LUA_UTAG_LIMIT", "LUA_LUTAG_LIMIT", "LUA_MEMORY_CATEGORIES",
        )
        functions.addAll(
            "lua_close", "lua_mainthread", "lua_isthreadreset",
            "lua_absindex", "lua_gettop", "lua_settop", "lua_pushvalue",
            "lua_remove", "lua_insert", "lua_replace", "lua_checkstack",
            "lua_rawcheckstack", "lua_isnumber", "lua_isstring", "lua_iscfunction",
            "lua_isLfunction", "lua_isuserdata", "lua_rawequal",
            "lua_tonumberx", "lua_tointegerx", "lua_tounsignedx", "lua_tovector",
            "lua_toboolean", "lua_tocfunction", "lua_tolightuserdata",
            "lua_tolightuserdatatagged", "lua_touserdata", "lua_touserdatatagged",
            "lua_userdatatag", "lua_lightuserdatatag", "lua_tothread", "lua_tobuffer",
            "lua_topointer", "lua_pushnil", "lua_pushnumber", "lua_pushinteger",
            "lua_pushunsigned", "lua_pushvector", "lua_pushboolean", "lua_pushthread",
            "lua_pushlightuserdatatagged",
            "lua_setreadonly", "lua_getreadonly", "lua_getmetatable",
            "luau_load", "lua_resume", "lua_resumeerror", "lua_status",
            "lua_isyieldable", "lua_getthreaddata", "lua_setthreaddata", "lua_costatus",
            "lua_gc", "lua_setmemcat", "lua_totalbytes", "lua_rawiter",
            "lua_setuserdatatag", "lua_setuserdatadtor", "lua_getuserdatametatable",
            "lua_getlightuserdataname", "lua_ref", "lua_unref", "lua_callbacks",
            "lua_type", "lua_iscfunction", "lua_isFfunction",
            "lua_rawgetfield", "lua_rawget", "lua_rawgeti", "lua_getinfo",
            "lua_setuserdatametatable", "lua_pcall", "lua_call",
            "lua_tolstringatom", "lua_namecallatom",
        )
        typedefs.addAll("lua_Alloc", "lua_CFunction", "lua_Destructor", "lua_Continuation")
        structs.addAll("lua_Callbacks", "lua_Debug")
    }

    header("$nativeBuild/VM/include/lualib.h") {
        targetPackage = "net.hollowcube.luau.internal.vm"

        functions.addAll(
            "luaopen_base", "luaopen_coroutine",
            "luaopen_table", "luaopen_os", "luaopen_string",
            "luaopen_string", "luaopen_bit32", "luaopen_buffer",
            "luaopen_utf8", "luaopen_math", "luaopen_debug",
            "luaopen_vector", "luaL_openlibs", "luaL_optboolean",

            "luaL_sandbox", "luaL_sandboxthread",
        )
    }

    header("$nativeBuild/CodeGen/include/luacodegen.h") {
        targetPackage = "net.hollowcube.luau.internal.vm"

        functions.addAll(
            "luau_codegen_supported",
            "luau_codegen_create",
            "luau_codegen_compile",
        )
    }

    header("$nativeBuild/VM/include/luawrap.h") {
        targetPackage = "net.hollowcube.luau.internal.vm"

        functions.addAll(
            "luaW_getstatus",

            "luaW_newstate", "luaW_newthread", "luaW_resetthread",
            "lua_xmove", "lua_xpush", "luaW_equal",
            "luaW_tolstring", "luaW_tolstringatom", "luaW_namecallatom",
            "luaW_objlen", "luaW_pushlstring", "luaW_pushcclosurek",
            "luaW_newuserdatatagged", "luaW_newuserdatataggedwithmetatable",
            "luaW_newuserdatadtor", "luaW_newbuffer", "luaW_gettable",
            "luaW_getfield", "luaW_createtable", "luaW_settable",
            "luaW_setfield", "luaW_rawsetfield", "luaW_rawset",
            "luaW_rawseti", "luaW_setmetatable",
            "luaW_yield", "luaW_break", "luaW_next",
            "luaW_concat", "luaW_setlightuserdataname",
            "luaW_clonefunction", "luaW_cleartable", "luaW_clonetable",
            "luaLW_newmetatable", "luaLW_tolstring", "luaLW_findtable",
            "luaLW_typename", "luaLW_typeerror", "luaLW_argerror",
            "luaW_lessthan", "luaLW_checkboolean", "luaLW_checkudata"
        )
    }
}

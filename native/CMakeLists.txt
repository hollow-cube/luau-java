cmake_minimum_required(VERSION 3.28)
project(luaujava C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

find_package(JNI REQUIRED)

add_subdirectory(luau)

set(LUAU_DIR ${CMAKE_CURRENT_SOURCE_DIR}/luau)

#
# luau-java bridge
#
# These sources used to live in a fork of Luau. They are compiled directly into the
# Luau target (rather than a standalone library) because they need VM internals such
# as lua_jmpbuf and luaD_throw, which are hidden-visibility in a shared build.
#
target_sources(Luau.VM PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src/luaujava.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/luaujavac.cpp)
target_include_directories(Luau.VM PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)

add_library(globalref SHARED src/net_hollowcube_luau_util_GlobalRef.cc)
target_include_directories(globalref PRIVATE ${JNI_INCLUDE_DIRS})

#
# LUAU_BUILD_SHARED is on for its export macros (extern "C" + default visibility on
# LUA_API/LUACODE_API), but its ~10 library split does not survive linking, because the
# fast flag a library reads is often defined in a library above it:
#
#   Luau.Bytecode  -> Luau.VM        (LuauCostModel, LuauCallFeedback)
#   Luau.Bytecode  -> Luau.Compiler  (LuauEmitCallFeedback)
#   Luau.Bytecode  -> Luau.Ast       (DebugLuauUserDefinedClasses)
#   Luau.CodeGen   -> Luau.VM        internals, hidden visibility (LUAI_FUNC)
#   Luau.Inliner   -> Luau.VM        internals, hidden visibility (LUAI_FUNC)
#
# There is no cut through that graph, so everything goes into one library. (Not
# $<TARGET_OBJECTS:>, which would still leave the support libraries themselves to be
# linked - fatal on Windows, where a DLL may not have unresolved symbols.)
#
function(luaujava_absorb target)
    foreach (support ${ARGN})
        get_target_property(dir ${support} SOURCE_DIR)
        get_target_property(srcs ${support} SOURCES)
        foreach (src ${srcs})
            if (NOT IS_ABSOLUTE ${src})
                set(src ${dir}/${src})
            endif ()
            target_sources(${target} PRIVATE ${src})
        endforeach ()
        set_target_properties(${support} PROPERTIES EXCLUDE_FROM_ALL TRUE)
    endforeach ()
endfunction()

luaujava_absorb(Luau.VM
        Luau.Common Luau.Ast Luau.Bytecode Luau.Compiler Luau.CodeGen Luau.Inliner)

set_target_properties(Luau.VM PROPERTIES LINK_LIBRARIES "" INTERFACE_LINK_LIBRARIES "")
target_include_directories(Luau.VM PRIVATE
        ${LUAU_DIR}/Common/include
        ${LUAU_DIR}/Ast/include
        ${LUAU_DIR}/Bytecode/include
        ${LUAU_DIR}/Bytecode/src
        ${LUAU_DIR}/Compiler/include
        ${LUAU_DIR}/CodeGen/include
        ${LUAU_DIR}/CodeGen/src
        ${LUAU_DIR}/Inliner/include
        ${LUAU_DIR}/VM/src)

# Upstream sets these per-target, so absorbing the sources loses them. It never wires up
# LUAJITINLINER_API at all, which would leave luau_enable_jit_inliner a mangled C++ symbol.
if (MSVC)
    target_compile_definitions(Luau.VM PRIVATE
            "LUACODE_API=extern \"C\" __declspec(dllexport)"
            "LUACODEGEN_API=extern \"C\" __declspec(dllexport)"
            "LUAJITINLINER_API=extern \"C\" __declspec(dllexport)")
else ()
    target_compile_definitions(Luau.VM PRIVATE
            "LUACODE_API=extern \"C\" __attribute__((visibility(\"default\")))"
            "LUACODEGEN_API=extern \"C\" __attribute__((visibility(\"default\")))"
            "LUAJITINLINER_API=extern \"C\" __attribute__((visibility(\"default\")))")
endif ()

# Exclude other libs we dont use from build
foreach (unused Luau.CLI.lib Luau.Config Luau.Analysis Luau.Require isocline)
    set_target_properties(${unused} PROPERTIES EXCLUDE_FROM_ALL TRUE)
endforeach ()

function(luaujava_ship target name)
    set_target_properties(${target} PROPERTIES OUTPUT_NAME ${name}
            RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
            LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
endfunction()

luaujava_ship(Luau.VM luau)
luaujava_ship(globalref globalref)

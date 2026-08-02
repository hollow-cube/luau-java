cmake_minimum_required(VERSION 3.28)
project(luaujava C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

find_package(JNI REQUIRED)

# Shipped libraries are all extracted to the same directory (see NativeLibraryLoader),
# so resolve libcompiler's dependency on libvm relative to the loading library.
set(CMAKE_BUILD_WITH_INSTALL_RPATH ON)
set(CMAKE_INSTALL_NAME_DIR "@rpath")
if (APPLE)
    set(CMAKE_INSTALL_RPATH "@loader_path")
else ()
    set(CMAKE_INSTALL_RPATH "$ORIGIN")
endif ()

add_subdirectory(luau)

set(LUAU_DIR ${CMAKE_CURRENT_SOURCE_DIR}/luau)

#
# luau-java bridge
#
# These sources used to live in a fork of Luau. They are compiled directly into the
# upstream targets (rather than a standalone library) because they need VM internals
# such as lua_jmpbuf and luaD_throw, which are hidden-visibility in a shared build.
#
target_sources(Luau.VM PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/luaujava.cpp)
target_include_directories(Luau.VM PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${LUAU_DIR}/VM/src)

target_sources(Luau.Compiler PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/luaujavac.cpp)
target_include_directories(Luau.Compiler PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)

add_library(globalref SHARED src/net_hollowcube_luau_util_GlobalRef.cc)
target_include_directories(globalref PRIVATE ${JNI_INCLUDE_DIRS})

#
# LUAU_BUILD_SHARED is on for its export macros (extern "C" + default visibility on
# LUA_API/LUACODE_API), but its library split does not work for us: it produces ~10
# dylibs, and Luau.Bytecode reads fast flags that Luau.Compiler defines while
# Luau.Compiler links Luau.Bytecode. ld64 rejects the resulting undefined symbols and
# dyld cannot resolve them at load time either, since Bytecode is always loaded first.
#
# So compile the support libraries' sources into the two libraries we actually ship and
# drop the link edges. (Not $<TARGET_OBJECTS:>, which would still leave the support
# libraries themselves to be linked - fatal on Windows, where a DLL may not have
# unresolved symbols.)
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

# Luau.CodeGen is the native codegen (JIT) backend. It reaches into hidden-visibility VM
# internals, so it can only be absorbed into Luau.VM, never linked against it.
luaujava_absorb(Luau.VM Luau.Common Luau.CodeGen)
luaujava_absorb(Luau.Compiler Luau.Common Luau.Ast Luau.Bytecode)

set_target_properties(Luau.VM Luau.Compiler PROPERTIES
        LINK_LIBRARIES "" INTERFACE_LINK_LIBRARIES "")
target_include_directories(Luau.VM PRIVATE
        ${LUAU_DIR}/Common/include
        ${LUAU_DIR}/CodeGen/include
        ${LUAU_DIR}/CodeGen/src)
if (MSVC)
    target_compile_definitions(Luau.VM PRIVATE "LUACODEGEN_API=extern \"C\" __declspec(dllexport)")
else ()
    target_compile_definitions(Luau.VM PRIVATE "LUACODEGEN_API=extern \"C\" __attribute__((visibility(\"default\")))")
endif ()
target_include_directories(Luau.Compiler PRIVATE
        ${LUAU_DIR}/Common/include
        ${LUAU_DIR}/Ast/include
        ${LUAU_DIR}/Bytecode/include
        ${LUAU_DIR}/Bytecode/src)

# Luau.Bytecode reads two fast flags that the VM defines (LuauCostModel, LuauCallFeedback),
# so the compiler library is not standalone. Load "vm" before "compiler" on the Java side.
target_link_libraries(Luau.Compiler PRIVATE Luau.VM)

# Exclude other libs we dont use from build
foreach (unused Luau.CLI.lib Luau.Config Luau.Analysis Luau.Require Luau.Inliner isocline)
    set_target_properties(${unused} PROPERTIES EXCLUDE_FROM_ALL TRUE)
endforeach ()

function(luaujava_ship target name)
    set_target_properties(${target} PROPERTIES OUTPUT_NAME ${name}
            RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
            LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
endfunction()

luaujava_ship(Luau.VM vm)
luaujava_ship(Luau.Compiler compiler)
luaujava_ship(globalref globalref)

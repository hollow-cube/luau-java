cmake_minimum_required(VERSION 3.28)
project(luaujava C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

#if(MSVC)
#    add_compile_definitions(LUA_API=__declspec(dllexport))
#endif()

find_package(JNI REQUIRED)
add_subdirectory(luau)

add_library(globalref SHARED src/net_hollowcube_luau_util_GlobalRef.cc)
target_include_directories(globalref PRIVATE ${JNI_INCLUDE_DIRS})

set_target_properties(Luau.Compiler PROPERTIES OUTPUT_NAME compiler
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set_target_properties(Luau.VM PROPERTIES OUTPUT_NAME vm
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set_target_properties(globalref PROPERTIES OUTPUT_NAME globalref
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

cmake_minimum_required(VERSION 3.28)
project(luaujava C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

#set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(LUAU_EXTERN_C ON)

find_package(JNI REQUIRED)
add_subdirectory(luau)

#set_target_properties(Luau.Compiler PROPERTIES
#        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
#        ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
#)

add_library(LuauJava SHARED src/main/c/net_hollowcube_luau_util_JNIRefTest.c)
target_include_directories(LuauJava PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(LuauJava PUBLIC Luau.Compiler)

set_target_properties(LuauJava PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
        ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)

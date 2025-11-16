cmake_minimum_required(VERSION 3.28)
project(luaujava C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

find_package(JNI REQUIRED)
add_subdirectory(luau)

# Build globalref as a static library first
add_library(globalref STATIC src/net_hollowcube_luau_util_GlobalRef.cc)
target_include_directories(globalref PRIVATE ${JNI_INCLUDE_DIRS})

# Create a dummy source file for the combined library
file(WRITE ${CMAKE_BINARY_DIR}/libluau_dummy.cpp "// Dummy file for combined library\n")

# Create the combined shared library
add_library(libluau SHARED ${CMAKE_BINARY_DIR}/libluau_dummy.cpp)

# Force whole archive linking to include all symbols
if(MSVC)
    target_link_options(libluau PRIVATE
            /WHOLEARCHIVE:Luau.Compiler
            /WHOLEARCHIVE:Luau.VM
            /WHOLEARCHIVE:Luau.CodeGen
            /WHOLEARCHIVE:Luau.Require
            /WHOLEARCHIVE:globalref
    )
    target_link_libraries(libluau PUBLIC
            Luau.Compiler Luau.VM Luau.CodeGen Luau.Require globalref
    )
elseif(APPLE)
    target_link_options(libluau PRIVATE
            "LINKER:-force_load,$<TARGET_FILE:Luau.Compiler>"
            "LINKER:-force_load,$<TARGET_FILE:Luau.VM>"
            "LINKER:-force_load,$<TARGET_FILE:Luau.CodeGen>"
            "LINKER:-force_load,$<TARGET_FILE:Luau.Require>"
            "LINKER:-force_load,$<TARGET_FILE:globalref>"
    )
    target_link_libraries(libluau PUBLIC
            Luau.Compiler Luau.VM Luau.CodeGen Luau.Require globalref
    )
else() # Linux/Unix
    target_link_libraries(libluau PUBLIC
            -Wl,--whole-archive
            Luau.Compiler
            Luau.VM
            Luau.CodeGen
            Luau.Require
            globalref
            -Wl,--no-whole-archive
    )
endif()

# Link transitive dependencies normally
target_link_libraries(libluau PRIVATE
        Luau.Common
        Luau.Ast
        Luau.Config
        Luau.Analysis
        Luau.EqSat
        ${JNI_LIBRARIES}
)

target_include_directories(libluau PRIVATE ${JNI_INCLUDE_DIRS})

# Set output properties
set_target_properties(libluau PROPERTIES
        OUTPUT_NAME luau
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)
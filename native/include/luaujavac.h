// luau-java additions to the compiler API.
//
// Compiled into the Luau target by native/CMakeLists.txt so the luau submodule can
// track upstream unmodified.
#pragma once

#include "luacode.h"

// Frees a bytecode buffer returned by luau_compile from within the library. Calling
// free() from the JVM crashes on Windows because the DLL and the JVM use different
// CRT heaps.
LUACODE_API void luau_ext_free(char* bytecode);

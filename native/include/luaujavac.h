// luau-java additions to the Luau.Compiler library.
//
// Compiled into the Luau.Compiler target by native/CMakeLists.txt so the luau
// submodule can track upstream unmodified.
#pragma once

#include "luacode.h"

// Frees a bytecode buffer returned by luau_compile from within the compiler library.
// Calling free() from the JVM crashes on Windows because the compiler DLL and the JVM
// use different CRT heaps.
LUACODE_API void luau_ext_free(char* bytecode);

// Enable all stable `Luau*` fast flags. The compiler library owns its own flag list,
// so this must be called in addition to luaW_setflagsdefault (see luaujava.h).
LUACODE_API void luauC_setflagsdefault(void);

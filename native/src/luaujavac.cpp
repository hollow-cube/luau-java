// See luaujavac.h. Compiled into the Luau target by native/CMakeLists.txt.
#include <cstdlib>

#include "luaujavac.h"

LUACODE_API void luau_ext_free(char* bytecode)
{
    free(bytecode);
}

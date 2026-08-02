// See luaujavac.h. Compiled into the Luau.Compiler target by native/CMakeLists.txt.
#include <cstdlib>
#include <cstring>

#include "luaujavac.h"

#include "Luau/Common.h"
#include "Luau/ExperimentalFlags.h"

LUACODE_API void luau_ext_free(char* bytecode)
{
    free(bytecode);
}

LUACODE_API void luauC_setflagsdefault(void)
{
    for (Luau::FValue<bool>* flag = Luau::FValue<bool>::list; flag; flag = flag->next)
        if (strncmp(flag->name, "Luau", 4) == 0 && !Luau::isAnalysisFlagExperimental(flag->name))
            flag->value = true;
}

// luau-java bridge API.
//
// Two things live here:
//  * `luaW_*` barrier wrappers around every lua_* / luaL_* API which may raise. Luau
//    is built with LUA_USE_LONGJMP (implied by LUAU_EXTERN_C), so a raise inside an
//    API called directly from Java would longjmp over the FFM downcall stub. Each
//    wrapper installs its own jump target; Java reads `luaW_getstatus` afterwards and
//    rethrows on the Java side.
//  * `luaW_dispatch`, a native trampoline registered as the lua_CFunction for every
//    Java closure. Java callbacks may not raise directly (that would longjmp over the
//    FFM upcall stub), so they return a sentinel (-100 - status) and the trampoline
//    raises once the Java frame is gone.
#pragma once

#include "lua.h"
#include "lualib.h"

// Enable all stable `Luau*` fast flags, matching what Luau's own CLI does at startup.
LUA_API void luaW_setflagsdefault(void);

// Status of the last luaW_* call on this thread; LUA_OK (0) if it did not raise.
LUA_API int luaW_getstatus(lua_State* L);

LUA_API void luaW_assertconf_log(void);
LUA_API void luaW_assertconf_dump(void);

// Contents of lua_Callbacks::userdata. Allocated and owned by the Java binding;
// `javadata` is opaque to native code.
struct luaW_userdata
{
    void* javadata;
    int (*preempt)(lua_State* L, int gc); // nullable
};

// Attempts to compile the function at idx, returning the CodeGenCompilationResult
LUA_API int luaW_codegen_compile(lua_State* L, int idx);

// True if the function at idx has been re-optimized by the runtime inliner. Diagnostic
// only - inlining is otherwise transparent, and this reaches into VM internals.
LUA_API int luaW_isinlined(lua_State* L, int idx);

//
// BEGIN LUA WRAPPERS
//

LUA_API lua_State* luaW_newstate(lua_Alloc f /* nullable */);
// lua_close is not wrapped
LUA_API lua_State* luaW_newthread(lua_State* L);
// lua_mainthread is not wrapped
LUA_API void luaW_resetthread(lua_State* L);
// lua_isthreadreset is not wrapped

// lua_absindex is not wrapped
// lua_gettop is not wrapped
// lua_settop is not wrapped
// lua_pushvalue is not wrapped
// lua_remove is not wrapped
// lua_insert is not wrapped
// lua_replace is not wrapped
// lua_checkstack is not wrapped
// lua_rawcheckstack is not wrapped
// lua_xmove is not wrapped
// lua_xpush is not wrapped

// lua_isnumber
// lua_isstring
// lua_iscfunction
// lua_isLfunction
// lua_isuserdata
// lua_type
// lua_typename is unused (luaL_typename is preferred)
LUA_API int luaW_equal(lua_State* L, int idx1, int idx2);
// lua_rawequal
LUA_API int luaW_lessthan(lua_State* L, int idx1, int idx2);
// lua_tonumberx
// lua_tointegerx
// lua_tounsignedx
// lua_tovector
// lua_toboolean
LUA_API const char* luaW_tolstring(lua_State* L, int idx, size_t* len);
// lua_tostringatom is unused (only lstrings)
LUA_API int luaW_objlen(lua_State* L, int idx);
// lua_tocfunction
// lua_tolightuserdata
// lua_tolightuserdatatagged
// lua_touserdata
// lua_touserdatatagged
// lua_userdatatag
// lua_lightuserdatatag
// lua_tothread
// lua_tobuffer
// lua_topointer

// lua_pushnil
// lua_pushnumber
// lua_pushinteger
// lua_pushunsigned
// lua_pushvector
LUA_API void luaW_pushlstring(lua_State* L, const char* s, size_t l);
// lua_pushstring is unused (only lstrings)
// lua_pushvfstring is unused (only lstrings)
// lua_pushfstringL is unused (use java formatting)

// Pushes a Java closure. `fn` and `cont` are FFM upcall stubs; they are stored as
// hidden upvalues 1 and 2 and the real lua_CFunction is luaW_dispatch. User upvalues
// therefore start at index 3 - see LuaState.upvalueIndex on the Java side.
LUA_API void luaW_pushcclosurek(lua_State* L, lua_CFunction fn, const char* debugname, int nup, lua_Continuation cont);
// lua_pushboolean
// lua_pushthread
// lua_pushlightuserdatatagged
LUA_API void* luaW_newuserdatatagged(lua_State* L, size_t sz, int tag);
LUA_API void* luaW_newuserdatataggedwithmetatable(lua_State* L, size_t sz, int tag); // metatable fetched with lua_getuserdatametatable
LUA_API void* luaW_newuserdatadtor(lua_State* L, size_t sz, void (*dtor)(void*));
LUA_API void* luaW_newbuffer(lua_State* L, size_t sz);

LUA_API int luaW_gettable(lua_State* L, int idx);
LUA_API int luaW_getfield(lua_State* L, int idx, const char* k);
// lua_rawgetfield
// lua_rawget
// lua_rawgeti
// lua_rawgetptagged
LUA_API void luaW_createtable(lua_State* L, int narr, int nrec);
// lua_setreadonly
// lua_getreadonly
// lua_setsafeenv
// lua_getmetatable
// lua_getfenv

LUA_API void luaW_settable(lua_State* L, int idx);
LUA_API void luaW_setfield(lua_State* L, int idx, const char* k);
LUA_API void luaW_rawsetfield(lua_State* L, int idx, const char* k);
LUA_API void luaW_rawset(lua_State* L, int idx);
LUA_API void luaW_rawseti(lua_State* L, int idx, int n);
LUA_API void luaW_rawsetptagged(lua_State* L, int idx, void* p, int tag);
LUA_API int luaW_setmetatable(lua_State* L, int objindex);
// lua_setfenv

// luau_load
// lua_call is unused
// lua_pcall
// lua_cpcall is unused

LUA_API int luaW_yield(lua_State* L, int nresults); // TODO: this only throws if there is a c boundary on the stack. We can check this with isyieldable from java and handle this error there.
LUA_API int luaW_break(lua_State* L); // TODO: this only throws if there is a c boundary on the stack. We can check this with isyieldable from java and handle this error there.
// lua_resume
// TODO: resumeerror might cause a longjmp unexpectedly on another thread? no idea need to investigate.
// lua_resumeerror
// lua_status
// lua_isyieldable
// lua_getthreaddata
// lua_setthreaddata
// lua_costatus

// lua_gc
// lua_setmemcat
// lua_totalbytes

// lua_error is unused (handled on java side)
LUA_API int luaW_next(lua_State* L, int idx);
// lua_rawiter;
LUA_API void luaW_concat(lua_State* L, int n);
// lua_encodepointer is unused
// lua_clock is unused

// lua_setuserdatatag
// lua_setuserdatadtor
// lua_getuserdatadtor
// lua_setuserdatametatable
// lua_getuserdatametatable
LUA_API void luaW_setlightuserdataname(lua_State* L, int tag, const char* name);
// lua_getlightuserdataname

LUA_API void luaW_clonefunction(lua_State* L, int idx);
LUA_API void luaW_cleartable(lua_State* L, int idx);
LUA_API void luaW_clonetable(lua_State* L, int idx);
// lua_getallocf is unused

// lua_ref
// lua_unref

// lua_callbacks

//
// BEGIN LUALIB WRAPPERS
//

LUA_API int luaLW_newmetatable(lua_State* L, const char* tname);
LUA_API const char* luaLW_tolstring(lua_State* L, int idx, size_t* len);
LUA_API const char* luaLW_findtable(lua_State* L, int idx, const char* fname, int szhint);

LUA_API const char* luaLW_typename(lua_State* L, int idx);

LUA_API void luaLW_typeerror(lua_State* L, int narg, const char* tname);
LUA_API void luaLW_argerror(lua_State* L, int narg, const char* extramsg);

LUA_API int luaLW_checkboolean(lua_State* L, int narg);

LUA_API void* luaLW_checkudata(lua_State* L, int ud, const char* tname);
LUA_API void* luaLW_checkudatatagged(lua_State* L, int ud, int tag);

// luaL_sandbox is not wrapped (should not fail in practical conditions)
// luaL_sandboxthread is not wrapped (should not fail in practical conditions)

//
// BEGIN EXTENSION HELPERS
//

// True if the frame at `level` is a Java closure pushed by luaW_pushcclosurek.
// Used to split the interleaved Java/Lua stack trace; replaces the fork's `isC == 2`
// marker and the "J" value it reported through lua_Debug::what.
LUA_API int luaW_isjavaframe(lua_State* L, int level);

// Interrupt handler which forwards to luaW_userdata::preempt and performs the
// resulting yield/error natively, once the Java frame is off the stack.
LUA_API void luaW_interrupt_preempt_handler(lua_State* L, int gc);

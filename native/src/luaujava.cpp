// See luaujava.h. Compiled into the Luau.VM target by native/CMakeLists.txt so it can
// reach VM internals (lua_jmpbuf, luaD_throw) without patching the luau submodule.
#include <csetjmp>
#include <cstdio>
#include <cstring>

#include "luaujava.h"

#include "Luau/CodeGen.h"
#include "Luau/Common.h"
#include "Luau/ExperimentalFlags.h"

#include "lapi.h"
#include "ldo.h"
#include "lobject.h"
#include "lstate.h"

// use POSIX versions of setjmp/longjmp if possible: they don't save/restore signal mask and are therefore faster
#if defined(__linux__) || defined(__APPLE__)
#define LUAU_SETJMP(buf) _setjmp(buf)
#define LUAU_LONGJMP(buf, code) _longjmp(buf, code)
#else
#define LUAU_SETJMP(buf) setjmp(buf)
#define LUAU_LONGJMP(buf, code) longjmp(buf, code)
#endif

// Mirrors ldo.cpp's definition; the VM only forward declares it in lstate.h.
struct lua_jmpbuf
{
    lua_jmpbuf* volatile prev;
    volatile int status;
    jmp_buf buf;
};

// Status of the innermost completed barrier. Threadlocal is fine since
// Java always reads it immediately and on the same thread.
static thread_local int luaW_lastStatus = 0;

inline void luaW_enter(lua_State* L, lua_jmpbuf* jb)
{
    jb->prev = L->global->errorjmp;
    jb->status = 0;
    L->global->errorjmp = jb;
}

inline void luaW_exit(lua_State* L, lua_jmpbuf* jb)
{
    L->global->errorjmp = jb->prev;
    luaW_lastStatus = jb->status;
}

LUA_API int luaW_getstatus(lua_State* L)
{
    return luaW_lastStatus;
}

LUA_API void luaW_setflagsdefault(void)
{
    for (Luau::FValue<bool>* flag = Luau::FValue<bool>::list; flag; flag = flag->next)
        if (strncmp(flag->name, "Luau", 4) == 0 && !Luau::isAnalysisFlagExperimental(flag->name))
            flag->value = true;
}

LUA_API void luaW_assertconf_log(void)
{
    Luau::assertHandler() = [](const char* expression, const char* file, int line, const char* function)
    {
        fprintf(stderr, "LUAU ASSERT FAILED: %s\n", expression);
        fprintf(stderr, "  at %s:%d in %s\n", file, line, function);

        return 0; // continue executing (probably will have corruption, but may give more info)
    };
}

LUA_API void luaW_assertconf_dump(void)
{
    Luau::assertHandler() = [](const char* expression, const char* file, int line, const char* function)
    {
        fprintf(stderr, "LUAU ASSERT FAILED: %s\n", expression);
        fprintf(stderr, "  at %s:%d in %s\n", file, line, function);

        // hard crash from here so the JVM will reconstruct the jvm side of the stacktrace.
        // not sure if there is a better way to achieve this, but it works :-)
        int* p = NULL;
        *p = 42;

        return 1;
    };
}

LUA_API int luaW_codegen_compile(lua_State* L, int idx)
{
    // Not wrapped: codegen reports failure through its return value, it never raises.
    if (!lua_isLfunction(L, idx))
        return int(Luau::CodeGen::CodeGenCompilationResult::Count);

    Luau::CodeGen::CompilationOptions options;
    return int(Luau::CodeGen::compile(L, idx, options).result);
}

LUA_API int luaW_isinlined(lua_State* L, int idx)
{
    if (!lua_isLfunction(L, idx))
        return 0;

    // The runtime inliner replaces a hot function's proto with a re-optimized copy and
    // leaves a backlink to the original, so this is how you tell that it has run.
    return clvalue(luaA_toobject(L, idx))->l.p->deoptimized != nullptr;
}

//
// JAVA CLOSURE DISPATCH
//
// Java callbacks return >= 0 for a normal return, -1 for a yield, and -100 - status
// to signal "raise this status". They cannot call luaD_throw themselves because the
// longjmp would cross the FFM upcall stub, so the trampoline does it here instead.
//

// Hidden upvalue slots; user upvalues start at LUAW_UPVAL_BASE + 1.
#define LUAW_UPVAL_FUNC 1
#define LUAW_UPVAL_CONT 2
#define LUAW_UPVAL_BASE 2

static int luaW_dispatch(lua_State* L)
{
    lua_CFunction fn = (lua_CFunction)lua_tolightuserdatatagged(L, lua_upvalueindex(LUAW_UPVAL_FUNC), 0);
    int n = fn(L);

    if (n < -100)
        luaD_throw(L, -(n + 100));

    return n;
}

static int luaW_dispatchcont(lua_State* L, int status)
{
    lua_Continuation cont = (lua_Continuation)lua_tolightuserdatatagged(L, lua_upvalueindex(LUAW_UPVAL_CONT), 0);
    int n = cont(L, status);

    if (n < -100)
        luaD_throw(L, -(n + 100));

    return n;
}

LUA_API int luaW_isjavaframe(lua_State* L, int level)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
    {
        lua_Debug ar = {};
        if (lua_getinfo(L, level, "f", &ar))
        {
            ret = lua_tocfunction(L, -1) == luaW_dispatch;
            lua_pop(L, 1);
        }
    }
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void luaW_interrupt_preempt_handler(lua_State* L, int gc)
{
    lua_Callbacks* callbacks = lua_callbacks(L);
    if (!callbacks)
        return;

    luaW_userdata* data = (luaW_userdata*)callbacks->userdata;
    if (!data || !data->preempt)
        return;

    int result = data->preempt(L, gc);
    if (result == -1)
        lua_yield(L, 0);
    else if (result < -100)
        luaD_throw(L, -(result + 100));
}

//
// BEGIN LUA WRAPPERS
//

LUA_API lua_State* luaW_newstate(lua_Alloc f)
{
    return f != nullptr ? lua_newstate(f, nullptr) : luaL_newstate();
}

LUA_API lua_State* luaW_newthread(lua_State* L)
{
    lua_jmpbuf jb;
    lua_State* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_newthread(L);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void luaW_resetthread(lua_State* L)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_resetthread(L);
    luaW_exit(L, &jb);
}

LUA_API int luaW_equal(lua_State* L, int idx1, int idx2)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_equal(L, idx1, idx2);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_lessthan(lua_State* L, int idx1, int idx2)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_lessthan(L, idx1, idx2);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API const char* luaW_tolstring(lua_State* L, int idx, size_t* len)
{
    lua_jmpbuf jb;
    const char* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_tolstring(L, idx, len);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_objlen(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_objlen(L, idx);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void luaW_pushlstring(lua_State* L, const char* s, size_t l)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_pushlstring(L, s, l);
    luaW_exit(L, &jb);
}

LUA_API void luaW_pushcclosurek(lua_State* L, lua_CFunction fn, const char* debugname, int nup, lua_Continuation cont)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
    {
        // Slide the hidden upvalues underneath the user upvalues so they occupy slots 1 and 2.
        // Stack goes [u1..un, cont, fn] -> [fn, u1..un, cont] -> [fn, cont, u1..un].
        lua_pushlightuserdatatagged(L, (void*)cont, 0);
        lua_pushlightuserdatatagged(L, (void*)fn, 0);
        lua_insert(L, -(nup + 2));
        lua_insert(L, -(nup + 1));

        lua_pushcclosurek(L, luaW_dispatch, debugname, nup + LUAW_UPVAL_BASE, cont != nullptr ? luaW_dispatchcont : nullptr);
    }
    luaW_exit(L, &jb);
}

LUA_API void* luaW_newuserdatatagged(lua_State* L, size_t sz, int tag)
{
    lua_jmpbuf jb;
    void* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_newuserdatatagged(L, sz, tag);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void* luaW_newuserdatataggedwithmetatable(lua_State* L, size_t sz, int tag)
{
    lua_jmpbuf jb;
    void* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_newuserdatataggedwithmetatable(L, sz, tag);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void* luaW_newuserdatadtor(lua_State* L, size_t sz, void (*dtor)(void*))
{
    lua_jmpbuf jb;
    void* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_newuserdatadtor(L, sz, dtor);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void* luaW_newbuffer(lua_State* L, size_t sz)
{
    lua_jmpbuf jb;
    void* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_newbuffer(L, sz);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_gettable(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_gettable(L, idx);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_getfield(lua_State* L, int idx, const char* k)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_getfield(L, idx, k);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void luaW_createtable(lua_State* L, int narr, int nrec)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_createtable(L, narr, nrec);
    luaW_exit(L, &jb);
}

LUA_API void luaW_settable(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_settable(L, idx);
    luaW_exit(L, &jb);
}

LUA_API void luaW_setfield(lua_State* L, int idx, const char* k)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_setfield(L, idx, k);
    luaW_exit(L, &jb);
}

LUA_API void luaW_rawsetfield(lua_State* L, int idx, const char* k)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_rawsetfield(L, idx, k);
    luaW_exit(L, &jb);
}

LUA_API void luaW_rawset(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_rawset(L, idx);
    luaW_exit(L, &jb);
}

LUA_API void luaW_rawseti(lua_State* L, int idx, int n)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_rawseti(L, idx, n);
    luaW_exit(L, &jb);
}

LUA_API void luaW_rawsetptagged(lua_State* L, int idx, void* p, int tag)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_rawsetptagged(L, idx, p, tag);
    luaW_exit(L, &jb);
}

LUA_API int luaW_setmetatable(lua_State* L, int objindex)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_setmetatable(L, objindex);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_yield(lua_State* L, int nresults)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_yield(L, nresults);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_break(lua_State* L)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_break(L);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_next(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = lua_next(L, idx);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void luaW_concat(lua_State* L, int n)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_concat(L, n);
    luaW_exit(L, &jb);
}

LUA_API void luaW_setlightuserdataname(lua_State* L, int tag, const char* name)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_setlightuserdataname(L, tag, name);
    luaW_exit(L, &jb);
}

LUA_API void luaW_clonefunction(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_clonefunction(L, idx);
    luaW_exit(L, &jb);
}

LUA_API void luaW_cleartable(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_cleartable(L, idx);
    luaW_exit(L, &jb);
}

LUA_API void luaW_clonetable(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        lua_clonetable(L, idx);
    luaW_exit(L, &jb);
}

//
// BEGIN LUALIB WRAPPERS
//

LUA_API int luaLW_newmetatable(lua_State* L, const char* tname)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_newmetatable(L, tname);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API const char* luaLW_tolstring(lua_State* L, int idx, size_t* len)
{
    lua_jmpbuf jb;
    const char* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_tolstring(L, idx, len);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API const char* luaLW_findtable(lua_State* L, int idx, const char* fname, int szhint)
{
    lua_jmpbuf jb;
    const char* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_findtable(L, idx, fname, szhint);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API const char* luaLW_typename(lua_State* L, int idx)
{
    lua_jmpbuf jb;
    const char* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_typename(L, idx);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void luaLW_typeerror(lua_State* L, int narg, const char* tname)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        luaL_typeerror(L, narg, tname);
    luaW_exit(L, &jb);
}

LUA_API void luaLW_argerror(lua_State* L, int narg, const char* extramsg)
{
    lua_jmpbuf jb;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        luaL_argerror(L, narg, extramsg);
    luaW_exit(L, &jb);
}

LUA_API int luaLW_checkboolean(lua_State* L, int narg)
{
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_checkboolean(L, narg);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void* luaLW_checkudata(lua_State* L, int ud, const char* tname)
{
    lua_jmpbuf jb;
    void* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_checkudata(L, ud, tname);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API void* luaLW_checkudatatagged(lua_State* L, int ud, int tag)
{
    lua_jmpbuf jb;
    void* ret = nullptr;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_checkudatatagged(L, ud, tag);
    luaW_exit(L, &jb);
    return ret;
}

LUA_API int luaW_pcallyieldable(lua_State* L, int nargs, int nresults, int errfunc)
{
    // luaL_pcallyieldable only asserts that the running closure has a continuation, and
    // would call through a null pointer in a release build, so check it for real.
    if (!iscfunction(L->ci->func) || !clvalue(L->ci->func)->c.cont)
        return LUAW_PCALLYIELDABLE_NOCONT;

    // Barriered like everything else, but for a subtler reason than usual: the callee
    // running to completion means luaL_pcallyieldable invokes our continuation before
    // returning, and a Java continuation which fails raises from luaW_dispatchcont.
    lua_jmpbuf jb;
    int ret = 0;
    luaW_enter(L, &jb);
    if (LUAU_SETJMP(jb.buf) == 0)
        ret = luaL_pcallyieldable(L, nargs, nresults, errfunc);
    luaW_exit(L, &jb);
    return ret;
}

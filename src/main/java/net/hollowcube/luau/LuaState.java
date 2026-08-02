package net.hollowcube.luau;

import net.hollowcube.luau.require.RequireResolver;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public sealed interface LuaState extends AutoCloseable permits LuaStateImpl {
    /// Maximum number of usable light userdata tags
    int LIGHT_USERDATA_TAG_LIMIT = LuaStateImpl.LIGHT_USERDATA_TAG_LIMIT;
    /// Maximum number of usable userdata tags
    int USERDATA_TAG_LIMIT = LuaStateImpl.USERDATA_TAG_LIMIT;
    /// Maximum number of memory categories for use in [#setMemCat(int)]
    int MEMORY_CATEGORIES = LuaStateImpl.MEMORY_CATEGORIES;

    /// The string atom value used to represent no atom in a [LuaCallbacks.UserAtom] callback.
    short NO_ATOM = (short) -1;

    int REGISTRY_INDEX = LuaStateImpl.REGISTRY_INDEX;

    /// Pseudo-index of the `i`th (1-based) upvalue of the running Java closure.
    ///
    /// Java closures are pushed as a native dispatch trampoline which reserves the first
    /// two upvalue slots for the Java function and continuation, so this is offset from
    /// the raw `lua_upvalueindex`.
    static int upvalueIndex(int i) {
        return LuaStateImpl.GLOBALS_INDEX - (i + LuaStateImpl.DISPATCH_UPVALUES);
    }

    /// Create a new lua state (main thread).
    ///
    /// Must be cleaned up with [#close()] when finished, or will leak resources.
    static LuaState newState() {
        return LuaStateImpl.newState(null);
    }

    /// Create a new lua state (main thread) with a custom memory allocator.
    ///
    /// Must be cleaned up with [#close()] when finished, or will leak resources.
    static LuaState newState(LuaAlloc allocator) {
        return LuaStateImpl.newState(((LuaStateImpl.AllocImpl) allocator).handle());
    }

    /// Create a new lua state (main thread) with a custom memory allocator.
    ///
    /// The allocator may be any MemorySegment which points to a native function (or FFM upcall)
    /// which implements the lua_Alloc interface (copied below). See [#newState(LuaAlloc)] for
    /// implementing the allocator as a Java upcall.
    ///
    /// ```c
    /// typedef void *(*lua_Alloc)(void *, void *, size_t, size_t)
    ///```
    ///
    /// Must be cleaned up with [#close()] when finished, or will leak resources.
    static LuaState newState(MemorySegment allocator) {
        return LuaStateImpl.newState(allocator);
    }

    // TODO: remove me, add supported apis for require to use
    @ApiStatus.Internal
    MemorySegment L();

    // TODO: remove me, add supported apis for require to use
    @ApiStatus.Internal
    static LuaState wrap(MemorySegment L) {
        return new LuaStateImpl(L);
    }

    /// Close this lua thread and clean up any managed resources.
    ///
    /// It is invalid to call any methods on this type after closing.
    @Override
    void close();

    /// Creates a new thread, pushes it on the stack, and returns a pointer to a
    /// [LuaState] that represents this new thread. The new thread returned by
    /// this function shares with the original thread its global environment,
    /// but has an independent execution stack.
    ///
    /// There is no need to explicit function to close a thread. Threads are
    /// subject to garbage collection, like any other Lua object.
    ///
    /// @see #sandboxThread()
    LuaState newThread();
    LuaState mainThread();
    void resetThread();
    boolean isThreadReset();

    /// Converts the acceptable index into an equivalent absolute index
    /// (that is, one that does not depend on the stack top).
    int absIndex(int index);
    /// Returns the index of the top element in the stack. Because indices
    /// start at 1, this result is equal to the number of elements in the
    /// stack; in particular, 0 means an empty stack.
    int top();
    void top(int index);
    void pop(int n);
    void pushValue(int index);
    void remove(int index);
    void insert(int index);
    void replace(int index);
    void xmove(LuaState to, int n);
    void xpush(LuaState to, int index);

    LuaType type(int index);
    /// Returns the computed type name of the value at index.
    ///
    /// This will include more specific type names, eg metatable __type. If you just want the
    /// name of the raw Lua type, use [#type(int)] and [LuaType#typeName()].
    String typeName(int index);
    boolean isNone(int index);
    boolean isNil(int index);
    boolean isNoneOrNil(int index);
    boolean isBoolean(int index);
    boolean isNumber(int index);
    boolean isVector(int index);
    boolean isString(int index);
    boolean isBuffer(int index);
    boolean isTable(int index);
    /// Returns true if the value at index is any type of function (C, Java, or Lua), false otherwise.
    boolean isFunction(int index);
    /// Returns true if the value at index is a C or Java function, false otherwise.
    boolean isNativeFunction(int index);
    boolean isLuaFunction(int index);
    boolean isUserData(int index);
    boolean isLightUserData(int index);
    boolean isThread(int index);
    /// Returns true if the value at index is a Luau `integer`, false otherwise.
    ///
    /// [LuaType#INTEGER] is a distinct type from [LuaType#NUMBER], and the VM does not
    /// coerce between them: [#isNumber(int)] is false for an integer, and [#toNumber(int)]
    /// returns 0. Use [#toInteger64(int)] to read one.
    boolean isInteger64(int index);

    boolean equal(int index1, int index2);
    boolean rawEqual(int index1, int index2);
    boolean lessThan(int index1, int index2);
    void concat(int n);
    int len(int index);

    boolean toBoolean(int index);
    /// Returns the number at index, or 0 if the value is not a number.
    double toNumber(int index);
    @Nullable Double toNumberOrNull(int index);
    /// Returns the integer at index, or 0 if the value is not a number.
    int toInteger(int index);
    @Nullable Integer toIntegerOrNull(int index);
    /// Returns the unsigned integer at index, or 0 if the value is not a number.
    long toUnsigned(int index);
    @Nullable Long toUnsignedOrNull(int index);
    /// Returns the Luau `integer` at index, or 0 if the value is not an integer.
    ///
    /// Note this is not [#toInteger(int)] with a wider result: that one reads a *number*
    /// and truncates it, and returns 0 for an integer. See [#isInteger64(int)].
    long toInteger64(int index);
    @Nullable Long toInteger64OrNull(int index);
    float @Nullable [] toVector(int index);
    /// Returns the string at index, or null if the value is not a string.
    ///
    /// Note: this behavior differs from lua_tolstring because it does NOT convert
    /// number values to string. Use [#unsafeToString(int)] for the exact lua behavior
    /// or [#toStringRepr(int)] to stringify any value (luaL_tolstring).
    @Nullable String toString(int index);
    @Nullable String unsafeToString(int index);
    String toStringRepr(int index);
    /// Returns the string atom if present, otherwise [#NO_ATOM].
    short toStringAtomRaw(int index);
    @Nullable LuaString toStringAtom(int index);
    /// May only be invoked from within a __namecall method, throws otherwise.
    ///
    /// Returns the string atom if present, otherwise [#NO_ATOM].
    short nameCallAtomRaw();
    /// May only be invoked from within a __namecall method, throws otherwise.
    LuaString nameCallAtom();
    long toLightUserData(int index);
    long toLightUserDataTagged(int index, int tag);
    int lightUserDataTag(int index);
    /// Unlike the lua api, will NOT return a light userdata object.
    @Nullable Object toUserData(int index);
    @Nullable Object toUserDataTagged(int index, int tag);
    /// Returns -1 if the value is not a userdata, or 0 for untagged userdata
    int userDataTag(int index);
    @Nullable LuaState toThread(int index);
    @Nullable ByteBuffer toBuffer(int index);

    void pushNil();
    void pushBoolean(boolean value);
    void pushNumber(double value);
    void pushInteger(int value);
    /// Pushes a Luau `integer`, the 64 bit integer type provided by the `integer` library.
    void pushInteger64(long value);
    void pushUnsigned(long value);
    void pushVector(float x, float y, float z);
    void pushVector(float[] value);
    void pushString(String value);
    void pushLightUserData(long value);
    void pushLightUserDataTagged(long value, int tag);
    void newUserData(Object value);
    void newUserDataTagged(Object value, int tag);
    /// metatable fetched with lua_getuserdatametatable
    void newUserDataTaggedWithMetatable(Object value, int tag);
    boolean pushThread(LuaState thread);
    ByteBuffer newBuffer(long size);
    void pushFunction(LuaFunc func);

    LuaType getTable(int index);
    LuaType getField(int index, String key);
    LuaType rawGetField(int index, String key);
    LuaType rawGet(int index);
    LuaType rawGetI(int index, int n);
    boolean getReadOnly(int index);

    void createTable(int narr, int nrec);
    void newTable();
    void setTable(int index);
    void setField(int index, String key);
    void rawSetField(int index, String key);
    void rawSet(int index);
    void rawSetI(int index, int n);
    void setReadOnly(int index, boolean enabled);

    boolean getMetaTable(int index);
    /// Pops a table from the stack and sets it as the new metatable for the
    /// value at the given index.
    void setMetaTable(int index);

    void load(String chunkname, byte[] data);

    void call(int nargs, int nresults);

    /// Yields the current thread, result _must_ be returned from the [LuaFunc] impl.
    @CheckReturnValue
    int yield(int nresults);
    // TODO: resume can throw, need to decide how to handle that
    LuaStatus resume(@Nullable LuaState from, int narg);
    // TODO: unsure what resumeerror does
    //    int resumeerror(lua_State* from);
    LuaStatus status();
    boolean isYieldable();
    LuaCoStatus costatus(LuaState co);

    // TODO: how to handle threaddata callbacks
    //    taking arbitrary java objects would be nice, but then we need to use the thread change callback to delete refs.
    //    void* lua_getthreaddata(lua_State* L);
    //    void lua_setthreaddata(lua_State* L, void* data);

    @Nullable Object getThreadData();
    void setThreadData(@Nullable Object data);

    /// Se comments on [LuaGcOp] for the meaning of the data parameter
    int gc(LuaGcOp op, int data);
    void setMemCat(int category);
    long totalBytes(int category);

    /// Throws, assumes that there is a value on the stack which becomes the thrown object.
    @Contract("-> fail")
    LuaError error();
    LuaError error(String message);
    @Contract("_, _ -> fail")
    LuaError error(@PrintFormat String message, @Nullable Object... args);
    @Contract("_, _ -> fail")
    void typeError(int narg, String tname);
    @Contract("_, _ -> fail")
    void argError(int narg, String extramsg);

    boolean checkStack(int sz);
    void checkStack(int sz, @Nullable String msg);

    boolean next(int index);
    int rawIter(int index, int iter);

    void setUserDataTag(int index, int tag);
    void setUserDataMetaTable(int tag);
    void getUserDataMetaTable(int tag);
    void setLightUserDataName(int tag, String name);
    @Nullable String getLightUserDataName(int tag);

    void cloneFunction(int index);
    void clearTable(int index);
    void cloneTable(int index);

    int ref(int index);
    /// Releases a reference. The id must not be used again: releasing recycles the slot
    /// into a free list rather than clearing it, so [#getRef(int)] on a released id pushes
    /// an internal link value rather than nil.
    void unref(int ref);
    LuaType getRef(int ref);

    /// Creates a weak reference to the value at index, which does not keep it alive.
    ///
    /// Unlike [#ref(int)], the referent may be collected at any point; [#getWeakRef(int)]
    /// then pushes nil. Use this for non-owning associations - a Java side identity cache
    /// for Lua tables, or a listener registry which should drop entries whose callback is
    /// no longer reachable from Lua. Reaching for it to break a Java/Lua reference cycle
    /// only works when the Lua value should not outlive its Java owner.
    ///
    /// Weak references live in a registry separate from [#ref(int)], so the returned id
    /// may only be passed to [#getWeakRef(int)] and [#weakUnref(int)].
    int weakRef(int index);
    /// Releases a weak reference. As with [#unref(int)], the id must not be used again.
    void weakUnref(int ref);
    /// Pushes the referent of a weak reference, or nil if it has been collected.
    LuaType getWeakRef(int ref);

    void setGlobal(String s);
    void getGlobal(String s);

    LuaCallbacks callbacks();

    // Arg checking

    void checkAny(int argNum);
    void checkType(int argNum, LuaType type);
    boolean checkBoolean(int argNum);
    boolean optBoolean(int argNum, boolean def);
    double checkNumber(int argNum);
    double optNumber(int argNum, double def);
    int checkInteger(int argNum);
    int optInteger(int argNum, int def);
    long checkInteger64(int argNum);
    long optInteger64(int argNum, long def);
    long checkUnsigned(int argNum);
    long optUnsigned(int argNum, long def);
    float[] checkVector(int argNum);
    float[] optVector(int argNum, float[] def);
    String checkString(int argNum);
    String optString(int argNum, String def);
    int checkOption(int argNum, @Nullable String def, List<String> lst);
    Object checkUserData(int argNum, String typeName);
    ByteBuffer checkBuffer(int argNum);

    //    LUALIB_API int luaL_getmetafield(lua_State* L, int obj, const char* e);
    //    LUALIB_API int luaL_callmeta(lua_State* L, int obj, const char* e);

    boolean newMetaTable(String typeName);
    LuaType getMetaTable(String typeName);

    // TODO: idk below
    //    LUALIB_API void luaL_where(lua_State* L, int lvl);
    //    LUALIB_API const char* luaL_findtable(lua_State* L, int index, const char* fname, int szhint);
    //    LUALIB_API int luaL_callyieldable(lua_State* L, int nargs, int nresults);

    @Nullable String findTable(int index, String fname, int sizeHint);

    void sandbox();
    void sandboxThread();

    /// Reads back the execution counts recorded by the Lua function at index.
    ///
    /// Coverage is collected by the compiler, not the VM: a chunk only records anything if
    /// it was compiled with a [net.hollowcube.luau.compiler.CoverageLevel] above
    /// [net.hollowcube.luau.compiler.CoverageLevel#NONE], which makes the compiler emit a
    /// counting instruction per statement (or per expression, at
    /// [net.hollowcube.luau.compiler.CoverageLevel#STATEMENT_AND_EXPRESSION]). Counts accumulate in the
    /// bytecode itself and are never reset, so this reports totals since the chunk was loaded.
    ///
    /// The result covers the given function and, recursively, every function nested inside
    /// it - one entry each, outermost first.
    ///
    /// @throws IllegalArgumentException if the value at index is not a Lua function
    List<LuaCoverage> getCoverage(int index);

    //region Codegen

    /// Whether native code generation is available for the current platform and build.
    ///
    /// Only x86-64 and arm64 are supported, and the process must be able to allocate
    /// executable memory, so this may be false at runtime even on a supported architecture.
    static boolean codegenSupported() {
        return LuaStateImpl.codegenSupported();
    }

    /// Enable native code generation for this state.
    ///
    /// Must be called on the main thread, before loading any code, and only if
    /// [#codegenSupported()] is true. Does nothing if codegen is already enabled.
    /// Functions still need to be compiled individually with [#codegenCompile(int)];
    /// this only installs the machinery.
    ///
    /// Effectively exclusive with the runtime inliner - see [#jitInlinerCreate()].
    void codegenCreate();

    /// Natively compile the Lua function at index, and all functions nested inside it.
    ///
    /// Requires a preceding [#codegenCreate()], otherwise this returns
    /// [LuaCodegenResult#NOT_INITIALIZED]. Compilation is best effort: a nested function
    /// which cannot be lowered is silently left to the interpreter without affecting the
    /// result, and native and interpreted functions are otherwise indistinguishable.
    ///
    /// Bytecode compiled with a [net.hollowcube.luau.compiler.TypeInfoLevel] above `NONE`
    /// produces better native code.
    ///
    /// @throws IllegalArgumentException if the value at index is not a Lua function
    LuaCodegenResult codegenCompile(int index);

    /// Enable the runtime bytecode inliner for this state.
    ///
    /// Unlike [#codegenCompile(int)] this is not driven by the caller: the VM counts call
    /// sites, and once one is monomorphic and hot the inliner splices the callee into a
    /// re-optimized copy of the caller's bytecode and constant folds the result. It emits
    /// bytecode rather than machine code, so it speeds up the interpreter and does not
    /// require [#codegenSupported()]. Inlining is transparent: stack traces and
    /// `debug.info` are unaffected.
    ///
    /// Effectively exclusive with native codegen, so pick one. Natively compiled functions
    /// do not record call feedback, so the inliner never fires for them; enabling both is
    /// harmless but only whichever functions were left to the interpreter can be inlined.
    ///
    /// Only some calls are candidates - the callee must have no upvalues, and the call
    /// must be a non-multret call inside a nested function. `return f(x)` is a multret
    /// call and never inlines; `local v = f(x) return v` does.
    void jitInlinerCreate();

    /// Disable the runtime bytecode inliner for this state.
    ///
    /// Functions already re-optimized stay re-optimized; this only stops further inlining.
    void jitInlinerDisable();

    //endregion

    /// Load the specified builtin libraries in the current state.
    ///
    /// Builtin libraries often have fastcalls so should always be preferred
    /// over a java implementation when possible.
    ///
    /// If not loading specific libraries, they should be disabled in the
    /// compiler so that calls are not replaced with builtins.
    /// (todo docs)
    ///
    /// @param libraries The libraries to load, or empty to load all libraries.
    void openLibs(BuilinLibrary... libraries);
    void register(Map<String, LuaFunc> library);
    void register(String name, Map<String, LuaFunc> library);

    //region Require

    /// Push the `require` function onto the stack using resolver.
    void pushRequire(RequireResolver resolver);
    /// Push the `require` function onto the stack using resolver and register it as
    /// the global variable "require" in the current state.
    void openRequire(RequireResolver resolver);

    /// Register the given exact alias path (starts with '@') as a module. After registration,
    /// the given result will always be immediately returned when the given path is required.
    ///
    /// Expects the table to be passed as an argument on the stack.
    void requireRegisterModule(String path);
    /// Register the given exact alias path (starts with '@') as a module. After registration,
    /// the given result will always be immediately returned when the given path is required.
    ///
    /// Expects the path and table to be passed as arguments on the stack.
    void requireRegisterModule();
    /// Clear a single entry with the given cacheKey from the `require` module cache.
    void requireClearCacheEntry(String cacheKey);
    /// Clear a single entry from the `require` module cache.
    ///
    /// Expects the entry cacheKey to be passed as an argument on the stack.
    void requireClearCacheEntry();
    /// Clears all entries from the `require` module cache.
    void requireClearCache();

    //endregion
}

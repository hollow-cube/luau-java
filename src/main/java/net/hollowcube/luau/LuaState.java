package net.hollowcube.luau;

import org.intellij.lang.annotations.PrintFormat;
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

    /// Close this lua thread and clean up any managed resources.
    ///
    /// It is invalid to call any methods on this type after closing.
    @Override void close();

    LuaState newThread();
    LuaState mainThread();
    void resetThread();
    boolean isThreadReset();

    int absIndex(int index);
    int getTop();
    void setTop(int index);
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
    void createTable(int narr, int nrec);
    void newTable();

    void setReadOnly(int index, boolean enabled);
    boolean getReadOnly(int index);

    void setTable(int index);
    void setField(int index, String key);
    void rawSetField(int index, String key);
    void rawSet(int index);
    void rawSetI(int index, int n);

    boolean getMetaTable(int index);
    void setMetaTable(int index);

    void load(String chunkname, byte[] data);
    void call(int nargs, int nresults);

    /// Yields the current thread, result _must_ be returned from the [LuaFunc] impl.
    @CheckReturnValue int yield(int nresults);
    // TODO: resume can throw, need to decide how to handle that
    //    int resume(LuaState from, int narg);
    // TODO: unsure what resumeerror does
    //    int resumeerror(lua_State* from);
    LuaStatus status();
    boolean isYieldable();
    LuaCoStatus costatus(LuaState co);

    // TODO: how to handle threaddata callbacks
    //    taking arbitrary java objects would be nice, but then we need to use the thread change callback to delete refs.
    //    void* lua_getthreaddata(lua_State* L);
    //    void lua_setthreaddata(lua_State* L, void* data);

    /// Se comments on [LuaGcOp] for the meaning of the data parameter
    int gc(LuaGcOp op, int data);
    void setMemCat(int category);
    long totalBytes(int category);

    /// Throws, assumes that there is a value on the stack which becomes the thrown object.
    @Contract("-> fail") void error();
    @Contract("_ -> fail") void error(String message);
    @Contract("_, _ -> fail") void error(@PrintFormat String message, Object... args);
    @Contract("_, _ -> fail") void typeError(int narg, String tname);
    @Contract("_, _ -> fail") void argError(int narg, String extramsg);
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
    void unRef(int ref);
    LuaType getRef(int ref);

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
    long checkUnsigned(int argNum);
    long optUnsigned(int argNum, long def);
    float[] checkVector(int argNum);
    float[] optVector(int argNum, float[] def);
    String checkString(int argNum);
    String optString(int argNum, String def);
    int checkOption(int argNum, @Nullable String def, List<String> lst);
    Object checkUserData(int argNum, String typeName);
    ByteBuffer checkBuffer(int argNum);

    //    LUALIB_API void luaL_register(lua_State* L, const char* libname, const luaL_Reg* l);
    //    LUALIB_API int luaL_getmetafield(lua_State* L, int obj, const char* e);
    //    LUALIB_API int luaL_callmeta(lua_State* L, int obj, const char* e);

    //    LUALIB_API int luaL_newmetatable(lua_State* L, const char* tname);
    //    #define luaL_getmetatable(L, n) (lua_getfield(L, LUA_REGISTRYINDEX, (n)))

    // TODO: idk below
    //    LUALIB_API void luaL_where(lua_State* L, int lvl);
    //    LUALIB_API const char* luaL_findtable(lua_State* L, int index, const char* fname, int szhint);
    //    LUALIB_API int luaL_callyieldable(lua_State* L, int nargs, int nresults);

    void sandbox();
    void sandboxThread();

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

}

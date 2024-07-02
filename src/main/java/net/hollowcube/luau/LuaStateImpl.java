package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.*;
import net.hollowcube.luau.util.GlobalRef;
import net.hollowcube.luau.util.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import static net.hollowcube.luau.internal.vm.lua_h.*;
import static net.hollowcube.luau.internal.vm.lualib_h.*;

@SuppressWarnings("preview")
final class LuaStateImpl implements LuaState {

    static {
        NativeLibraryLoader.loadLibrary("vm");
    }

    /*
        // isSandboxed call required
    // assertNotSandboxed() before any setglobal
    //todo if global.sandbox() prevents any globals in that state from being changed
    // then what does sandboxthread actually do?
    // would stop modification of anything present, so i believe i could eval some code and then sandboxthread and it couldnt be touched.
     */

    static final int UTAG_LIMIT = LUA_UTAG_LIMIT();
    static final int LUTAG_LIMIT = LUA_LUTAG_LIMIT();

    static double clock() {
        class H {
            static final lua_clock invoker = lua_clock.makeInvoker();
        }
        return H.invoker.apply();
    }

    private static final MemorySegment UNTAGGED_UDATA_DTOR = lua_newuserdatadtor$dtor.allocate(
            ud -> GlobalRef.unref(ud.get(ValueLayout.JAVA_LONG, 0)), Arena.global());

    private final MemorySegment L;
    private final Arena arena;
    private final boolean isThread;

    private long ref; // JNI global reference which needs to be freed.

    // Holds the thread data set by the user, rather than actually sending it to lua.
    private Object threadData = null;

    public LuaStateImpl(@NotNull MemorySegment L, @NotNull Arena arena, boolean isThread) {
        this.L = L;
        this.arena = arena;
        this.isThread = isThread;

        // Create a JNI global reference and store it in the userdata of this state.
        // We implement the user data setter/getter in plain java so this is fine.
        this.ref = GlobalRef.newref(this);
        lua_setthreaddata(L, MemorySegment.ofAddress(this.ref));
    }

    public LuaStateImpl() {
        this(lualib_h.luaL_newstate(), Arena.ofConfined(), false);

        // If this is a root state, setup the thread close callback to clean them up.
        final MemorySegment callbacks = lua_callbacks(L);
        final MemorySegment userthread = lua_Callbacks.userthread.allocate(LuaStateImpl::threadChange, arena);
        lua_Callbacks.userthread(callbacks, userthread);
    }

    @Override
    public void close() {
        if (isThread)
            throw new IllegalStateException("cannot close a thread directly, it will be closed when lua garbage collects it.");
        lua_h.lua_close(L);
        closeInternal();
    }

    private static void threadChange(@NotNull MemorySegment LP, @NotNull MemorySegment L) {
        // We don't need to do anything on start currently
        if (LP != MemorySegment.NULL) return;

        deref(L).closeInternal();
    }

    private void closeInternal() {
        arena.close();

        if (ref == 0) throw new IllegalStateException("LuaState was double closed.");
        GlobalRef.unref(ref);
        ref = 0;
    }

    @Override
    public @NotNull LuaState newThread() {
        return new LuaStateImpl(lua_newthread(L), Arena.ofConfined(), true);
    }

    @Override
    public @NotNull LuaState mainThread() {
        return new LuaStateImpl(lua_mainthread(L), Arena.ofConfined(), true);
    }

    @Override
    public void resetThread() {
        lua_resetthread(L);
    }

    @Override
    public boolean isThreadReset() {
        return lua_isthreadreset(L) != 0;
    }

    @Override
    public void setGlobal(@NotNull String name) {
        try (Arena local = Arena.ofConfined()) {
            final MemorySegment nameStr = local.allocateUtf8String(name);
            lua_setfield(L, LUA_GLOBALSINDEX(), nameStr);
        }
    }

    @Override
    public int getGlobal(@NotNull String name) {
        try (Arena local = Arena.ofConfined()) {
            final MemorySegment nameStr = local.allocateUtf8String(name);
            return lua_getfield(L, LUA_GLOBALSINDEX(), nameStr);
        }
    }

    @Override
    public void sandbox() {
        lualib_h.luaL_sandbox(L);
    }

    @Override
    public void sandboxThread() {
        lualib_h.luaL_sandboxthread(L);
    }

    @Override
    public int absIndex(int index) {
        return lua_absindex(L, index);
    }

    @Override
    public int getTop() {
        return lua_gettop(L);
    }

    @Override
    public void setTop(int index) {
        lua_settop(L, index);
    }

    @Override
    public void pushValue(int index) {
        lua_pushvalue(L, index);
    }

    @Override
    public void remove(int index) {
        lua_remove(L, index);
    }

    @Override
    public void insert(int index) {
        lua_insert(L, index);
    }

    @Override
    public void replace(int index) {
        lua_replace(L, index);
    }

    @Override
    public boolean checkStack(int size) {
        return lua_checkstack(L, size) != 0;
    }

    @Override
    public void rawCheckStack(int size) {
        lua_rawcheckstack(L, size);
    }

    @Override
    public void xMove(@NotNull LuaState to, int n) {
        lua_xmove(L, ((LuaStateImpl) to).L, n);
    }

    @Override
    public void xPush(@NotNull LuaState to, int index) {
        lua_xpush(L, ((LuaStateImpl) to).L, index);
    }

    @Override
    public void pop(int n) {
        setTop(-n - 1);
    }

    @Override
    public boolean isNone(int index) {
        return type(index) == LuaType.NONE;
    }

    @Override
    public boolean isNil(int index) {
        return type(index) == LuaType.NIL;
    }

    @Override
    public boolean isNoneOrNil(int index) {
        final LuaType type = type(index);
        return type == LuaType.NONE || type == LuaType.NIL;
    }

    @Override
    public boolean isBoolean(int index) {
        return type(index) == LuaType.BOOLEAN;
    }

    @Override
    public boolean isLightUserData(int index) {
        return type(index) == LuaType.LIGHTUSERDATA;
    }

    @Override
    public boolean isNumber(int index) {
        return type(index) == LuaType.NUMBER;
    }

    @Override
    public boolean isVector(int index) {
        return type(index) == LuaType.VECTOR;
    }

    @Override
    public boolean isString(int index) {
        return lua_isstring(L, index) != 0;
    }

    @Override
    public boolean isTable(int index) {
        return type(index) == LuaType.TABLE;
    }

    @Override
    public boolean isFunction(int index) {
        return type(index) == LuaType.FUNCTION;
    }

    @Override
    public boolean isCFunction(int index) {
        return lua_iscfunction(L, index) != 0;
    }

    @Override
    public boolean isLFunction(int index) {
        return lua_isLfunction(L, index) != 0;
    }

    @Override
    public boolean isUserData(int index) {
        return lua_isuserdata(L, index) != 0;
    }

    @Override
    public boolean isThread(int index) {
        return type(index) == LuaType.THREAD;
    }

    @Override
    public boolean isBuffer(int index) {
        return type(index) == LuaType.BUFFER;
    }

    @Override
    public @NotNull LuaType type(int index) {
        return LuaType.byId(lua_type(L, index));
    }

    @Override
    public @NotNull String typeName(@NotNull LuaType type) {
        return lua_typename(L, type.id()).getUtf8String(0);
    }

    @Override
    public @NotNull String typeName(int index) {
        return typeName(type(index));
    }

    @Override
    public boolean equal(int index1, int index2) {
        return lua_equal(L, index1, index2) != 0;
    }

    @Override
    public boolean rawEqual(int index1, int index2) {
        return lua_rawequal(L, index1, index2) != 0;
    }

    @Override
    public boolean lessThan(int index1, int index2) {
        return lua_lessthan(L, index1, index2) != 0;
    }

    @Override
    public int toInteger(int index) {
        return lua_tointegerx(L, index, MemorySegment.NULL);
    }

    @Override
    public double toNumber(int index) {
        return lua_tonumberx(L, index, MemorySegment.NULL);
    }

    @Override
    public int toUnsigned(int index) {
        return lua_tounsignedx(L, index, MemorySegment.NULL);
    }

    @Override
    public float[] toVector(int index) {
        return unpackVector(lua_tovector(L, index));
    }

    @Override
    public boolean toBoolean(int index) {
        return lua_toboolean(L, index) != 0;
    }

    @Override
    public @NotNull String toString(int index) {
        return readLString(len -> lua_tolstring(L, index, len));
    }

    @Override
    public int objectLength(int index) {
        return lua_objlen(L, index);
    }

    @Override
    public int stringLength(int index) {
        return objectLength(index);
    }

    @Override
    public @NotNull LuaFunc toCFunction(int index) {
        final MemorySegment cfunc = lua_tocfunction(L, index);
        return state -> lua_CFunction.invoke(cfunc, ((LuaStateImpl) state).L);
    }

    @Override
    public Object toLightUserData(int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Object toLightUserDataTagged(int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Object toUserData(int index) {
        final MemorySegment ud = lua_touserdata(L, index);
        return GlobalRef.get(ud.get(ValueLayout.JAVA_LONG, 0));
    }

    @Override
    public Object toUserDataTagged(int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public int userDataTag(int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public int lightUserDataTag(int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public @NotNull LuaState toThread(int index) {
        return deref(lua_tothread(L, index));
    }

    @Override
    public @NotNull ByteBuffer toBuffer(int index) {
        return readBuffer(len -> lua_tobuffer(L, index, len));
    }

    @Override
    public Object toPointer(int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void pushNil() {
        lua_pushnil(L);
    }

    @Override
    public void pushNumber(double n) {
        lua_pushnumber(L, n);
    }

    @Override
    public void pushInteger(int n) {
        lua_pushinteger(L, n);
    }

    @Override
    public void pushUnsigned(int n) {
        lua_pushunsigned(L, n);
    }

    @Override
    public void pushVector(float[] v) {
        if (v.length != 3) throw new IllegalArgumentException("Vector must have 3 components");
        lua_pushvector(L, v[0], v[1], v[2]);
    }

    @Override
    public void pushString(@NotNull String s) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment str = arena.allocateUtf8String(s);
            lua_pushlstring(L, str, str.byteSize() - 1); // -1 to exclude null terminator
        }
    }

    @Override
    public void pushBoolean(boolean b) {
        lua_pushboolean(L, b ? 1 : 0);
    }

    @Override
    public void pushCFunction(@NotNull LuaFunc func, @NotNull String debugName) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment debugNameStr = arena.allocateUtf8String(debugName);
            lua_pushcclosurek(L, wrapLuaFunc(func), debugNameStr, 0, MemorySegment.NULL);
        }
    }

    @Override
    public int pushThread() {
        return lua_pushthread(L);
    }

    @Override
    public void pushLightUserData(Object p) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void pushLightUserDataTagged(Object p, int tag) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void newUserData(@NotNull Object userdata) {
        // Our userdata fields always contain a single long holding a jni global reference. It is freed in the
        // destructor.
        final MemorySegment ud = lua_newuserdatadtor(L, ValueLayout.JAVA_LONG.byteSize(), UNTAGGED_UDATA_DTOR);
        ud.set(ValueLayout.JAVA_LONG, 0, GlobalRef.newref(userdata));
    }

    @Override
    public Object newUserDataTagged(long size, int tag) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public @NotNull ByteBuffer newBuffer(long size) {
        return lua_newbuffer(L, size).asSlice(0, size).asByteBuffer();
    }

    @Override
    public @NotNull LuaType getTable(int index) {
        return LuaType.byId(lua_gettable(L, index));
    }

    @Override
    public @NotNull LuaType getField(int index, @NotNull String key) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateUtf8String(key);
            return LuaType.byId(lua_getfield(L, index, nameStr));
        }
    }

    @Override
    public @NotNull LuaType rawGetField(int index, @NotNull String key) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateUtf8String(key);
            return LuaType.byId(lua_rawgetfield(L, index, nameStr));
        }
    }

    @Override
    public @NotNull LuaType rawGet(int index) {
        return LuaType.byId(lua_rawget(L, index));
    }

    @Override
    public @NotNull LuaType rawGetI(int index, int n) {
        return LuaType.byId(lua_rawgeti(L, index, n));
    }

    @Override
    public void newTable() {
        createTable(0, 0);
    }

    @Override
    public void createTable(int arrayCapacity, int fieldCapacity) {
        lua_createtable(L, arrayCapacity, fieldCapacity);
    }

    @Override
    public void setReadOnly(int index, boolean enabled) {
        lua_setreadonly(L, index, enabled ? 1 : 0);
    }

    @Override
    public boolean getReadOnly(int index) {
        return lua_getreadonly(L, index) != 0;
    }

    @Override
    public void setSafeEnv(int index, boolean enabled) {
        lua_setsafeenv(L, index, enabled ? 1 : 0);
    }

    @Override
    public int getMetaTable(int objectIndex) {
        return lua_getmetatable(L, objectIndex);
    }

    @Override
    public void getFEnv(int index) {
        lua_getfenv(L, index);
    }

    @Override
    public void setTable(int index) {
        lua_settable(L, index);
    }

    @Override
    public void setField(int index, @NotNull String key) {
        try (Arena arena = Arena.ofConfined()) {
            lua_setfield(L, index, arena.allocateUtf8String(key));
        }
    }

    @Override
    public void rawSetField(int index, @NotNull String key) {
        try (Arena arena = Arena.ofConfined()) {
            lua_rawsetfield(L, index, arena.allocateUtf8String(key));
        }
    }

    @Override
    public void rawSet(int index) {
        lua_rawset(L, index);
    }

    @Override
    public void rawSetI(int index, int n) {
        lua_rawseti(L, index, n);
    }

    @Override
    public void setMetaTable(int objectIndex) {
        lua_setmetatable(L, objectIndex);
    }

    @Override
    public void setFEnv(int index) {
        lua_setfenv(L, index);
    }

    @Override
    public void load(@NotNull String fileName, byte[] bytecode) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateUtf8String(fileName);
            final MemorySegment bytecodeArr = arena.allocateArray(ValueLayout.JAVA_BYTE, bytecode);

            int result = lua_h.luau_load(L, nameStr, bytecodeArr, bytecode.length, 0);
            if (result != 0) {
                final String error = toString(-1);
                pop(1);
                throw new RuntimeException(error);
            }
        }
    }

    @Override
    public void call(int argCount, int resultCount) {
        lua_call(L, argCount, resultCount);
    }

    @Override
    public void pcall(int argCount, int resultCount) {
        final LuaStatus status = LuaStatus.byId(lua_pcall(L, argCount, resultCount, 0)); //todo errFunc
        switch (status) {
            case OK -> {
            }
            case ERRRUN -> {
                throw new RuntimeException(toString(1));
            }
            default -> {
                throw new RuntimeException("unexpected pcall error: " + status);
            }
        }
    }

    @Override
    public void yield(int resultCount) {
        // lua_yield does return an int, but its always -1.
        lua_yield(L, resultCount);
    }

    @Override
    public void break_() {
        // lua_break does return an int, but its always -1.
        lua_break(L);
    }

    @Override
    public @NotNull LuaStatus resume(@NotNull LuaState from, int argCount) {
        final int status = lua_resume(L, ((LuaStateImpl) from).L, argCount);
        return LuaStatus.byId(status);
    }

    @Override
    public @NotNull LuaStatus resumeError(@NotNull LuaState from) {
        final int status = lua_resumeerror(L, ((LuaStateImpl) from).L);
        return LuaStatus.byId(status);
    }

    @Override
    public @NotNull LuaStatus status() {
        return LuaStatus.byId(lua_status(L));
    }

    @Override
    public boolean isYieldable() {
        return lua_isyieldable(L) != 0;
    }

    @Override
    public @Nullable Object getThreadData() {
        return this.threadData;
    }

    @Override
    public void setThreadData(@Nullable Object data) {
        this.threadData = data;
    }

    @Override
    public @NotNull LuaCoroutineStatus coroutineStatus(@NotNull LuaState coroutine) {
        return LuaCoroutineStatus.byId(lua_costatus(L, ((LuaStateImpl) coroutine).L));
    }

    @Override
    public int gc(@NotNull LuaGCOp op, int data) {
        return lua_gc(L, op.id(), data);
    }

    @Override
    public void setMemoryCategory(int category) {
        if (category < 0 || category > LUA_MEMORY_CATEGORIES())
            throw new IllegalArgumentException("Invalid memory category: " + category);
        lua_setmemcat(L, category);
    }

    @Override
    public long totalBytes(int category) {
        return lua_totalbytes(L, category);
    }

    @Override
    public void error() {
        lua_error(L);
    }

    @Override
    public void error(@NotNull String message) {
        // Reimplements the logic in luaL_errorL to avoid dealing with the varargs/unnecessary formatting
        luaL_where(L, 1);
        pushString(message);
        concat(2);
        error();
    }

    @Override
    public void typeError(int argIndex, @NotNull String typeName) {
        try (Arena arena = Arena.ofConfined()) {
            luaL_typeerrorL(L, argIndex, arena.allocateUtf8String(typeName)); // noreturn
        }
    }

    @Override
    public void argError(int argIndex, @NotNull String message) {
        try (Arena arena = Arena.ofConfined()) {
            luaL_argerrorL(L, argIndex, arena.allocateUtf8String(message)); // noreturn
        }
    }

    @Override
    public boolean next(int tableIndex) {
        return lua_next(L, tableIndex) != 0;
    }

    @Override
    public int rawIter(int index, int iter) {
        return lua_rawiter(L, index, iter);
    }

    @Override
    public void concat(int n) {
        lua_concat(L, n);
    }

    @Override
    public void setUserDataTag(int index, int tag) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void setUserDataDestructor(int tag, Object destructor) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Object getUserDataDestructor(int tag) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void setUserDataMetaTable(int tag, int index) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void getUserDataMetaTable(int tag) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void setLightUserDataName(int tag, @NotNull String name) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public @UnknownNullability String getLightUserDataName(int tag) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void cloneFunction(int index) {
        lua_clonefunction(L, index);
    }

    @Override
    public void clearTable(int index) {
        lua_cleartable(L, index);
    }

    @Override
    public int ref(int index) {
        return lua_ref(L, index);
    }

    @Override
    public void unref(int ref) {
        lua_unref(L, ref);
    }

    @Override
    public int getref(int ref) {
        return lua_rawgeti(L, LUA_REGISTRYINDEX(), ref);
    }

    @Override
    public int stackDepth() {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public @NotNull LuaCallbacks callbacks() {
        return new LuaCallbacksImpl(lua_callbacks(L));
    }

    @Override
    public void registerLib(@Nullable String name, @NotNull Map<String, LuaFunc> lib) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment l = luaL_Reg.allocateArray(lib.size() + 1, arena); // +1 for null entry

            int i = 0;
            for (final Map.Entry<String, LuaFunc> entry : lib.entrySet()) {
                final MemorySegment elem = luaL_Reg.asSlice(l, i++);
                luaL_Reg.name(elem, arena.allocateUtf8String(entry.getKey()));
                luaL_Reg.func(elem, wrapLuaFunc(entry.getValue()));
            }

            final MemorySegment nameStr = name != null
                    ? arena.allocateUtf8String(name)
                    : MemorySegment.NULL;
            luaL_register(L, nameStr, l);
        }
    }

    @Override
    public boolean getMetaField(int obj, @NotNull String e) {
        try (Arena arena = Arena.ofConfined()) {
            return luaL_getmetafield(L, obj, arena.allocateUtf8String(e)) != 0;
        }
    }

    @Override
    public boolean callMeta(int obj, @NotNull String e) {
        try (Arena arena = Arena.ofConfined()) {
            return luaL_callmeta(L, obj, arena.allocateUtf8String(e)) != 0;
        }
    }

    @Override
    public boolean newMetaTable(@NotNull String typeName) {
        try (Arena arena = Arena.ofConfined()) {
            return luaL_newmetatable(L, arena.allocateUtf8String(typeName)) != 0;
        }
    }

    @Override
    public void getMetaTable(@NotNull String typeName) {
        try (Arena arena = Arena.ofConfined()) {
            final LuaType type = getField(LUA_REGISTRYINDEX(), typeName);
            assert type == LuaType.TABLE : "expected table";
        }
    }

    @Override
    public void where() {
        where(0);
    }

    @Override
    public void where(int level) {
        luaL_where(L, level);
    }

    @Override
    public boolean checkBooleanArg(int argIndex) {
        return luaL_checkboolean(L, argIndex) != 0;
    }

    @Override
    public boolean optBooleanArg(int argIndex, boolean defaultValue) {
        return luaL_optboolean(L, argIndex, defaultValue ? 1 : 0) != 0;
    }

    @Override
    public int checkIntegerArg(int argIndex) {
        return luaL_checkinteger(L, argIndex);
    }

    @Override
    public int optIntegerArg(int argIndex, int defaultValue) {
        return luaL_optinteger(L, argIndex, defaultValue);
    }

    @Override
    public double checkNumberArg(int argIndex) {
        return luaL_checknumber(L, argIndex);
    }

    @Override
    public double optNumberArg(int argIndex, float defaultValue) {
        return luaL_optnumber(L, argIndex, defaultValue);
    }

    @Override
    public int checkUnsignedArg(int argIndex) {
        return luaL_checkunsigned(L, argIndex);
    }

    @Override
    public int optUnsignedArg(int argIndex, int defaultValue) {
        return luaL_optunsigned(L, argIndex, defaultValue);
    }

    @Override
    public float[] checkVectorArg(int argIndex) {
        return unpackVector(luaL_checkvector(L, argIndex));
    }

    @Override
    public float[] optVectorArg(int argIndex, float[] defaultValue) {
        try (Arena arena = Arena.ofConfined()) {
            return unpackVector(luaL_optvector(L, argIndex, packVector(arena, defaultValue)));
        }
    }

    @Override
    public @NotNull String checkStringArg(int argIndex) {
        return readLString(len -> luaL_checklstring(L, argIndex, len));
    }

    @Override
    public @NotNull String optStringArg(int argIndex, @NotNull String defaultValue) {
        try (Arena arena = Arena.ofConfined()) {
            return readLString(len -> luaL_optlstring(L, argIndex, arena.allocateUtf8String(defaultValue), len));
        }
    }

    @Override
    public @NotNull ByteBuffer checkBufferArg(int argIndex) {
        return readBuffer(len -> luaL_checkbuffer(L, argIndex, len));
    }

    @Override
    public @NotNull Object checkUserDataArg(int argIndex, @NotNull String typeName) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment ud = luaL_checkudata(L, argIndex, arena.allocateUtf8String(typeName));
            return GlobalRef.get(ud.get(ValueLayout.JAVA_LONG, 0));
        }
    }

    @Override
    public void checkStack(int size, @NotNull String message) {
        try (Arena arena = Arena.ofConfined()) {
            luaL_checkstack(L, size, arena.allocateUtf8String(message));
        }
    }

    @Override
    public void checkType(int argIndex, @NotNull LuaType type) {
        luaL_checktype(L, argIndex, type.id());
    }

    @Override
    public void checkAny(int argIndex) {
        luaL_checkany(L, argIndex);
    }

    @Override
    public void openLibs(@NotNull BuilinLibrary... libraries) {
        // Load all libraries if none are specified
        if (libraries.length == 0) {
            luaL_openlibs(L);
            return;
        }

        for (final BuilinLibrary lib : libraries) {
            switch (lib) {
                case BASE -> luaopen_base(L);
                case COROUTINE -> luaopen_coroutine(L);
                case TABLE -> luaopen_table(L);
                case OS -> luaopen_os(L);
                case STRING -> luaopen_string(L);
                case BIT32 -> luaopen_bit32(L);
                case BUFFER -> luaopen_buffer(L);
                case UTF8 -> luaopen_utf8(L);
                case MATH -> luaopen_math(L);
                case DEBUG -> luaopen_debug(L);
            }
        }
    }

    private static @NotNull LuaStateImpl deref(@NotNull MemorySegment L) {
        return (LuaStateImpl) GlobalRef.get(lua_getthreaddata(L).address());
    }

    private @NotNull MemorySegment wrapLuaFunc(@NotNull LuaFunc func) {
        final lua_CFunction.Function fi = L -> func.call(deref(L));
        return lua_CFunction.allocate(fi, arena);
    }

    private float[] unpackVector(@NotNull MemorySegment floats) {
        return new float[]{
                floats.get(ValueLayout.JAVA_FLOAT, 0),
                floats.get(ValueLayout.JAVA_FLOAT, 1),
                floats.get(ValueLayout.JAVA_FLOAT, 2)
        };
    }

    private @NotNull MemorySegment packVector(@NotNull SegmentAllocator alloc, float[] floats) {
        if (floats.length != 3) throw new IllegalArgumentException("Vector must have 3 components");
        final MemorySegment seg = alloc.allocateArray(ValueLayout.JAVA_FLOAT, 3);
        seg.set(ValueLayout.JAVA_FLOAT, 0, floats[0]);
        seg.set(ValueLayout.JAVA_FLOAT, 1, floats[1]);
        seg.set(ValueLayout.JAVA_FLOAT, 2, floats[2]);
        return seg;
    }

    private @NotNull String readLString(@NotNull Function<MemorySegment, MemorySegment> func) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment len = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment raw = func.apply(len);

            final long msgLen = len.get(ValueLayout.JAVA_LONG, 0);
            final byte[] msg = raw.asSlice(0, msgLen).toArray(ValueLayout.JAVA_BYTE);

            return new String(msg, StandardCharsets.UTF_8);
        }
    }

    private @NotNull ByteBuffer readBuffer(@NotNull Function<MemorySegment, MemorySegment> func) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment sizePtr = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment ptr = func.apply(sizePtr);

            final MemorySegment sizedPtr = ptr.asSlice(0, sizePtr.get(ValueLayout.JAVA_LONG, 0));
            return sizedPtr.asByteBuffer();
        }
    }
}

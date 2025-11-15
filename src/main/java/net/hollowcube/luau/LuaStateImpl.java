package net.hollowcube.luau;

import static net.hollowcube.luau.internal.vm.lua_h.*;
import static net.hollowcube.luau.internal.vm.lualib_h.*;
import static net.hollowcube.luau.internal.vm.luawrap_h.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.hollowcube.luau.internal.vm.*;
import net.hollowcube.luau.util.GlobalRef;
import net.hollowcube.luau.util.NativeLibraryLoader;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Nullable;

/// LuaStateImpl is a thin wrapper around a LuaState*, and must hold no extra state on the java side.
/// This is because we reconstruct instances simply from the pointer returned by c functions.
record LuaStateImpl(MemorySegment L) implements LuaState {
    static {
        NativeLibraryLoader.loadLibrary("vm");
    }

    private static final boolean SHOW_COMPLETE_BACKTRACE = Boolean.getBoolean(
        "luau.show-complete-backtrace"
    );
    private static final boolean NO_BACKTRACE_MERGE = Boolean.getBoolean(
        "luau.no-backtrace-merge"
    );

    private static final Pattern DEFAULT_ERROR_TRACE_REGEX = Pattern.compile(
        "^\\[string \".*?\"]:\\d+:\\s"
    );

    private static final MemorySegment UNTAGGED_UDATA_DTOR =
        luaW_newuserdatadtor$dtor.allocate(
            ud -> GlobalRef.unref(ud.get(ValueLayout.JAVA_LONG, 0)),
            Arena.global()
        );
    private static final MemorySegment TAGGED_UDATA_DTOR =
        lua_Destructor.allocate(
            (_, ud) -> GlobalRef.unref(ud.get(ValueLayout.JAVA_LONG, 0)),
            Arena.global()
        );
    // We allocate the raw value for this function because we don't want the error handling parts of LuaFunc.
    private static final MemorySegment PCALL_ERRFUNC_REF =
        lua_CFunction.allocate(LuaStateImpl::pcallErrFunc, Arena.global());
    private static final MemorySegment LUA_DEBUG_WHAT =
        Arena.global().allocateFrom("sln");

    static final int LIGHT_USERDATA_TAG_LIMIT = LUA_LUTAG_LIMIT();
    static final int USERDATA_TAG_LIMIT = LUA_UTAG_LIMIT();
    static final int MEMORY_CATEGORIES = LUA_MEMORY_CATEGORIES();

    static LuaState newState(@Nullable MemorySegment allocator) {
        var L = luaW_newstate(
            Objects.requireNonNullElse(allocator, MemorySegment.NULL)
        );
        if (L.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Failed to create new Lua state");
        }

        // We use userdata destructors to remove the java object ref (so it may be GC'd).
        // So we must add a destructor for every tag immediately.
        for (int i = 0; i < USERDATA_TAG_LIMIT; i++) {
            lua_setuserdatadtor(L, i, TAGGED_UDATA_DTOR);
        }

        return new LuaStateImpl(L);
    }

    @Override
    public void close() {
        lua_close(L);
    }

    //TODO: test me
    @Override
    public LuaState newThread() {
        final MemorySegment thread = luaW_newthread(L);
        propagateException();
        return new LuaStateImpl(thread);
    }

    //TODO: test me
    @Override
    public LuaState mainThread() {
        return new LuaStateImpl(lua_mainthread(L));
    }

    //TODO: test me
    @Override
    public void resetThread() {
        luaW_resetthread(L);
        propagateException();
    }

    //TODO: test me
    @Override
    public boolean isThreadReset() {
        return lua_isthreadreset(L) != 0;
    }

    @Override
    public int absIndex(int index) {
        return lua_absindex(L, index);
    }

    @Override
    public int getTop() {
        // TODO: might be faster for some simple methods to inline the reads from luastate directly.
        //       The jvm is able to optimize that much better than a downcall
        return lua_gettop(L);
    }

    @Override
    public void setTop(int index) {
        lua_settop(L, index);
    }

    @Override
    public void pop(int n) {
        lua_settop(L, -n - 1);
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

    //TODO: test me
    @Override
    public void xmove(LuaState to, int n) {
        lua_xmove(L, ((LuaStateImpl) to).L, n);
    }

    //TODO: test me
    @Override
    public void xpush(LuaState to, int index) {
        lua_xpush(L, ((LuaStateImpl) to).L, index);
    }

    @Override
    public LuaType type(int index) {
        return LuaType.byId(lua_type(L, index));
    }

    //TODO: test me (including on userdata types & stuff)
    @Override
    public String typeName(int index) {
        return luaLW_typename(L, index).getString(0, StandardCharsets.UTF_8);
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
    public boolean isNumber(int index) {
        return type(index) == LuaType.NUMBER;
    }

    @Override
    public boolean isVector(int index) {
        return type(index) == LuaType.VECTOR;
    }

    @Override
    public boolean isString(int index) {
        return type(index) == LuaType.STRING;
    }

    @Override
    public boolean isBuffer(int index) {
        return type(index) == LuaType.BUFFER;
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
    public boolean isNativeFunction(int index) {
        return lua_iscfunction(L, index) != 0;
    }

    @Override
    public boolean isLuaFunction(int index) {
        return lua_isLfunction(L, index) != 0;
    }

    @Override
    public boolean isUserData(int index) {
        return type(index) == LuaType.USERDATA;
    }

    @Override
    public boolean isLightUserData(int index) {
        return type(index) == LuaType.LIGHTUSERDATA;
    }

    //TODO: test me
    @Override
    public boolean isThread(int index) {
        return type(index) == LuaType.THREAD;
    }

    //TODO: test me
    @Override
    public boolean equal(int index1, int index2) {
        final boolean result = luaW_equal(L, index1, index2) != 0;
        propagateException();
        return result;
    }

    //TODO: test me
    @Override
    public boolean rawEqual(int index1, int index2) {
        return lua_rawequal(L, index1, index2) != 0;
    }

    //TODO: test me
    @Override
    public boolean lessThan(int index1, int index2) {
        int res = luaW_lessthan(L, index1, index2);
        propagateException();
        return res != 0;
    }

    @Override
    public void concat(int n) {
        luaW_concat(L, n);
        propagateException();
    }

    @Override
    public int len(int index) {
        int result = luaW_objlen(L, index);
        propagateException();
        return result;
    }

    @Override
    public boolean toBoolean(int index) {
        return lua_toboolean(L, index) != 0;
    }

    @Override
    public double toNumber(int index) {
        return lua_tonumberx(L, index, MemorySegment.NULL);
    }

    @Override
    public @Nullable Double toNumberOrNull(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment isNum = arena.allocate(ValueLayout.JAVA_INT);
            final double value = lua_tonumberx(L, index, isNum);
            return isNum.get(ValueLayout.JAVA_INT, 0) != 0 ? value : null;
        }
    }

    @Override
    public int toInteger(int index) {
        return lua_tointegerx(L, index, MemorySegment.NULL);
    }

    @Override
    public @Nullable Integer toIntegerOrNull(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment isNum = arena.allocate(ValueLayout.JAVA_INT);
            final int value = lua_tointegerx(L, index, isNum);
            return isNum.get(ValueLayout.JAVA_INT, 0) != 0 ? value : null;
        }
    }

    @Override
    public long toUnsigned(int index) {
        return Integer.toUnsignedLong(
            lua_tounsignedx(L, index, MemorySegment.NULL)
        );
    }

    @Override
    public @Nullable Long toUnsignedOrNull(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment isNum = arena.allocate(ValueLayout.JAVA_INT);
            final int value = lua_tounsignedx(L, index, isNum);
            return isNum.get(ValueLayout.JAVA_INT, 0) != 0
                ? Integer.toUnsignedLong(value)
                : null;
        }
    }

    @Override
    public float@Nullable [] toVector(int index) {
        final MemorySegment value = lua_tovector(L, index);
        return value.equals(MemorySegment.NULL)
            ? null
            : new float[] {
                  value.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                  value.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                  value.getAtIndex(ValueLayout.JAVA_FLOAT, 2),
              };
    }

    @Override
    public @Nullable String toString(int index) {
        if (!isString(index)) return null;
        return unsafeToString(index);
    }

    @Override
    public @Nullable String unsafeToString(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment len = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment raw = luaW_tolstring(L, index, len);
            propagateException();

            if (raw.equals(MemorySegment.NULL)) return null;

            final long msgLen = len.get(ValueLayout.JAVA_LONG, 0);
            final byte[] msg = raw
                .asSlice(0, msgLen)
                .toArray(ValueLayout.JAVA_BYTE);
            return new String(msg, StandardCharsets.UTF_8);
        }
    }

    @Override
    public String toStringRepr(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment len = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment raw = luaLW_tolstring(L, index, len);
            propagateException();

            final long msgLen = len.get(ValueLayout.JAVA_LONG, 0);
            final byte[] msg = raw
                .asSlice(0, msgLen)
                .toArray(ValueLayout.JAVA_BYTE);
            return new String(msg, StandardCharsets.UTF_8);
        }
    }

    @Override
    public short toStringAtomRaw(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment atomRef = arena.allocate(
                ValueLayout.JAVA_SHORT
            );
            final MemorySegment str = lua_tolstringatom(
                L,
                index,
                MemorySegment.NULL,
                atomRef
            );
            if (str.equals(MemorySegment.NULL)) return NO_ATOM;
            return atomRef.get(ValueLayout.JAVA_SHORT, 0);
        }
    }

    @Override
    public @Nullable LuaString toStringAtom(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment lenRef = arena.allocate(ValueLayout.JAVA_INT);
            final MemorySegment atomRef = arena.allocate(
                ValueLayout.JAVA_SHORT
            );
            final MemorySegment str = lua_tolstringatom(
                L,
                index,
                lenRef,
                atomRef
            );
            if (str.equals(MemorySegment.NULL)) return null;

            short atom = atomRef.get(ValueLayout.JAVA_SHORT, 0);
            if (atom >= 0) return new LuaString.Atom(atom);
            return new LuaString.Str(
                new String(
                    str
                        .reinterpret(lenRef.get(ValueLayout.JAVA_INT, 0))
                        .toArray(ValueLayout.JAVA_BYTE),
                    StandardCharsets.UTF_8
                )
            );
        }
    }

    //TODO: test me
    @Override
    public short nameCallAtomRaw() {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment atomRef = arena.allocate(
                ValueLayout.JAVA_SHORT
            );
            final MemorySegment str = lua_namecallatom(L, atomRef);
            if (str.equals(MemorySegment.NULL)) return NO_ATOM;
            return atomRef.get(ValueLayout.JAVA_SHORT, 0);
        }
    }

    //TODO: test me
    @Override
    public LuaString nameCallAtom() {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment atomRef = arena.allocate(
                ValueLayout.JAVA_SHORT
            );
            final MemorySegment str = lua_namecallatom(L, atomRef);
            if (str.equals(MemorySegment.NULL)) {
                throw new IllegalStateException(
                    "namecallatom may only be called within a __namecall metamethod"
                );
            }

            short atom = atomRef.get(ValueLayout.JAVA_SHORT, 0);
            if (atom >= 0) return new LuaString.Atom(atom);
            return new LuaString.Str(str.getString(0, StandardCharsets.UTF_8));
        }
    }

    @Override
    public long toLightUserData(int index) {
        return lua_tolightuserdata(L, index).address();
    }

    @Override
    public long toLightUserDataTagged(int index, int tag) {
        return lua_tolightuserdatatagged(L, index, tag).address();
    }

    //TODO: test me
    @Override
    public int lightUserDataTag(int index) {
        return lua_lightuserdatatag(L, index);
    }

    @Override
    public @Nullable Object toUserData(int index) {
        if (type(index) != LuaType.USERDATA) return null;
        // Can't return null because we already ensured it is a userdata.
        return GlobalRef.get(
            lua_touserdata(L, index).get(ValueLayout.JAVA_LONG, 0)
        );
    }

    @Override
    public @Nullable Object toUserDataTagged(int index, int tag) {
        final MemorySegment address = lua_touserdatatagged(L, index, tag);
        if (address.equals(MemorySegment.NULL)) return null;
        return GlobalRef.get(address.get(ValueLayout.JAVA_LONG, 0));
    }

    @Override
    public int userDataTag(int index) {
        int tag = lua_userdatatag(L, index);
        // We normalize the "untagged with constructor" tag (max tag) to 0 because all
        // untagged userdata has a destructor and we dont expose destructors to java.
        return tag == USERDATA_TAG_LIMIT ? 0 : tag;
    }

    @Override
    public @Nullable LuaState toThread(int index) {
        final MemorySegment thread = lua_tothread(L, index);
        if (thread.equals(MemorySegment.NULL)) return null;
        return new LuaStateImpl(thread);
    }

    @Override
    public @Nullable ByteBuffer toBuffer(int index) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment sizePtr = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment ptr = lua_tobuffer(L, index, sizePtr);
            propagateException();

            if (ptr.equals(MemorySegment.NULL)) return null;

            final MemorySegment sizedPtr = ptr.asSlice(
                0,
                sizePtr.get(ValueLayout.JAVA_LONG, 0)
            );
            return sizedPtr.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    @Override
    public void pushNil() {
        lua_pushnil(L);
    }

    @Override
    public void pushBoolean(boolean value) {
        lua_pushboolean(L, value ? 1 : 0);
    }

    @Override
    public void pushNumber(double value) {
        lua_pushnumber(L, value);
    }

    @Override
    public void pushInteger(int value) {
        lua_pushinteger(L, value);
    }

    @Override
    public void pushUnsigned(long value) {
        lua_pushunsigned(L, (int) value);
    }

    @Override
    public void pushVector(float x, float y, float z) {
        lua_pushvector(L, x, y, z);
    }

    @Override
    public void pushVector(float[] value) {
        if (value.length != 3) throw new LuaError(
            "vector must have 3 components"
        );
        lua_pushvector(L, value[0], value[1], value[2]);
    }

    @Override
    public void pushString(String value) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment str = arena.allocateFrom(value);
            luaW_pushlstring(L, str, str.byteSize() - 1); // -1 to exclude null terminator
            propagateException();
        }
    }

    @Override
    public void pushLightUserData(long value) {
        lua_pushlightuserdatatagged(L, MemorySegment.ofAddress(value), 0);
    }

    @Override
    public void pushLightUserDataTagged(long value, int tag) {
        if (tag < 0 || tag > LIGHT_USERDATA_TAG_LIMIT) throw new LuaError(
            "light userdata tag must be between 0 and " +
                LIGHT_USERDATA_TAG_LIMIT
        );
        lua_pushlightuserdatatagged(L, MemorySegment.ofAddress(value), tag);
    }

    @Override
    public void newUserData(Object value) {
        final MemorySegment ud = luaW_newuserdatadtor(
            L,
            ValueLayout.JAVA_LONG.byteSize(),
            UNTAGGED_UDATA_DTOR
        );
        propagateException();
        ud.set(ValueLayout.JAVA_LONG, 0, GlobalRef.newref(value));
    }

    @Override
    public void newUserDataTagged(Object value, int tag) {
        final MemorySegment ud = luaW_newuserdatatagged(
            L,
            ValueLayout.JAVA_LONG.byteSize(),
            tag
        );
        propagateException();
        ud.set(ValueLayout.JAVA_LONG, 0, GlobalRef.newref(value));
    }

    @Override
    public void newUserDataTaggedWithMetatable(Object value, int tag) {
        final MemorySegment ud = luaW_newuserdatataggedwithmetatable(
            L,
            ValueLayout.JAVA_LONG.byteSize(),
            tag
        );
        propagateException();
        ud.set(ValueLayout.JAVA_LONG, 0, GlobalRef.newref(value));
    }

    //TODO: test me
    @Override
    public boolean pushThread(LuaState thread) {
        return lua_pushthread(((LuaStateImpl) thread).L) != 0;
    }

    @Override
    public ByteBuffer newBuffer(long size) {
        final MemorySegment value = luaW_newbuffer(L, size);
        propagateException();
        return value
            .reinterpret(size)
            .asByteBuffer()
            .order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void pushFunction(LuaFunc func) {
        // The switch is here as an exhaustivity check :)
        switch (func) {
            case LuaFuncImpl(
                MemorySegment funcRef,
                MemorySegment debugNameRef,
                _
            ) -> luaW_pushcclosurek(
                L,
                funcRef,
                debugNameRef,
                0,
                MemorySegment.NULL
            );
        }
    }

    @Override
    public LuaType getTable(int index) {
        int result = luaW_gettable(L, index);
        propagateException();
        return LuaType.byId(result);
    }

    @Override
    public LuaType getField(int index, String key) {
        try (Arena arena = Arena.ofConfined()) {
            int result = luaW_getfield(L, index, arena.allocateFrom(key));
            propagateException();
            return LuaType.byId(result);
        }
    }

    @Override
    public LuaType rawGetField(int index, String key) {
        try (Arena arena = Arena.ofConfined()) {
            int result = lua_rawgetfield(L, index, arena.allocateFrom(key));
            return LuaType.byId(result);
        }
    }

    @Override
    public LuaType rawGet(int index) {
        int result = lua_rawget(L, index);
        return LuaType.byId(result);
    }

    @Override
    public LuaType rawGetI(int index, int n) {
        int result = lua_rawgeti(L, index, n);
        return LuaType.byId(result);
    }

    @Override
    public void createTable(int narr, int nrec) {
        luaW_createtable(L, narr, nrec);
        propagateException();
    }

    @Override
    public void newTable() {
        createTable(0, 0);
    }

    //TODO: test me
    @Override
    public void setReadOnly(int index, boolean enabled) {
        lua_setreadonly(L, index, enabled ? 1 : 0);
    }

    //TODO: test me
    @Override
    public boolean getReadOnly(int index) {
        return lua_getreadonly(L, index) != 0;
    }

    //TODO: test me
    @Override
    public void setTable(int index) {
        luaW_settable(L, index);
        propagateException();
    }

    //TODO: test me
    @Override
    public void setField(int index, String key) {
        try (Arena arena = Arena.ofConfined()) {
            luaW_setfield(L, index, arena.allocateFrom(key));
            propagateException();
        }
    }

    //TODO: test me
    @Override
    public void rawSetField(int index, String key) {
        try (Arena arena = Arena.ofConfined()) {
            luaW_rawsetfield(L, index, arena.allocateFrom(key));
            propagateException();
        }
    }

    //TODO: test me
    @Override
    public void rawSet(int index) {
        luaW_rawset(L, index);
        propagateException();
    }

    //TODO: test me
    @Override
    public void rawSetI(int index, int n) {
        luaW_rawseti(L, index, n);
        propagateException();
    }

    //TODO: test me
    @Override
    public boolean getMetaTable(int index) {
        return lua_getmetatable(L, index) != 0;
    }

    //TODO: test me
    @Override
    public void setMetaTable(int index) {
        luaW_setmetatable(L, index); // always returns 1
        propagateException();
    }

    @Override
    public void load(String chunkName, byte[] data) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment chunkNameRef = arena.allocateFrom(chunkName);
            final MemorySegment bytecodeRef = arena.allocateFrom(
                ValueLayout.JAVA_BYTE,
                data
            );
            final LuaStatus status = LuaStatus.byId(
                luau_load(L, chunkNameRef, bytecodeRef, data.length, 0)
            );
            if (status != LuaStatus.OK) {
                final String message = toStringRepr(-1);
                throw new LuaError(status, message);
            }
        }
    }

    @Override
    public void call(int nargs, int nresults) {
        luaW_pushcclosurek(
            L,
            PCALL_ERRFUNC_REF,
            MemorySegment.NULL,
            0,
            MemorySegment.NULL
        );
        // Move the errfunc above the function being called
        // Stack before: [func] [arg1] [arg2] ... [argN]
        // Stack after:  [errfunc] [func] [arg1] [arg2] ... [argN]
        lua_insert(L, -(nargs + 2));

        int errFuncIndex = lua_absindex(L, -(nargs + 2));
        LuaStatus status = LuaStatus.byId(
            lua_pcall(L, nargs, nresults, -(nargs + 2))
        );
        lua_remove(L, errFuncIndex);

        if (status != LuaStatus.OK) propagateExceptionInner(status);
    }

    //TODO: test me
    @Override
    public int yield(int nresults) {
        int result = luaW_yield(L, nresults);
        propagateException();
        return result;
    }

    //TODO: test me
    @Override
    public LuaStatus status() {
        return LuaStatus.byId(lua_status(L));
    }

    //TODO: test me
    @Override
    public boolean isYieldable() {
        return lua_isyieldable(L) != 0;
    }

    //TODO: test me
    @Override
    public LuaCoStatus costatus(LuaState co) {
        return LuaCoStatus.byId(lua_costatus(L, ((LuaStateImpl) co).L));
    }

    //TODO: test me
    @Override
    public int gc(LuaGcOp op, int data) {
        return lua_gc(L, op.ordinal(), data);
    }

    @Override
    public void setMemCat(int category) {
        lua_setmemcat(L, category);
    }

    @Override
    public long totalBytes(int category) {
        return lua_totalbytes(L, category);
    }

    //region Error Throwing

    @Override
    public void error() {
        throw new LuaError((String) null);
    }

    @Override
    public void error(String message) {
        throw new LuaError(message);
    }

    @Override
    public void error(@PrintFormat String message, Object... args) {
        throw new LuaError(message.formatted(args));
    }

    @Override
    public void typeError(int argNum, String typeName) {
        try (Arena arena = Arena.ofConfined()) {
            luaLW_typeerror(L, argNum, arena.allocateFrom(typeName));
            propagateException();
        }
    }

    @Override
    public void argError(int argNum, String message) {
        try (Arena arena = Arena.ofConfined()) {
            luaLW_argerror(L, argNum, arena.allocateFrom(message));
            propagateException();
        }
    }

    //endregion

    @Override
    public boolean checkStack(int size) {
        return lua_checkstack(L, size) != 0;
    }

    @Override
    public void checkStack(int size, @Nullable String message) {
        if (!checkStack(size)) {
            final String msg = message != null
                ? "stack overflow (%s)".formatted(message)
                : "stack overflow";
            throw new LuaError(msg);
        }
    }

    //TODO: test me
    @Override
    public boolean next(int index) {
        int result = luaW_next(L, index);
        propagateException();
        return result != 0;
    }

    //TODO: test me
    @Override
    public int rawIter(int index, int iter) {
        return lua_rawiter(L, index, iter);
    }

    @Override
    public void setUserDataTag(int index, int tag) {
        lua_setuserdatatag(L, index, tag);
    }

    @Override
    public void setUserDataMetaTable(int tag) {
        lua_setuserdatametatable(L, tag);
    }

    @Override
    public void getUserDataMetaTable(int tag) {
        lua_getuserdatametatable(L, tag);
    }

    @Override
    public void setLightUserDataName(int tag, String name) {
        try (Arena arena = Arena.ofConfined()) {
            luaW_setlightuserdataname(L, tag, arena.allocateFrom(name));
            propagateException();
        }
    }

    @Override
    public @Nullable String getLightUserDataName(int tag) {
        try (Arena arena = Arena.ofConfined()) {
            var name = lua_getlightuserdataname(L, tag);
            if (name.equals(MemorySegment.NULL)) return null;
            return name.getString(0, StandardCharsets.UTF_8);
        }
    }

    //TODO: test me
    @Override
    public void cloneFunction(int index) {
        luaW_clonefunction(L, index);
        propagateException();
    }

    //TODO: test me
    @Override
    public void clearTable(int index) {
        luaW_cleartable(L, index);
        propagateException();
    }

    //TODO: test me
    @Override
    public void cloneTable(int index) {
        luaW_clonetable(L, index);
        propagateException();
    }

    //TODO: test me
    @Override
    public int ref(int index) {
        return lua_ref(L, index);
    }

    //TODO: test me
    @Override
    public void unRef(int ref) {
        lua_unref(L, ref);
    }

    //TODO: test me
    @Override
    public LuaType getRef(int ref) {
        return rawGetI(LUA_REGISTRYINDEX(), ref);
    }

    @Override
    public void setGlobal(String s) {
        setField(LUA_GLOBALSINDEX(), s);
    }

    @Override
    public void getGlobal(String s) {
        getField(LUA_GLOBALSINDEX(), s);
    }

    @Override
    public LuaCallbacks callbacks() {
        return new LuaCallbacksImpl(lua_callbacks(L));
    }

    //region Arg checking

    @Override
    public void checkAny(int argNum) {
        if (lua_type(L, argNum) == LuaType.NONE.id()) return;
        error("missing argument #%d", argNum);
    }

    @Override
    public void checkType(int argNum, LuaType type) {
        if (lua_type(L, argNum) == type.id()) return;
        typeError(argNum, type.typeName());
    }

    @Override
    public boolean checkBoolean(int argNum) {
        boolean result = luaLW_checkboolean(L, argNum) != 0;
        propagateException();
        return result;
    }

    @Override
    public boolean optBoolean(int argNum, boolean def) {
        return luaL_optboolean(L, argNum, def ? 1 : 0) != 0;
    }

    @Override
    public double checkNumber(int argNum) {
        final Double number = toNumberOrNull(argNum);
        if (number != null) return number;

        typeError(argNum, LuaType.NUMBER.typeName());
        return 0; // unreachable
    }

    @Override
    public double optNumber(int argNum, double def) {
        final Double number = toNumberOrNull(argNum);
        return number != null ? number : def;
    }

    @Override
    public int checkInteger(int argNum) {
        final Integer integer = toIntegerOrNull(argNum);
        if (integer != null) return integer;

        typeError(argNum, LuaType.NUMBER.typeName());
        return 0; // unreachable
    }

    @Override
    public int optInteger(int argNum, int def) {
        final Integer integer = toIntegerOrNull(argNum);
        return integer != null ? integer : def;
    }

    @Override
    public long checkUnsigned(int argNum) {
        final Long unsigned = toUnsignedOrNull(argNum);
        if (unsigned != null) return unsigned;

        typeError(argNum, LuaType.NUMBER.typeName());
        return 0; // unreachable
    }

    @Override
    public long optUnsigned(int argNum, long def) {
        final Long unsigned = toUnsignedOrNull(argNum);
        return unsigned != null ? unsigned : def;
    }

    @Override
    public float[] checkVector(int argNum) {
        final float[] vector = toVector(argNum);
        if (vector != null) return vector;

        typeError(argNum, LuaType.VECTOR.typeName());
        return null; // unreachable
    }

    @Override
    public float[] optVector(int argNum, float[] def) {
        final float[] vector = toVector(argNum);
        return vector != null ? vector : def;
    }

    @Override
    public String checkString(int argNum) {
        final String string = toString(argNum);
        if (string != null) return string;

        typeError(argNum, LuaType.STRING.typeName());
        return null; // unreachable
    }

    @Override
    public String optString(int argNum, String def) {
        final String string = toString(argNum);
        return string != null ? string : def;
    }

    @Override
    public int checkOption(int argNum, @Nullable String def, List<String> lst) {
        final String name = def != null
            ? optString(argNum, def)
            : checkString(argNum);
        for (int i = 0; i < lst.size(); i++) {
            if (lst.get(i).equals(name)) return i;
        }
        argError(argNum, "invalid option '%s'".formatted(name));
        return 0; // Never reached
    }

    @Override
    public Object checkUserData(int argNum, String typeName) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment ud = luaLW_checkudata(
                L,
                argNum,
                arena.allocateFrom(typeName)
            );
            propagateException();
            return GlobalRef.get(ud.get(ValueLayout.JAVA_LONG, 0));
        }
    }

    @Override
    public ByteBuffer checkBuffer(int argNum) {
        final ByteBuffer buffer = toBuffer(argNum);
        if (buffer != null) return buffer;

        typeError(argNum, LuaType.BUFFER.typeName());
        return null; // unreachable
    }

    //endregion

    @Override
    public void sandbox() {
        luaL_sandbox(L);
    }

    @Override
    public void sandboxThread() {
        luaL_sandboxthread(L);
    }

    @Override
    public void openLibs(BuilinLibrary... libraries) {
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

    @Override
    public void register(Map<String, LuaFunc> library) {
        for (Map.Entry<String, LuaFunc> entry : library.entrySet()) {
            pushFunction(entry.getValue());
            setField(-2, entry.getKey());
        }
    }

    @Override
    public void register(String name, Map<String, LuaFunc> library) {
        // TODO
        // int size = libsize(l);
        // // check whether lib already exists
        // luaL_findtable(L, LUA_REGISTRYINDEX, "_LOADED", 1);
        // lua_getfield(L, -1, libname); // get _LOADED[libname]
        // if (!lua_istable(L, -1))
        // {                  // not found?
        //     lua_pop(L, 1); // remove previous result
        //     // try global variable (and create one if it does not exist)
        //     if (luaL_findtable(L, LUA_GLOBALSINDEX, libname, size) != NULL)
        //         luaL_error(L, "name conflict for module '%s'", libname);
        //     lua_pushvalue(L, -1);
        //     lua_setfield(L, -3, libname); // _LOADED[libname] = new table
        // }
        // lua_remove(L, -2); // remove _LOADED table
    }

    //region Exception Handling

    private void propagateException() {
        LuaStatus status = LuaStatus.byId(luaW_getstatus(L));
        if (status != LuaStatus.OK) propagateExceptionInner(status);
    }

    private void propagateExceptionInner(LuaStatus status) {
        // If we have one of our own errors on the stack, simply rethrow
        if (toUserData(-1) instanceof LuaError err) throw err;

        // Otherwise, start throwing with the message at index -1
        final String message = isNil(-1) ? null : toStringRepr(-1);
        pop(1); // Pop the message so the stack is clean.

        // In this case the initial trace was in lua, so start with that.
        final LuaError err = new LuaError(
            status,
            stripDefaultErrorPrefix(message)
        );
        err.setStackTrace(mergeBacktrace(L, err.getStackTrace(), false));
        throw err;
    }

    @SuppressWarnings("SameReturnValue")
    private static int pcallErrFunc(MemorySegment L) {
        // There are a few options at this point:
        // 1. we have a LuaError at -1, in which case we should attach the lua trace
        //    if needed or just continue upwards otherwise (leaving at -1).
        final LuaState state = new LuaStateImpl(L);
        if (state.toUserData(-1) instanceof LuaError) {
            return 1; // Keep throwing
        }

        // 2. we have a something at -1, in which case we should use it as the error message.
        //    We replace that with a LuaError object containing the initial lua stacktrace.
        final String message = state.isNil(-1) ? null : state.toStringRepr(-1);
        state.pop(1);

        // In this case the initial trace was in lua, so start with that.
        final LuaError err = new LuaError(stripDefaultErrorPrefix(message));
        err.setStackTrace(mergeBacktrace(L, err.getStackTrace(), true));
        state.newUserData(err);
        return 1;
    }

    static StackTraceElement[] mergeBacktrace(
        MemorySegment L,
        StackTraceElement[] javaTrace,
        boolean startInLua
    ) {
        if (NO_BACKTRACE_MERGE) return javaTrace;

        final List<StackTraceElement> mergedTrace = new ArrayList<>(
            javaTrace.length
        );
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment luaElem = lua_Debug.allocate(arena);

            int luaTraceIndex = 1;

            // If we are starting in lua, read the first part of the trace before any java parts.
            if (startInLua) luaTraceIndex =
                readLuaTracePart(L, luaElem, mergedTrace, luaTraceIndex) - 1;

            for (final StackTraceElement javaElem : javaTrace) {
                // lua_h.lua_pcall is our downcall marker, we expect no other downcalls to occur.
                // At every downcall point, we need to get the 'next' lua trace segment.
                boolean isDowncall =
                    lua_h.class.getName().equals(javaElem.getClassName()) &&
                    "lua_pcall".equals(javaElem.getMethodName());
                if (isDowncall) {
                    luaTraceIndex = readLuaTracePart(
                        L,
                        luaElem,
                        mergedTrace,
                        luaTraceIndex
                    );
                }

                if (!shouldExcludeElement(javaElem)) mergedTrace.add(javaElem);
            }
        }
        return mergedTrace.toArray(new StackTraceElement[0]);
    }

    private static int readLuaTracePart(
        MemorySegment L,
        MemorySegment luaElem,
        List<StackTraceElement> mergedTrace,
        int index
    ) {
        while (lua_getinfo(L, index++, LUA_DEBUG_WHAT, luaElem) != 0) {
            char what = (char) lua_Debug
                .what(luaElem)
                .get(ValueLayout.JAVA_BYTE, 0);
            if (what == 'J') return index;

            boolean isLua = what == 'L';
            String source = null,
                name = "<anonymous>";
            if (isLua) {
                var sourceRef = lua_Debug.source(luaElem);
                if (!sourceRef.equals(MemorySegment.NULL)) source =
                    sourceRef.getString(0);
            }
            var nameRef = lua_Debug.name(luaElem);
            if (!nameRef.equals(MemorySegment.NULL)) name = nameRef.getString(
                0
            );
            int currentLine = !isLua ? -1 : lua_Debug.currentline(luaElem);

            mergedTrace.add(
                new StackTraceElement(
                    // declaring class
                    "lua",
                    // method name
                    name,
                    // file name
                    Objects.requireNonNullElse(source, "<native>"),
                    // line number
                    currentLine
                )
            );
        }
        return index;
    }

    private static boolean shouldExcludeElement(StackTraceElement elem) {
        if (SHOW_COMPLETE_BACKTRACE) return false;

        class Exclusions {

            static final Set<String> SET = Set.of(
                lua_h.class.getName() + "-lua_pcall",
                LuaFuncImpl.CFunctionWrapper.class.getName() + "-apply",
                LuaStateImpl.class.getName() + "-propagateException",
                LuaStateImpl.class.getName() + "-propagateExceptionInner",
                LuaStateImpl.class.getName() + "-pcallErrFunc"
            );
        }
        return Exclusions.SET.contains(
            "%s-%s".formatted(elem.getClassName(), elem.getMethodName())
        );
    }

    static @Nullable String stripDefaultErrorPrefix(@Nullable String raw) {
        if (raw == null) return null;
        return DEFAULT_ERROR_TRACE_REGEX.matcher(raw).replaceFirst("");
    }

    //endregion

    record AllocImpl(MemorySegment handle) implements LuaAlloc {
        public AllocImpl(Function function, Arena arena) {
            this(lua_Alloc.allocate(function, arena));
        }
    }
}

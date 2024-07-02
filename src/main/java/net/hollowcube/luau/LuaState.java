package net.hollowcube.luau;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.nio.ByteBuffer;
import java.util.Map;


// https://pgl.yoyo.org/luai/i/lua_typename
//todo read here: https://github.com/luau-lang/luau/issues/251
public sealed interface LuaState permits LuaStateImpl {

    //todo we should probably configure longjmp
    int USERDATA_TAG_LIMIT = LuaStateImpl.UTAG_LIMIT;
    int LIGHTUSERDATA_TAG_LIMIT = LuaStateImpl.LUTAG_LIMIT;

    /*
    State Manipulation
     */

    static @NotNull LuaState newState() {
        return new LuaStateImpl();
    }

    void close();

    @NotNull LuaState newThread();
    @NotNull LuaState mainThread();
    void resetThread();
    boolean isThreadReset();

    void setGlobal(@NotNull String name);
    int getGlobal(@NotNull String name);

    void sandbox(); // lualib.h
    void sandboxThread(); // lualib.h

    /*
    Basic Stack Manipulation
     */

    int absIndex(int index);
    int getTop();
    void setTop(int index);
    void pushValue(int index);
    void remove(int index);
    void insert(int index);
    void replace(int index);
    boolean checkStack(int size);
    void rawCheckStack(int size);

    void xMove(@NotNull LuaState to, int n);
    void xPush(@NotNull LuaState to, int index);

    /*
    Access Functions (Stack -> Java)
     */

    void pop(int n);

    boolean isNone(int index);
    boolean isNil(int index);
    boolean isNoneOrNil(int index);
    boolean isBoolean(int index);
    boolean isLightUserData(int index);
    boolean isNumber(int index);
    boolean isVector(int index);
    boolean isString(int index);
    boolean isTable(int index);
    boolean isFunction(int index);
    boolean isCFunction(int index);
    boolean isLFunction(int index);
    boolean isUserData(int index);
    boolean isThread(int index);
    boolean isBuffer(int index);
    @NotNull LuaType type(int index);
    @NotNull String typeName(@NotNull LuaType type);
    @NotNull String typeName(int index);

    boolean equal(int index1, int index2);
    boolean rawEqual(int index1, int index2);
    boolean lessThan(int index1, int index2);

    double toNumber(int index);
    int toInteger(int index);
    int toUnsigned(int index);
    float[] toVector(int index);
    boolean toBoolean(int index);
    @NotNull String toString(int index);
    // tostringatom
    // namecallatom
    int objectLength(int index);
    int stringLength(int index);
    @NotNull LuaFunc toCFunction(int index);
    Object toLightUserData(int index); //todo type
    Object toLightUserDataTagged(int index); //todo type
    Object toUserData(int index); //todo type
    Object toUserDataTagged(int index); //todo type
    int userDataTag(int index);
    int lightUserDataTag(int index);
    @NotNull LuaState toThread(int index);
    @NotNull ByteBuffer toBuffer(int index); //todo type
    Object toPointer(int index); //todo type

    /*
    Push Functions (Java -> Stack)
     */

    void pushNil();
    void pushNumber(double n);
    void pushInteger(int n);
    void pushUnsigned(int n);
    void pushVector(float[] v);
    void pushString(@NotNull String s);
    //todo pushcclosurek
    void pushBoolean(boolean b);
    void pushCFunction(@NotNull LuaFunc func, @NotNull String debugName);
    //    void pushCClosure(Object func, @NotNull String debugName, int nup); //todo
    int pushThread();

    void pushLightUserData(Object p); //todo type
    void pushLightUserDataTagged(Object p, int tag); //todo type
    void newUserData(@NotNull Object userdata);
    Object newUserDataTagged(long size, int tag); //todo

    @NotNull ByteBuffer newBuffer(long size);

    /*
    Get Functions (Lua -> Stack)
     */

    @NotNull LuaType getTable(int index);
    @NotNull LuaType getField(int index, @NotNull String key);
    @NotNull LuaType rawGetField(int index, @NotNull String key);
    @NotNull LuaType rawGet(int index);
    @NotNull LuaType rawGetI(int index, int n);
    void newTable();
    void createTable(int arrayCapacity, int fieldCapacity);

    void setReadOnly(int index, boolean enabled);
    boolean getReadOnly(int index);
    void setSafeEnv(int index, boolean enabled);

    int getMetaTable(int objectIndex);
    void getFEnv(int index);

    /*
    Set Functions (Stack -> Lua)
     */

    void setTable(int index);
    void setField(int index, @NotNull String key);
    void rawSetField(int index, @NotNull String key);
    void rawSet(int index);
    void rawSetI(int index, int n);
    void setMetaTable(int objectIndex);
    void setFEnv(int index);

    /*
    `load' and `call' functions (load and run Luau bytecode)
     */

    void load(@NotNull String fileName, byte[] bytecode);
    void call(int argCount, int resultCount);
    void pcall(int argCount, int resultCount); //todo add variant with errFunc

    /*
    Coroutine Functions
     */

    void yield(int resultCount);
    void break_();
    @NotNull LuaStatus resume(@NotNull LuaState from, int argCount);
    @NotNull LuaStatus resumeError(@NotNull LuaState from);
    @NotNull LuaStatus status();
    boolean isYieldable();
    @Nullable Object getThreadData();
    void setThreadData(@Nullable Object data);
    @NotNull LuaCoroutineStatus coroutineStatus(@NotNull LuaState coroutine);

    /*
    Garbage Collection
     */

    int gc(@NotNull LuaGCOp op, int data);

    /*
    Memory Statistics
     */

    void setMemoryCategory(int category);
    long totalBytes(int category);

    /*
    Miscellaneous Functions
     */

    @Contract("-> fail") void error();
    @Contract("_ -> fail") void error(@NotNull String message);
    @Contract("_, _ -> fail") void typeError(int argIndex, @NotNull String typeName); // lualib.h
    @Contract("_, _ -> fail") void argError(int argIndex, @NotNull String message); // lualib.h

    boolean next(int tableIndex);
    int rawIter(int tableIndex, int iter);

    void concat(int n);

    static double clock() {
        return LuaStateImpl.clock();
    }

    //todo encodepointer

    void setUserDataTag(int index, int tag);
    void setUserDataDestructor(int tag, Object destructor); //todo type
    Object getUserDataDestructor(int tag); //todo type

    void setUserDataMetaTable(int tag, int index);
    void getUserDataMetaTable(int tag);

    void setLightUserDataName(int tag, @NotNull String name);
    @UnknownNullability String getLightUserDataName(int tag); //todo nullable?

    void cloneFunction(int index);

    void clearTable(int index);

    //todo getallocf

    /*
    Reference system, can be used to pin objects
     */

    int ref(int index);
    void unref(int ref);
    int getref(int ref);

    /*
    Debug API
     */

    int stackDepth();
//    int getInfo(); //todo args here
//    int getArgument(); //todo args here
    // getLocal
    // setLocal
    // getUpValue
    // setUpValue

    // singleStep
    // breakpoint

    // getcoverage

    // debugtrace

    /*
    Callback API
     */

    @NotNull LuaCallbacks callbacks();

    /*
    Lua Library API (lualib.h)
     */

    void registerLib(@Nullable String name, @NotNull Map<String, LuaFunc> lib);
    boolean getMetaField(int obj, @NotNull String e); //todo what are these fields
    boolean callMeta(int obj, @NotNull String e); //todo what are these fields
    boolean newMetaTable(@NotNull String typeName);
    void getMetaTable(@NotNull String typeName);

    void where();
    void where(int level);

    /* Argument utility functions */

    boolean checkBooleanArg(int argIndex);
    boolean optBooleanArg(int argIndex, boolean defaultValue);
    int checkIntegerArg(int argIndex);
    int optIntegerArg(int argIndex, int defaultValue);
    double checkNumberArg(int argIndex);
    double optNumberArg(int argIndex, float defaultValue);
    int checkUnsignedArg(int argIndex);
    int optUnsignedArg(int argIndex, int defaultValue);
    float[] checkVectorArg(int argIndex);
    float[] optVectorArg(int argIndex, float[] defaultValue);
    @NotNull String checkStringArg(int argIndex);
    @NotNull String optStringArg(int argIndex, @NotNull String defaultValue);
    @NotNull ByteBuffer checkBufferArg(int argIndex);
    //todo checkOptionArg
    @NotNull Object checkUserDataArg(int argIndex, @NotNull String typeName);
    void checkStack(int size, @NotNull String message);
    void checkType(int argIndex, @NotNull LuaType type);
    void checkAny(int argIndex);

    /*
    Builtin Libraries
     */

    void openLibs(@NotNull BuilinLibrary... libraries); //todo no args = open all libs

}


# luacode.h
/Users/matt/Downloads/jextract-22/bin/jextract \
  luau/Compiler/include/luacode.h \
  --output src/generated/java \
  --define-macro LUA_API="extern \"C\"" \
  --target-package "net.hollowcube.luau.internal.compiler" \
  --include-function luau_compile \
  --include-struct lua_CompileOptions

# lualib.h
/Users/matt/Downloads/jextract-22/bin/jextract \
  luau/VM/include/lualib.h \
  --output src/generated/java \
  --target-package "net.hollowcube.luau.internal.vm" \
  --include-function luaL_newstate \
  --include-function luaL_openlibs \
  --include-function luaL_checkinteger

# lua.h
/Users/matt/Downloads/jextract-22/bin/jextract \
  luau/VM/include/lua.h \
  --output src/generated/java \
  --target-package "net.hollowcube.luau.internal.vm" \
  --include-function luau_load \
  --include-function lua_close \
  --include-function lua_pcall \
  --include-function lua_setfield \
  --include-typedef lua_CFunction \
  --include-function lua_pushcclosurek \
  --include-constant LUA_GLOBALSINDEX \
  --include-function lua_pushinteger \
  --include-struct lua_Callbacks

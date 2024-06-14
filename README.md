# luau-java

## Notes

LuaState is not thread safe, but in our case instances are isolated anyway. Below is an (ai generated, could be wrong)
example of running a sandboxed script. We would create a single global state for each world when it loads which has
all the hollowcube library functions (or possibly multiple for different contexts like an entity vs region or
something).

Then for each child (like each entity) we would create a new thread and run the script in that thread.

We would probably do one execution of the script per tick, so doing a yield would be a way to skip to the next tick,
and you could use sleep functions to sleep for a known amount of time I suppose. We would also have some form of
event responders so you could execute lua logic on events like onTick.

It seems like we could implement some logic in lua? Not sure how the performance would compare but it seems like it may
be faster than going back to a library function then into java? Like for a sleep function where it just yields over and
over until the elapsed time. Although a sleep function in java could be smarter about how it sleeps, so could be better.

If a thread execution results in a LUA_OK, I assume it means that the thread finished completely in which case we can
completely stop ticking it and only call events on it.

Things to attach scripts:

- The player
- Entities in general
- Regions (individual blocks like buttons?)
- The world maybe? (for global events of some kind?)

Actions you may want to do

- Sending messages to players
- Editing the world (placing/breaking blocks, opening doors, etc)
- Editing entity attributes arbitrarily
- Entity interactions (left/right click)

Should require explicit event registration in some way (probably a function)

Could we handle NPCs with this? like create some sort of stateful dialog system?

- probably could do it with interact events sending a message to the player and some
  internal state machine to handle the interactions. Could get fancy for the hub and provide
  an api for creating the fancy dialog guis rather than just sending the message.
- Not sure we would want to expose the fancy dialogs to peoples maps, but maybe? Not sure if there
  is any harm in doing it :)

HOW DO SCRIPTS SERIALIZE VALUES? IS IT ATTACHED TO THE PLAYER? HOW DO WE LIMIT THIS?

- Probably depends on the map type, would be cool if it was seamless
    - For exmaple single player maps (the only one we have for now) could be saved in the player state.
    - Coop maps could be saved in the world state.
    - In some coop maps you might want state saved to a player anyway, so perhaps always expose a player
      and world state system in lua, but save the world state in the player anyway for singleplayer maps.

FIRST TARGET:

- Entities with dialogs?
- In the hub world only we would want to expose special functions for actions like
  showing a merchant gui, setting a quest, or anything else that would be hub specific.
  This may also happen for OC, so would be good to have a nice system for supplying a special module
- Something like `hub.showMerchantGui(player)` or maybe something
  like `script.Parent.World as Hub : showMerchantGui(player)`

```c
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>

// Function to run a script in a sandboxed thread
void run_script(lua_State* L, const char* script) {
    // Create a new thread (represents a script's execution context)
    lua_State* thread = lua_newthread(L);
    
    // Apply sandboxing to the thread
    luaL_sandboxthread(thread);

    // Load and run the script
    if (luaL_dostring(thread, script) != LUA_OK) {
        fprintf(stderr, "Error: %s\n", lua_tostring(thread, -1));
    }

    // Clean up (in a real scenario, you might want to keep threads around)
    lua_pop(L, 1);  // remove thread from main state's stack
}

int main() {
    // Create the global Lua state
    lua_State* L = luaL_newstate();
    luaL_openlibs(L);  // Load standard libraries

    // Apply sandboxing to the global state
    luaL_sandbox(L);

    // Example scripts
    const char* script1 = "print('Hello from script 1!')";
    const char* script2 = "print('Hello from script 2!'); _G.shared = 42";
    const char* script3 = "print('Script 3 sees shared as: ' .. tostring(_G.shared))";

    // Run scripts in sandboxed threads
    run_script(L, script1);
    run_script(L, script2);
    run_script(L, script3);

    // Clean up
    lua_close(L);

    return 0;
}
```




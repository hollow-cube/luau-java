package net.hollowcube.luau;

import org.junit.jupiter.api.Test;

public class TestLuaStateInit {

    @Test
    void sanityInitClose() {
        LuaState.newState().close();
    }
}

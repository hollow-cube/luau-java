package net.hollowcube.luau;

import org.junit.jupiter.api.extension.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.foreign.Arena;

@ExtendWith(LuaStateParam.LuaStateResolver.class)
@ExtendWith(LuaStateParam.ArenaResolver.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LuaStateParam {

    final class LuaStateResolver implements ParameterResolver, AfterEachCallback {
        private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
                TestLuaState.class);
        private static final String KEY = "state";

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType() == LuaState.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            LuaState existing = store.get(KEY, LuaState.class);
            if (existing != null) return existing;

            LuaState state = LuaState.newState();
            store.put(KEY, state);
            return state;
        }

        @Override
        public void afterEach(ExtensionContext context) {
            ExtensionContext.Store store = context.getStore(NAMESPACE);
            LuaState state = store.remove(KEY, LuaState.class);
            if (state != null) state.close();
        }
    }

    final class ArenaResolver implements ParameterResolver, AfterEachCallback {
        private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
                TestLuaState.class);
        private static final String KEY = "arena";

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType() == Arena.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            Arena existing = store.get(KEY, Arena.class);
            if (existing != null) return existing;

            Arena state = Arena.ofConfined();
            store.put(KEY, state);
            return state;
        }

        @Override
        public void afterEach(ExtensionContext context) {
            ExtensionContext.Store store = context.getStore(NAMESPACE);
            Arena state = store.remove(KEY, Arena.class);
            if (state != null) state.close();
        }
    }
}

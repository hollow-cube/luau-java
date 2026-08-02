package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Drives [Navigator] directly with a scripted [RequireResolver], so every navigation
/// branch and error message can be exercised without a real module tree.
@LuaStateParam
class TestNavigator {

    /// A [RequireResolver] which returns canned results and records every call it receives.
    ///
    /// Each result may either be queued (one entry per call, in order) or left empty to
    /// fall back to the corresponding default.
    static final class ScriptedResolver implements RequireResolver {
        final List<String> calls = new ArrayList<>();

        Result reset = Result.PRESENT;
        final Deque<Result> toParent = new ArrayDeque<>();
        Result toParentDefault = Result.PRESENT;
        final Deque<Result> toChild = new ArrayDeque<>();
        Result toChildDefault = Result.PRESENT;
        final Deque<Result> configStatus = new ArrayDeque<>();
        Result configStatusDefault = Result.NOT_FOUND;
        Result jumpToAlias = Result.PRESENT;
        final Map<String, String> aliases = new HashMap<>();

        @Override
        public Result reset(LuaState state, String requirerChunkName) {
            calls.add("reset:" + requirerChunkName);
            return reset;
        }

        @Override
        public Result toParent(LuaState state) {
            calls.add("toParent");
            return toParent.isEmpty() ? toParentDefault : toParent.poll();
        }

        @Override
        public Result toChild(LuaState state, String name) {
            calls.add("toChild:" + name);
            return toChild.isEmpty() ? toChildDefault : toChild.poll();
        }

        @Override
        public Result jumpToAlias(LuaState state, String aliasPath) {
            calls.add("jumpToAlias:" + aliasPath);
            return jumpToAlias;
        }

        @Override
        public Result getConfigStatus(LuaState state) {
            calls.add("getConfigStatus");
            return configStatus.isEmpty() ? configStatusDefault : configStatus.poll();
        }

        @Override
        public @Nullable String resolveAlias(LuaState state, String alias) {
            calls.add("resolveAlias:" + alias);
            return aliases.get(alias);
        }

        @Override
        public @Nullable Module getModule(LuaState state) {
            calls.add("getModule");
            return null;
        }

        @Override
        public int load(LuaState state, String path, String chunkName, String loadName) {
            calls.add("load:" + path);
            return 0;
        }
    }

    private static Navigator navigator(ScriptedResolver resolver, LuaState state) {
        return new Navigator(resolver, state, "requirer.luau");
    }

    private static void assertSuccess(Navigator.Status status) {
        assertInstanceOf(Navigator.Status.Success.class, status);
    }

    private static void assertError(String expected, Navigator.Status status) {
        var reported = assertInstanceOf(Navigator.Status.ErrorReported.class, status);
        assertEquals(expected, reported.error());
    }

    //region reset

    @Test
    void resetNotFound(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.reset = RequireResolver.Result.NOT_FOUND;

        assertError(
                "could not reset to requiring context",
                navigator(resolver, state).navigate("./mod")
        );
        assertEquals(List.of("reset:requirer.luau"), resolver.calls);
    }

    @Test
    void resetAmbiguous(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.reset = RequireResolver.Result.AMBIGUOUS;

        assertError(
                "could not reset to requiring context (ambiguous)",
                navigator(resolver, state).navigate("./mod")
        );
    }

    //endregion

    //region path type dispatch

    @Test
    void unknownPathType(LuaState state) {
        var resolver = new ScriptedResolver();

        assertError(
                "require path must start with a valid prefix: ./, ../, or @",
                navigator(resolver, state).navigate("mod")
        );
        // The requirer is still reset before the path type is inspected.
        assertEquals(List.of("reset:requirer.luau"), resolver.calls);
    }

    @Test
    void unknownPathTypeForBarePath(LuaState state) {
        var resolver = new ScriptedResolver();

        assertError(
                "require path must start with a valid prefix: ./, ../, or @",
                navigator(resolver, state).navigate("/absolute/path")
        );
    }

    @Test
    void relativePathVisitsParentThenChildren(LuaState state) {
        var resolver = new ScriptedResolver();

        assertSuccess(navigator(resolver, state).navigate("./sub/mod"));
        assertEquals(
                List.of("reset:requirer.luau", "toParent", "toChild:sub", "toChild:mod"),
                resolver.calls
        );
    }

    @Test
    void relativePathNormalisesBackslashes(LuaState state) {
        var resolver = new ScriptedResolver();

        assertSuccess(navigator(resolver, state).navigate(".\\sub\\mod"));
        assertEquals(
                List.of("reset:requirer.luau", "toParent", "toChild:sub", "toChild:mod"),
                resolver.calls
        );
    }

    @Test
    void relativePathParentComponents(LuaState state) {
        var resolver = new ScriptedResolver();

        assertSuccess(navigator(resolver, state).navigate("../../mod"));
        // One toParent for the requiring context, then one per ".." component.
        assertEquals(
                List.of(
                        "reset:requirer.luau",
                        "toParent",
                        "toParent",
                        "toParent",
                        "toChild:mod"
                ),
                resolver.calls
        );
    }

    @Test
    void relativePathIgnoresEmptyAndDotComponents(LuaState state) {
        var resolver = new ScriptedResolver();

        assertSuccess(navigator(resolver, state).navigate(".///./mod/"));
        assertEquals(
                List.of("reset:requirer.luau", "toParent", "toChild:mod"),
                resolver.calls
        );
    }

    //endregion

    //region navigation failures

    @Test
    void parentOfRequiringContextNotFound(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "could not get parent of requiring context",
                navigator(resolver, state).navigate("./mod")
        );
    }

    @Test
    void parentOfRequiringContextAmbiguous(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.AMBIGUOUS;

        assertError(
                "could not get parent of requiring context (ambiguous)",
                navigator(resolver, state).navigate("./mod")
        );
    }

    @Test
    void parentOfComponentNotFound(LuaState state) {
        var resolver = new ScriptedResolver();
        // The first toParent (the requiring context) succeeds, the one for the ".."
        // component after "sub" does not.
        resolver.toParent.add(RequireResolver.Result.PRESENT);
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "could not get parent of component \"sub\"",
                navigator(resolver, state).navigate("./sub/../mod")
        );
    }

    @Test
    void parentOfComponentAmbiguous(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParent.add(RequireResolver.Result.PRESENT);
        resolver.toParentDefault = RequireResolver.Result.AMBIGUOUS;

        assertError(
                "could not get parent of component \"sub\" (ambiguous)",
                navigator(resolver, state).navigate("./sub/../mod")
        );
    }

    @Test
    void childNotFound(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toChildDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "could not resolve child component \"missing\"",
                navigator(resolver, state).navigate("./missing/mod")
        );
        assertEquals(
                List.of("reset:requirer.luau", "toParent", "toChild:missing"),
                resolver.calls
        );
    }

    @Test
    void childAmbiguous(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toChildDefault = RequireResolver.Result.AMBIGUOUS;

        assertError(
                "could not resolve child component \"missing\" (ambiguous)",
                navigator(resolver, state).navigate("./missing")
        );
    }

    //endregion

    //region aliases

    /// The alias part of "@library/mod", i.e. everything between the '@' and the first '/'.
    private static final String ALIAS = "library";

    @Test
    void aliasNotFoundAtRoot(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "@" + ALIAS + " is not a valid alias",
                navigator(resolver, state).navigate("@library/mod")
        );
        assertEquals(List.of("reset:requirer.luau", "toParent"), resolver.calls);
    }

    @Test
    void aliasSearchWalksToParentUntilConfigFound(LuaState state) {
        var resolver = new ScriptedResolver();
        // Two ancestors without a config, then the root.
        resolver.toParent.add(RequireResolver.Result.PRESENT);
        resolver.toParent.add(RequireResolver.Result.PRESENT);
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "@" + ALIAS + " is not a valid alias",
                navigator(resolver, state).navigate("@library/mod")
        );
        assertEquals(
                List.of(
                        "reset:requirer.luau",
                        "toParent",
                        "getConfigStatus",
                        "toParent",
                        "getConfigStatus",
                        "toParent"
                ),
                resolver.calls
        );
    }

    @Test
    void aliasSearchAncestryAmbiguous(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.AMBIGUOUS;

        assertError(
                "could not navigate up the ancestry chain during search for alias \""
                        + ALIAS + "\" (ambiguous)",
                navigator(resolver, state).navigate("@library/mod")
        );
    }

    @Test
    void aliasSearchConfigAmbiguous(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.configStatusDefault = RequireResolver.Result.AMBIGUOUS;

        assertError(
                "could not resolve alias \"" + ALIAS
                        + "\" (ambiguous configuration file)",
                navigator(resolver, state).navigate("@library/mod")
        );
        assertEquals(
                List.of("reset:requirer.luau", "toParent", "getConfigStatus"),
                resolver.calls
        );
    }

    @Test
    void aliasIsResolvedAgainstTheFirstConfigFound(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.configStatus.add(RequireResolver.Result.NOT_FOUND);
        resolver.configStatusDefault = RequireResolver.Result.PRESENT;
        resolver.aliases.put(ALIAS, "./libs/library");

        // The first ancestor has no config, so the search walks up one more level before
        // resolving the alias against the config it finds there.
        assertSuccess(navigator(resolver, state).navigate("@library/mod"));
        assertEquals(
                List.of(
                        "reset:requirer.luau",
                        "toParent",
                        "getConfigStatus",
                        "toParent",
                        "getConfigStatus",
                        "resolveAlias:" + ALIAS,
                        "toChild:libs",
                        "toChild:library",
                        "toChild:mod"
                ),
                resolver.calls
        );
    }

    @Test
    void aliasCaseIsNormalised(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "@" + ALIAS + " is not a valid alias",
                navigator(resolver, state).navigate("@LIBRARY/mod")
        );
    }

    //endregion

    //region known bugs

    @Test
    void aliasNameIsNotTruncated(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertError(
                "@library is not a valid alias",
                navigator(resolver, state).navigate("@library/mod")
        );
    }

    @Test
    void aliasResolvesToRelativePath(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.configStatusDefault = RequireResolver.Result.PRESENT;
        resolver.aliases.put("library", "./libs/library");

        assertSuccess(navigator(resolver, state).navigate("@library/mod"));
        assertEquals(
                List.of(
                        "reset:requirer.luau",
                        "toParent",
                        "getConfigStatus",
                        "resolveAlias:library",
                        "toChild:libs",
                        "toChild:library",
                        "toChild:mod"
                ),
                resolver.calls
        );
    }

    @Test
    void unknownAliasInPresentConfigIsReported(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.configStatusDefault = RequireResolver.Result.PRESENT;

        assertError(
                "@library is not a valid alias",
                navigator(resolver, state).navigate("@library/mod")
        );
    }

    @Test
    void aliasCycleIsReported(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.configStatusDefault = RequireResolver.Result.PRESENT;
        resolver.aliases.put("first", "@second/x");
        resolver.aliases.put("second", "@first/x");

        assertError(
                "detected alias cycle (@first -> @second -> @first)",
                navigator(resolver, state).navigate("@first/mod")
        );
    }

    @Test
    void selfAliasNavigatesFromRequirer(LuaState state) {
        var resolver = new ScriptedResolver();
        resolver.toParentDefault = RequireResolver.Result.NOT_FOUND;

        assertSuccess(navigator(resolver, state).navigate("@self/mod"));
        assertEquals(
                List.of(
                        "reset:requirer.luau",
                        "toParent",
                        "reset:requirer.luau",
                        "toChild:mod"
                ),
                resolver.calls
        );
    }

    //endregion
}

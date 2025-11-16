package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Navigator {

    public sealed interface Status {
        record Success() implements Status {}

        record ErrorReported(String error) implements Status {}
    }

    private final RequireConfiguration<?> lrc;
    private final LuaState state;
    private final String requirerChunkName;

    public Navigator(RequireConfiguration<?> lrc, LuaState state, String requirerChunkName) {
        this.lrc = lrc;
        this.state = state;
        this.requirerChunkName = requirerChunkName;
    }

    public Status navigate(String path) {
        path = path.replaceAll("\\\\", "/");

        String error = resetToRequirer();
        if (error != null) return new Status.ErrorReported(error);

        error = navigateImpl(path);
        if (error != null) return new Status.ErrorReported(error);

        return new Status.Success();
    }

    private @Nullable String navigateImpl(String path) {
        final PathType pathType = PathType.fromString(path);

        return switch (pathType) {
            case UNSUPPORTED -> "require path must start with a valid prefix: ./, ../, or @";
            case ALIASED -> {
                final String alias = extractAlias(path).toLowerCase(Locale.ROOT);

                // todo this map only ever gets a single value i think.
                Map<String, String> aliases = new HashMap<>();
                String error = navigateToAndPopulateConfig(alias, aliases);
                if (error != null) yield error;

                if (!aliases.containsKey(alias)) {
                    if (!alias.equals("self"))
                        yield "@" + alias + " is not a valid alias";

                    // If the alias is "@self", we reset to the requirer's context and
                    // navigate directly from there.
                    error = resetToRequirer();
                    if (error != null) yield error;
                    error = navigateThroughPath(path);
                    yield error;
                }

                error = navigateToAlias(path, aliases, new AliasCycleTracker());
                if (error != null) yield error;
                error = navigateThroughPath(path);
                yield error;
            }
            case RELATIVE_TO_CURRENT, RELATIVE_TO_PARENT -> {
                String error = navigateToParent(null);
                if (error != null) yield error;
                error = navigateThroughPath(path);
                yield error;
            }
        };
    }

    private @Nullable String navigateThroughPath(String path) {
        Map.Entry<String, String> components = PathType.split(path);
        if (path.startsWith("@")) {
            // If the path is aliased, we ignore the alias: this function assumes
            // that navigation to an alias is handled by the caller.
            components = PathType.split(components.getValue());
        }

        String previous = null;
        while (!(components.getKey().isEmpty() && components.getValue().isEmpty())) {
            if (".".equals(components.getKey()) || components.getKey().isEmpty()) {
                components = PathType.split(components.getValue());
                continue;
            }

            String error = "..".equals(components.getKey())
                    ? navigateToParent(previous)
                    : navigateToChild(components.getKey());
            if (error != null) return error;

            previous = components.getKey();
            components = PathType.split(components.getValue());
        }

        return null;
    }

    private @Nullable String navigateToAlias(String alias, Map<String, @Nullable String> aliases, AliasCycleTracker cycleTracker) {
        final String value = aliases.get(alias).toLowerCase(Locale.ROOT); //todo why isnt this a nullability issue?
        final PathType pathType = PathType.fromString(value);

        return switch (pathType) {
            case RELATIVE_TO_CURRENT, RELATIVE_TO_PARENT -> navigateThroughPath(value);
            case ALIASED -> {
                String error = cycleTracker.add(alias);
                if (error != null) yield error;

                final String nextAlias = extractAlias(value);
                if (aliases.containsKey(nextAlias)) {
                    error = navigateToAlias(nextAlias, aliases, cycleTracker);
                    if (error != null) yield error;
                } else {
                    Map<String, String> parentAliases = new HashMap<>();
                    error = navigateToAndPopulateConfig(nextAlias, parentAliases);
                    if (error != null) yield error;

                    yield parentAliases.containsKey(nextAlias)
                            ? navigateToAlias(nextAlias, parentAliases, new AliasCycleTracker())
                            : "@" + nextAlias + " is not a valid alias";
                }

                yield navigateThroughPath(value);
            }
            case UNSUPPORTED -> jumpToAlias(value);
        };
    }

    private @Nullable String navigateToAndPopulateConfig(String desiredAlias, Map<String, @Nullable String> aliases) {
        while (!aliases.containsKey(desiredAlias)) {
            final NavigationResult result = lrc.toParent(state, null);
            if (result == NavigationResult.AMBIGUOUS)
                return "could not navigate up the ancestry chain during search for alias \"" + desiredAlias + "\" (ambiguous)";
            if (result == NavigationResult.NOT_FOUND)
                break; // Not treated as an error: interpreted as reaching the root.

            ConfigStatus status = lrc.getConfigStatus(state, null);
            if (status == ConfigStatus.ABSENT) continue;
            if (status == ConfigStatus.AMBIGUOUS)
                return "could not resolve alias \"" + desiredAlias + "\" (ambiguous configuration file)";

            aliases.put(desiredAlias, lrc.getAlias(state, null, desiredAlias));
            break;
        }

        return null;
    }

    private @Nullable String resetToRequirer() {
        final NavigationResult result = lrc.reset(state, null, requirerChunkName);
        if (result == NavigationResult.SUCCESS) return null;

        String errorMessage = "could not reset to requiring context";
        if (result == NavigationResult.AMBIGUOUS) errorMessage += " (ambiguous)";
        return errorMessage;
    }

    private @Nullable String jumpToAlias(String aliasPath) {
        final NavigationResult result = lrc.jumpToAlias(state, null, aliasPath);
        if (result == NavigationResult.SUCCESS) return null;

        String errorMessage = "could not jump to alias \"" + aliasPath + "\"";
        if (result == NavigationResult.AMBIGUOUS) errorMessage += " (ambiguous)";
        return errorMessage;
    }

    private @Nullable String navigateToParent(@Nullable String previousComponent) {
        final NavigationResult result = lrc.toParent(state, null);
        if (result == NavigationResult.SUCCESS) return null;

        String errorMessage = previousComponent != null
                ? "could not get parent of component \"" + previousComponent + "\""
                : "could not get parent of requiring context";
        if (result == NavigationResult.AMBIGUOUS) errorMessage += " (ambiguous)";
        return errorMessage;
    }

    private @Nullable String navigateToChild(String component) {
        final NavigationResult result = lrc.toChild(state, null, component);
        if (result == NavigationResult.SUCCESS) return null;

        String errorMessage = "could not resolve child component \"" + component + "\"";
        if (result == NavigationResult.AMBIGUOUS) errorMessage += " (ambiguous)";
        return errorMessage;
    }

    private static String extractAlias(String path) {
        // To ignore the '@' alias prefix when processing the alias
        int aliasStartPos = 1;

        // If a directory separator was found, the length of the alias is the
        // distance between the start of the alias and the separator. Otherwise,
        // the whole string after the alias symbol is the alias.
        int aliasLength = path.indexOf('/');
        if (aliasLength != -1) aliasLength -= aliasStartPos;

        return path.substring(aliasStartPos, aliasLength);
    }

}

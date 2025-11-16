package net.hollowcube.luau.require;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AliasCycleTracker {
    private final Set<String> seen = new HashSet<>();
    private final List<String> ordered = new ArrayList<>();

    public @Nullable String add(String alias) {
        if (!seen.add(alias))
            return "detected alias cycle (%s)".formatted(getStringifiedCycle(alias));

        ordered.add(alias);
        return null;
    }

    private String getStringifiedCycle(String repeated) {
        final StringBuilder result = new StringBuilder();
        boolean inCycle = false;
        for (final String item : ordered) {
            if (inCycle) {
                result.append(" -> ");
                result.append("@").append(item);
            }
            if (item.equals(repeated)) {
                inCycle = true;
                result.append("@").append(item);
            }
        }
        result.append(" -> ").append("@").append(repeated);
        return result.toString();
    }
}

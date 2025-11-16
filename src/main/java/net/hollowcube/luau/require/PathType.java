package net.hollowcube.luau.require;

import java.util.Map;

public enum PathType {
    RELATIVE,
    ALIASED,
    UNKNOWN,
    ;

    public static PathType fromString(String path) {
        if (path.startsWith("./") || path.startsWith("../"))
            return RELATIVE;
        if (path.startsWith("@")) return ALIASED;
        return UNKNOWN;
    }

    public static Map.Entry<String, String> split(String path) {
        int pos = path.indexOf('/');
        if (pos == -1) return Map.entry(path, "");
        return Map.entry(path.substring(0, pos), path.substring(pos + 1));
    }
}

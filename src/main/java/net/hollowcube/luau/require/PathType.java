package net.hollowcube.luau.require;

import java.util.Map;

public enum PathType {
    RELATIVE_TO_CURRENT,
    RELATIVE_TO_PARENT,
    ALIASED,
    UNSUPPORTED,
    ;

    public static PathType fromString(String path) {
        if (path.startsWith("./")) return RELATIVE_TO_CURRENT;
        if (path.startsWith("../")) return RELATIVE_TO_PARENT;
        if (path.startsWith("@")) return ALIASED;
        return UNSUPPORTED;
    }

    public static Map.Entry<String, String> split(String path) {
        int pos = path.indexOf('/');
        if (pos == -1) return Map.entry(path, "");
        return Map.entry(path.substring(0, pos), path.substring(pos + 1));
    }
}

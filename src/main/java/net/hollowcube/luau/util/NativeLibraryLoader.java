package net.hollowcube.luau.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NativeLibraryLoader {

    /// Loads each library in order, skipping any already loaded. Everything native lives in
    /// a single `luaujava` library, but several entry points can be the first one touched
    /// (a state, the compiler, a global ref), so they all ask for it.
    public static synchronized void loadLibrary(String... names) {
        for (final String name : names) {
            if (!LOADED.add(name)) continue;
            if (!loadEmbeddedLibrary(name)) {
                System.loadLibrary(name);
            }
        }
    }

    private static final Set<String> LOADED = new HashSet<>();

    private static final Path NATIVES_DIR;

    static {
        try {
            NATIVES_DIR = Files.createTempDirectory("luau-natives");
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to create temporary directory for native libraries",
                e
            );
        }
    }

    private static boolean loadEmbeddedLibrary(String name) {
        String lib = String.format(
            "/net/hollowcube/luau/%s/%s/%s",
            currentOperatingSystem(),
            currentArchitecture(),
            System.mapLibraryName(name)
        );

        final URL innerPath = NativeLibraryLoader.class.getResource(lib);
        if (innerPath == null) return false;

        final Path targetPath = NATIVES_DIR.resolve(
            System.mapLibraryName(name)
        );
        try (InputStream in = innerPath.openStream()) {
            Files.copy(in, targetPath);
            System.load(targetPath.toString());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String currentOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return "windows";
        } else if (osName.contains("mac os x")) {
            return "macos";
        } else if (
            osName.contains("nix") ||
            osName.contains("nux") ||
            osName.contains("aix")
        ) {
            return "linux";
        } else throw new UnsupportedOperationException(
            "Unsupported OS: " + osName
        );
    }

    private static String currentArchitecture() {
        String archName = System.getProperty("os.arch").toLowerCase();
        if (archName.contains("amd64") || archName.contains("x86_64")) {
            return "x64";
        } else if (archName.contains("aarch64") || archName.contains("arm64")) {
            return "arm64";
        } else throw new UnsupportedOperationException(
            "Unsupported architecture: " + archName
        );
    }
}

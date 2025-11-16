package net.hollowcube.luau.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NativeLibraryLoader {

    public static void loadLibrary(String name) {
        if (!loadEmbeddedLibrary(name)) {
            System.loadLibrary(name);
        }
    }

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

package yt.ren.slate.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public class Desktop {
    public enum OperatingSystem {
        WINDOWS,
        MACOS,
        LINUX,
        OTHER
    }

    public static final OperatingSystem OS = detect();

    private static OperatingSystem detect() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return OperatingSystem.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return OperatingSystem.MACOS;
        }
        if (name.contains("linux") || name.contains("nux") || name.contains("bsd")) {
            return OperatingSystem.LINUX;
        }
        return OperatingSystem.OTHER;
    }

    public static boolean sdl() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("pylon") || loader.isModLoaded("lenis");
    }

    public static void open(Path path) throws IOException {
        if (sdl()) {
            SdlPlatform.open(path.toUri().toString());
            return;
        }
        new ProcessBuilder(opener(), path.toAbsolutePath().toString()).start();
    }

    private static String opener() {
        switch (OS) {
            case WINDOWS:
                return "explorer.exe";
            case MACOS:
                return "open";
            default:
                return "xdg-open";
        }
    }
}

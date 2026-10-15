package yt.ren.slate.ui.backend;

import net.fabricmc.loader.api.FabricLoader;
import yt.ren.slate.ui.backend.gl.GlUIBackend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class UIBackends {
    public static final String ENTRYPOINT = "slate:ui_backend";
    private static final String PROPERTY = "slate.ui.backend";
    private static final String OPENGL = "opengl";

    private static final UIBackendProvider GL = new UIBackendProvider() {
        @Override
        public String name() {
            return OPENGL;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean available() {
            return !glReplaced();
        }

        @Override
        public UIBackend create() {
            return new GlUIBackend();
        }
    };

    private static boolean glReplaced() {
        return FabricLoader.getInstance().isModLoaded("aurelium");
    }

    private static List<UIBackendProvider> providers() {
        List<UIBackendProvider> providers = new ArrayList<>();
        providers.add(GL);
        try {
            providers.addAll(FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, UIBackendProvider.class));
        } catch (RuntimeException | LinkageError exception) {
            System.err.println("[Slate] UI backend providers failed to load: " + exception);
        }
        providers.sort(Comparator.comparingInt(UIBackendProvider::priority).reversed());
        return providers;
    }

    public static UIBackend create() {
        String requested = System.getProperty(PROPERTY, "").toLowerCase(Locale.ROOT);
        for (UIBackendProvider provider : providers()) {
            if ((requested.isEmpty() || requested.equals(provider.name())) && provider.available()) {
                return provider.create();
            }
        }
        System.err.println("[Slate] No UI backend is available; the UI is disabled.");
        return null;
    }
}

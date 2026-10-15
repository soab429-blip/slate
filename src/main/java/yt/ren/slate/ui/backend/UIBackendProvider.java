package yt.ren.slate.ui.backend;

public interface UIBackendProvider {
    String name();

    int priority();

    boolean available();

    UIBackend create();
}

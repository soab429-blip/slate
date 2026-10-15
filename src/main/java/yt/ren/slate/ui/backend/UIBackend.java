package yt.ren.slate.ui.backend;

import yt.ren.slate.ui.vector.VectorContext;

public interface UIBackend {
    String name();

    VectorContext createVector(int flags);

    RenderTarget createTarget(VectorContext vector, int width, int height);

    void destroyTarget(RenderTarget target);

    void beginTarget(RenderTarget target);

    void endTarget(RenderTarget target);

    void pushState();

    void popState();
}

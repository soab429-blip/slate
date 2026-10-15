package yt.ren.slate.ui;

public interface UISurface {
    boolean resize(int width, int height);

    void setScale(float scale);

    void beginFrame(double now);

    void finishFrame();

    void beginPresent(double now);

    void present();

    void finishPresent();
}

package yt.ren.slate.ui;

public class UIRect {
    public float x;
    public float y;
    public float width;
    public float height;

    public UIRect(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public UIRect copy() {
        return new UIRect(x, y, width, height);
    }

    public void set(UIRect o) {
        x = o.x;
        y = o.y;
        width = o.width;
        height = o.height;
    }
}

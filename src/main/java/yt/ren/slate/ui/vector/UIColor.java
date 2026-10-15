package yt.ren.slate.ui.vector;

public class UIColor {
    public float red;
    public float green;
    public float blue;
    public float alpha;

    public UIColor() {
    }

    public UIColor(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public UIColor copy() {
        return new UIColor(red, green, blue, alpha);
    }

    public void set(UIColor other) {
        red = other.red;
        green = other.green;
        blue = other.blue;
        alpha = other.alpha;
    }

    public void set(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }
}

package yt.ren.slate.ui.vector;

import java.util.Arrays;

public class VectorPaint {
    public final float[] transform = new float[6];
    public final float[] extent = new float[2];
    public float radius;
    public float feather;
    public final UIColor inner = new UIColor();
    public final UIColor outer = new UIColor();
    public int image;

    public void set(VectorPaint other) {
        System.arraycopy(other.transform, 0, transform, 0, 6);
        extent[0] = other.extent[0];
        extent[1] = other.extent[1];
        radius = other.radius;
        feather = other.feather;
        inner.set(other.inner);
        outer.set(other.outer);
        image = other.image;
    }

    public VectorPaint copy() {
        VectorPaint paint = new VectorPaint();
        paint.set(this);
        return paint;
    }

    public void clear() {
        Arrays.fill(transform, 0.0f);
        extent[0] = 0.0f;
        extent[1] = 0.0f;
        radius = 0.0f;
        feather = 0.0f;
        inner.set(0.0f, 0.0f, 0.0f, 0.0f);
        outer.set(0.0f, 0.0f, 0.0f, 0.0f);
        image = 0;
    }
}

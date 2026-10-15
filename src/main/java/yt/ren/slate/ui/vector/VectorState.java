package yt.ren.slate.ui.vector;

import java.util.Arrays;

public class VectorState {
    public int sourceRgb;
    public int destinationRgb;
    public int sourceAlpha;
    public int destinationAlpha;
    public int shapeAntialias;
    public final VectorPaint fill = new VectorPaint();
    public final VectorPaint stroke = new VectorPaint();
    public float strokeWidth;
    public float miterLimit;
    public int lineJoin;
    public int lineCap;
    public float alpha;
    public final float[] transform = new float[6];

    public final float[] scissorTransform = new float[6];
    public final float[] scissorExtent = new float[2];
    public float fontSize;
    public float letterSpacing;
    public float lineHeight;
    public float fontBlur;
    public int textAlignment;
    public int font;

    public void set(VectorState o) {
        sourceRgb = o.sourceRgb;
        destinationRgb = o.destinationRgb;
        sourceAlpha = o.sourceAlpha;
        destinationAlpha = o.destinationAlpha;
        shapeAntialias = o.shapeAntialias;
        fill.set(o.fill);
        stroke.set(o.stroke);
        strokeWidth = o.strokeWidth;
        miterLimit = o.miterLimit;
        lineJoin = o.lineJoin;
        lineCap = o.lineCap;
        alpha = o.alpha;
        System.arraycopy(o.transform, 0, transform, 0, 6);
        System.arraycopy(o.scissorTransform, 0, scissorTransform, 0, 6);
        scissorExtent[0] = o.scissorExtent[0];
        scissorExtent[1] = o.scissorExtent[1];
        fontSize = o.fontSize;
        letterSpacing = o.letterSpacing;
        lineHeight = o.lineHeight;
        fontBlur = o.fontBlur;
        textAlignment = o.textAlignment;
        font = o.font;
    }

    public void clear() {
        sourceRgb = 0;
        destinationRgb = 0;
        sourceAlpha = 0;
        destinationAlpha = 0;
        shapeAntialias = 0;
        fill.clear();
        stroke.clear();
        strokeWidth = 0.0f;
        miterLimit = 0.0f;
        lineJoin = 0;
        lineCap = 0;
        alpha = 0.0f;
        Arrays.fill(transform, 0.0f);
        Arrays.fill(scissorTransform, 0.0f);
        scissorExtent[0] = 0.0f;
        scissorExtent[1] = 0.0f;
        fontSize = 0.0f;
        letterSpacing = 0.0f;
        lineHeight = 0.0f;
        fontBlur = 0.0f;
        textAlignment = 0;
        font = 0;
    }
}

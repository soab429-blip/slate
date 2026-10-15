package yt.ren.slate.ui.vector;

import yt.ren.slate.ui.font.FontQuad;
import yt.ren.slate.ui.font.FontStash;
import yt.ren.slate.ui.font.FontTextIterator;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class VectorContext {
    private static final float EPSILON = Float.intBitsToFloat(0x358637bd);
    private static final float PI = Float.intBitsToFloat(0x40490fdb);
    private static final float TWO_PI = Float.intBitsToFloat(0x40c90fdb);
    private static final double DETERMINANT_THRESHOLD = Double.longBitsToDouble(0x3eb0c6f7a0b5ed8dL);
    private static final int[] COMPOSITE_SOURCE = {2, 256, 512, 256, 512, 1, 1, 512, 2, 2, 512};
    private static final int[] COMPOSITE_DESTINATION = {128, 1, 1, 128, 2, 64, 128, 64, 2, 1, 128};

    public final VectorRenderer renderer;
    public float[] commands;
    public int commandCapacity;
    public int commandCount;
    public float commandX;
    public float commandY;
    public final VectorState[] states = new VectorState[32];
    public int stateCount;
    public final VectorCache cache;
    public float tessellationTolerance;
    public float distanceTolerance;
    public float fringeWidth;
    public float devicePixelRatio;
    public FontStash fontContext;
    public final int[] fontImages = new int[4];
    public int fontImageIndex;
    public int drawCallCount;
    public int fillTriangleCount;
    public int strokeTriangleCount;
    public int textTriangleCount;

    private VectorContext(VectorRenderer renderer) {
        this.renderer = renderer;
        for (int i = 0; i < states.length; ++i) {
            states[i] = new VectorState();
        }
        commands = new float[256];
        commandCapacity = 256;
        cache = new VectorCache();
    }

    public static VectorContext create(VectorRenderer renderer) {
        VectorContext c = new VectorContext(renderer);
        c.commandCount = 0;
        c.stateCount = 1;
        c.resetState();
        c.tessellationTolerance = 0.25f;
        c.distanceTolerance = 0.01f;
        c.fringeWidth = 1.0f;
        c.devicePixelRatio = 1.0f;
        if (renderer.create() != 0) {
            c.fontContext = FontStash.create(512, 512, 1);
            if (c.fontContext != null) {
                c.fontImages[0] = renderer.createTexture(1, 512, 512, 0, null);
                if (c.fontImages[0] != 0) {
                    c.fontImageIndex = 0;
                    return c;
                }
            }
        }
        c.delete();
        return null;
    }

    public void delete() {
        for (int i = 0; i < 4; ++i) {
            if (fontImages[i] != 0) {
                renderer.deleteTexture(fontImages[i]);
                fontImages[i] = 0;
            }
        }
        renderer.destroy();
    }

    public VectorState state() {
        return states[stateCount - 1];
    }

    public void save() {
        int count = stateCount;
        if (count >= 32) {
            return;
        }
        if (count > 0) {
            states[count].set(states[count - 1]);
            count = stateCount;
        }
        stateCount = count + 1;
    }

    public void restore() {
        if (stateCount > 1) {
            --stateCount;
        }
    }

    private static void identity(float[] t) {
        t[0] = 1.0f;
        t[1] = 0.0f;
        t[2] = 0.0f;
        t[3] = 1.0f;
        t[4] = 0.0f;
        t[5] = 0.0f;
    }

    public void resetState() {
        VectorState s = state();
        s.clear();
        s.sourceRgb = 2;
        s.destinationRgb = 128;
        s.sourceAlpha = 2;
        s.destinationAlpha = 128;
        s.shapeAntialias = 1;
        identity(s.fill.transform);
        s.fill.feather = 1.0f;
        s.fill.inner.set(1.0f, 1.0f, 1.0f, 1.0f);
        s.fill.outer.set(1.0f, 1.0f, 1.0f, 1.0f);
        identity(s.stroke.transform);
        s.stroke.feather = 1.0f;
        s.stroke.inner.set(0.0f, 0.0f, 0.0f, 1.0f);
        s.stroke.outer.set(0.0f, 0.0f, 0.0f, 1.0f);
        s.strokeWidth = 1.0f;
        s.miterLimit = 10.0f;
        s.lineJoin = 4;
        s.alpha = 1.0f;
        identity(s.transform);
        s.scissorExtent[0] = -1.0f;
        s.scissorExtent[1] = -1.0f;
        s.fontSize = 16.0f;
        s.lineHeight = 1.0f;
        s.textAlignment = 65;
    }

    public void setShapeAntialias(int enabled) {
        state().shapeAntialias = enabled;
    }

    public void setStrokeWidth(float width) {
        state().strokeWidth = width;
    }

    public void setMiterLimit(float limit) {
        state().miterLimit = limit;
    }

    public void setLineCap(int cap) {
        state().lineCap = cap;
    }

    public void setLineJoin(int join) {
        state().lineJoin = join;
    }

    public void setGlobalAlpha(float alpha) {
        state().alpha = alpha;
    }

    public void setFontSize(float size) {
        state().fontSize = size;
    }

    public void setTextAlignment(int alignment) {
        state().textAlignment = alignment;
    }

    public void setFontFace(int font) {
        state().font = font;
    }

    public void setFontBlur(float blur) {
        state().fontBlur = blur;
    }

    public void setLetterSpacing(float spacing) {
        state().letterSpacing = spacing;
    }

    public void setTextLineHeight(float height) {
        state().lineHeight = height;
    }

    public void setStrokeColor(UIColor color) {
        UIColor saved = color.copy();
        VectorPaint paint = state().stroke;
        paint.clear();
        identity(paint.transform);
        paint.feather = 1.0f;
        paint.inner.set(saved);
        paint.outer.set(saved);
    }

    public void setFillColor(UIColor color) {
        UIColor saved = color.copy();
        VectorPaint paint = state().fill;
        paint.clear();
        identity(paint.transform);
        paint.feather = 1.0f;
        paint.inner.set(saved);
        paint.outer.set(saved);
    }

    public void resetScissor() {
        VectorState s = state();
        Arrays.fill(s.scissorTransform, 0.0f);
        s.scissorExtent[0] = -1.0f;
        s.scissorExtent[1] = -1.0f;
    }

    public void setCompositeOperation(int operation) {
        int source = 2;
        int destination = 1;
        if (Integer.compareUnsigned(operation, 11) < 0) {
            source = COMPOSITE_SOURCE[operation];
            destination = COMPOSITE_DESTINATION[operation];
        }
        VectorState s = state();
        s.sourceRgb = source;
        s.destinationRgb = destination;
        s.sourceAlpha = source;
        s.destinationAlpha = destination;
    }

    public void setCompositeBlend(int source, int destination) {
        VectorState s = state();
        s.sourceRgb = source;
        s.destinationRgb = destination;
        s.sourceAlpha = source;
        s.destinationAlpha = destination;
    }

    public void setCompositeBlendSeparate(int sr, int dr, int sa, int da) {
        VectorState s = state();
        s.sourceRgb = sr;
        s.destinationRgb = dr;
        s.sourceAlpha = sa;
        s.destinationAlpha = da;
    }

    public void resetTransform() {
        identity(state().transform);
    }

    public void transform(float a, float b, float c, float d, float e, float f) {
        float[] o = state().transform;
        float t0 = o[0], t1 = o[1], t2 = o[2], t3 = o[3], t4 = o[4], t5 = o[5];
        o[0] = a * t0 + b * t2;
        o[1] = a * t1 + b * t3;
        o[2] = c * t0 + d * t2;
        o[3] = c * t1 + d * t3;
        o[4] = (e * t0 + f * t2) + t4;
        o[5] = (e * t1 + f * t3) + t5;
    }

    public void translate(float x, float y) {
        float[] o = state().transform;
        float t0 = o[0], t1 = o[1], t2 = o[2], t3 = o[3], t4 = o[4], t5 = o[5];
        o[0] = t2 * 0.0f + t0;
        o[1] = t3 * 0.0f + t1;
        o[2] = t0 * 0.0f + t2;
        o[3] = t1 * 0.0f + t3;
        o[4] = (x * t0 + y * t2) + t4;
        o[5] = (x * t1 + y * t3) + t5;
    }

    public void rotate(float angle) {
        float cosine = ScalarTrig.cosine(angle);
        float sine = ScalarTrig.sine(angle);
        float[] o = states[stateCount - 1].transform;
        float t0 = o[0], t1 = o[1], t2 = o[2], t3 = o[3], t4 = o[4], t5 = o[5];
        float minusSine = Floats.negate(sine);
        o[0] = cosine * t0 + sine * t2;
        o[1] = cosine * t1 + sine * t3;
        o[2] = minusSine * t0 + cosine * t2;
        o[3] = minusSine * t1 + cosine * t3;
        o[4] = (t0 * 0.0f + t2 * 0.0f) + t4;
        o[5] = (t1 * 0.0f + t3 * 0.0f) + t5;
    }

    public void scale(float x, float y) {
        float[] o = state().transform;
        float t0 = o[0], t1 = o[1], t2 = o[2], t3 = o[3], t4 = o[4], t5 = o[5];
        float cx = t2 * 0.0f;
        float dy = t3 * 0.0f;
        o[0] = cx + x * t0;
        o[1] = dy + x * t1;
        o[2] = y * t2 + t0 * 0.0f;
        o[3] = y * t3 + t1 * 0.0f;
        o[4] = (t0 * 0.0f + cx) + t4;
        o[5] = (t1 * 0.0f + dy) + t5;
    }

    public void readTransform(float[] output) {
        if (output == null) {
            return;
        }
        System.arraycopy(state().transform, 0, output, 0, 6);
    }

    private static void transformedPair(float[] t, float[] values, int index) {
        float x = values[index];
        float y = values[index + 1];
        values[index] = (t[0] * x + t[2] * y) + t[4];
        values[index + 1] = (x * t[1] + y * t[3]) + t[5];
    }

    public void appendCommands(float[] values, int count) {
        int required = commandCount + count;
        if (commandCapacity < required) {
            int capacity = commandCapacity / 2 + required;
            commands = Arrays.copyOf(commands, capacity);
            commandCapacity = capacity;
        }
        if (Integer.compareUnsigned(Sse.cvttss2si(values[0]) - 3, 2) >= 0) {
            commandX = values[count - 2];
            commandY = values[count - 1];
        }
        if (count > 0) {
            float[] transform = states[stateCount - 1].transform;
            int index = 0;
            do {
                int stride = 1;
                switch (Sse.cvttss2si(values[index])) {
                    case 0:
                    case 1:
                        transformedPair(transform, values, index + 1);
                        stride = 3;
                        break;
                    case 2:
                        transformedPair(transform, values, index + 1);
                        transformedPair(transform, values, index + 3);
                        transformedPair(transform, values, index + 5);
                        stride = 7;
                        break;
                    case 4:
                        stride = 2;
                        break;
                    default:
                        break;
                }
                index += stride;
            } while (index < count);
        }
        System.arraycopy(values, 0, commands, commandCount, count);
        commandCount += count;
    }

    public void beginPath() {
        commandCount = 0;
        cache.pointCount = 0;
        cache.pathCount = 0;
    }

    public void moveTo(float x, float y) {
        appendCommands(new float[]{0.0f, x, y}, 3);
    }

    public void lineTo(float x, float y) {
        appendCommands(new float[]{1.0f, x, y}, 3);
    }

    public void bezierTo(float a, float b, float d, float e, float x, float y) {
        appendCommands(new float[]{2.0f, a, b, d, e, x, y}, 7);
    }

    public void quadTo(float cx, float cy, float x, float y) {
        float ratio = Float.intBitsToFloat(0x3f2aaaab);
        appendCommands(new float[]{2.0f, (cx - commandX) * ratio + commandX, (cy - commandY) * ratio + commandY,
                (cx - x) * ratio + x, (cy - y) * ratio + y, x, y}, 7);
    }

    public void closePath() {
        appendCommands(new float[]{3.0f}, 1);
    }

    public void pathWinding(int winding) {
        appendCommands(new float[]{4.0f, (float) winding}, 2);
    }

    public void rect(float x, float y, float width, float height) {
        float bottom = height + y;
        float right = width + x;
        appendCommands(new float[]{0.0f, x, y, 1.0f, x, bottom, 1.0f, right, bottom, 1.0f, right, y, 3.0f}, 13);
    }

    public void roundedRect(float x, float y, float width, float height, float radius) {
        roundedRectVarying(x, y, width, height, radius, radius, radius, radius);
    }

    public void roundedRectVarying(float x, float y, float width, float height, float tl, float tr, float br, float bl) {
        float threshold = Float.intBitsToFloat(0x3dcccccd);
        if (tl < threshold && tr < threshold && br < threshold && bl < threshold) {
            rect(x, y, width, height);
            return;
        }
        float negativeWidth = Floats.negate(width);
        float negativeHeight = Floats.negate(height);
        float halfWidth = (width >= negativeWidth ? width : negativeWidth) * 0.5f;
        float halfHeight = (height >= negativeHeight ? height : negativeHeight) * 0.5f;
        float signX = negativeWidth <= width ? 1.0f : -1.0f;
        float signY = negativeHeight <= height ? 1.0f : -1.0f;
        float blx = Floats.min(bl, halfWidth) * signX;
        float bly = Floats.min(bl, halfHeight) * signY;
        float brx = Floats.min(br, halfWidth) * signX;
        float bry = Floats.min(br, halfHeight) * signY;
        float trx = Floats.min(tr, halfWidth) * signX;
        float try_ = Floats.min(tr, halfHeight) * signY;
        float tlx = Floats.min(tl, halfWidth) * signX;
        float tly = Floats.min(tl, halfHeight) * signY;
        float k = Float.intBitsToFloat(0x3ee53aee);
        float bottom = height + y;
        float right = width + x;
        float startY = y + tly;
        float[] values = {0.0f, x, startY,
                1.0f, x, bottom - bly,
                2.0f, x, Floats.negate(bly) * k + bottom, blx * k + x, bottom, blx + x, bottom,
                1.0f, right - brx, bottom,
                2.0f, right - brx * k, bottom, right, bottom - bry * k, right, bottom - bry,
                1.0f, right, try_ + y,
                2.0f, right, try_ * k + y, Floats.negate(trx) * k + right, y, right - trx, y,
                1.0f, x + tlx, y,
                2.0f, tlx * k + x, y, x, tly * k + y, x, startY, 3.0f};
        appendCommands(values, 44);
    }

    public void ellipse(float x, float y, float rx, float ry) {
        float k = Float.intBitsToFloat(0x3f0d6289);
        float left = x - rx;
        float bottom = y + ry;
        float top = y - ry;
        float right = x + rx;
        float lowerControl = ry * k + y;
        float leftControl = Floats.negate(rx) * k + x;
        float upperControl = Floats.negate(ry) * k + y;
        float rightControl = rx * k + x;
        float[] values = {0.0f, left, y, 2.0f, left, lowerControl, leftControl, bottom, x, bottom,
                2.0f, rightControl, bottom, right, lowerControl, right, y,
                2.0f, right, upperControl, rightControl, top, x, top,
                2.0f, leftControl, top, left, upperControl, left, y, 3.0f};
        appendCommands(values, 32);
    }

    public void circle(float x, float y, float radius) {
        ellipse(x, y, radius, radius);
    }

    private static void paintTransform(VectorPaint paint, float[] t) {
        float[] p = paint.transform;
        float p0 = p[0], p1 = p[1], p2 = p[2], p3 = p[3], p4 = p[4], p5 = p[5];
        p[0] = p0 * t[0] + p1 * t[2];
        p[1] = p0 * t[1] + p1 * t[3];
        p[2] = p2 * t[0] + p3 * t[2];
        p[3] = p2 * t[1] + p3 * t[3];
        p[4] = (p4 * t[0] + p5 * t[2]) + t[4];
        p[5] = (p4 * t[1] + p5 * t[3]) + t[5];
    }

    public void setStrokePaint(VectorPaint paint) {
        VectorState s = state();
        s.stroke.set(paint);
        paintTransform(s.stroke, s.transform);
    }

    public void setFillPaint(VectorPaint paint) {
        VectorState s = state();
        s.fill.set(paint);
        paintTransform(s.fill, s.transform);
    }

    public static VectorPaint linearGradient(float sx, float sy, float ex, float ey, UIColor inner, UIColor outer) {
        VectorPaint output = new VectorPaint();
        output.image = 0;
        float dx = ex - sx;
        float dy = ey - sy;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float normalizedX = dx / length;
        float normalizedY = dy / length;
        boolean mask = length > Float.intBitsToFloat(0x38d1b717);
        float nx = mask ? normalizedX : 0.0f;
        float ny = mask ? normalizedY : 1.0f;
        output.transform[0] = ny;
        output.transform[1] = Floats.negate(nx);
        output.transform[2] = nx;
        output.transform[3] = ny;
        output.transform[4] = -100000.0f * nx + sx;
        output.transform[5] = -100000.0f * ny + sy;
        output.extent[0] = 100000.0f;
        output.extent[1] = length * 0.5f + 100000.0f;
        output.radius = 0.0f;
        output.feather = Floats.max(1.0f, length);
        output.inner.set(inner);
        output.outer.set(outer);
        return output;
    }

    public static VectorPaint radialGradient(float x, float y, float innerRadius, float outerRadius, UIColor inner,
                                             UIColor outer) {
        VectorPaint output = new VectorPaint();
        float radius = (innerRadius + outerRadius) * 0.5f;
        float feather = outerRadius - innerRadius;
        output.image = 0;
        output.transform[0] = 1.0f;
        output.transform[3] = 1.0f;
        output.transform[4] = x;
        output.transform[5] = y;
        output.extent[0] = radius;
        output.extent[1] = radius;
        output.radius = radius;
        output.feather = Floats.max(1.0f, feather);
        output.inner.set(inner);
        output.outer.set(outer);
        return output;
    }

    public static VectorPaint boxGradient(float x, float y, float width, float height, float radius, float feather,
                                          UIColor inner, UIColor outer) {
        VectorPaint output = new VectorPaint();
        output.image = 0;
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        output.transform[0] = 1.0f;
        output.transform[3] = 1.0f;
        output.transform[4] = x + hw;
        output.transform[5] = y + hh;
        output.extent[0] = hw;
        output.extent[1] = hh;
        output.radius = radius;
        output.feather = Floats.max(1.0f, feather);
        output.inner.set(inner);
        output.outer.set(outer);
        return output;
    }

    public static VectorPaint imagePattern(float x, float y, float width, float height, float angle, int image,
                                           float alpha) {
        VectorPaint output = new VectorPaint();
        output.radius = 0.0f;
        output.feather = 0.0f;
        output.inner.set(0.0f, 0.0f, 0.0f, 0.0f);
        float cosine = ScalarTrig.cosine(angle);
        float sine = ScalarTrig.sine(angle);
        output.transform[0] = cosine;
        output.transform[1] = sine;
        output.transform[2] = Floats.negate(sine);
        output.transform[3] = cosine;
        output.transform[4] = x;
        output.transform[5] = y;
        output.extent[0] = width;
        output.extent[1] = height;
        output.image = image;
        output.outer.set(1.0f, 1.0f, 1.0f, alpha);
        output.inner.set(output.outer);
        return output;
    }

    private static void storeScissor(VectorState state, float[] t, float x, float y, float width, float height) {
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        float cx = hw + x;
        float cy = hh + y;
        float[] s = state.scissorTransform;
        s[0] = t[2] * 0.0f + t[0];
        s[1] = t[3] * 0.0f + t[1];
        s[2] = t[0] * 0.0f + t[2];
        s[3] = t[1] * 0.0f + t[3];
        s[4] = (cx * t[0] + cy * t[2]) + t[4];
        s[5] = (cx * t[1] + cy * t[3]) + t[5];
        state.scissorExtent[0] = hw;
        state.scissorExtent[1] = hh;
    }

    public void scissor(float x, float y, float width, float height) {
        VectorState s = state();
        float[] t = s.transform.clone();
        storeScissor(s, t, x, y, Floats.max(0.0f, width), Floats.max(0.0f, height));
    }

    private static double abs(double value) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(value) & 0x7fffffffffffffffL);
    }

    private static float orderedAbsolute(float a) {
        float n = Floats.negate(a);
        return n <= a ? a : n;
    }

    public void intersectScissor(float x, float y, float width, float height) {
        VectorState s = state();
        if (s.scissorExtent[0] < 0.0f) {
            scissor(x, y, width, height);
            return;
        }
        float[] sc = s.scissorTransform.clone();
        float extent0 = s.scissorExtent[0];
        float extent1 = s.scissorExtent[1];
        float[] t = s.transform.clone();
        float[] inverse = {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f};
        double determinant = (double) t[3] * (double) t[0] - (double) t[2] * (double) t[1];
        if (!(abs(determinant) < DETERMINANT_THRESHOLD)) {
            double r = 1.0 / determinant;
            inverse[0] = (float) ((double) t[3] * r);
            inverse[1] = (float) ((double) Floats.negate(t[1]) * r);
            inverse[2] = (float) ((double) Floats.negate(t[2]) * r);
            inverse[3] = (float) ((double) t[0] * r);
            inverse[4] = (float) (((double) t[2] * (double) t[5] - (double) t[4] * (double) t[3]) * r);
            inverse[5] = (float) (((double) t[1] * (double) t[4] - (double) t[5] * (double) t[0]) * r);
        }
        float a = sc[0] * inverse[0] + sc[1] * inverse[2];
        float b = sc[0] * inverse[1] + sc[1] * inverse[3];
        float c = sc[2] * inverse[0] + sc[3] * inverse[2];
        float d = sc[2] * inverse[1] + sc[3] * inverse[3];
        float cx = (sc[4] * inverse[0] + sc[5] * inverse[2]) + inverse[4];
        float cy = (sc[4] * inverse[1] + sc[5] * inverse[3]) + inverse[5];
        float ex = extent0 * orderedAbsolute(a) + extent1 * orderedAbsolute(c);
        float ey = extent0 * orderedAbsolute(b) + extent1 * orderedAbsolute(d);
        float left = cx - ex;
        float top = cy - ey;
        float clippedX = Floats.max(left, x);
        float clippedY = Floats.max(top, y);
        float right = Floats.min((ex + ex) + left, width + x);
        float bottom = Floats.min((ey + ey) + top, height + y);
        storeScissor(s, t, clippedX, clippedY, Floats.max(0.0f, right - clippedX), Floats.max(0.0f, bottom - clippedY));
    }

    public static boolean inverseTransform(float[] output, float[] input) {
        double a = input[0];
        double b = input[1];
        double c = input[2];
        double d = input[3];
        double determinant = a * d - b * c;
        if (abs(determinant) < DETERMINANT_THRESHOLD) {
            identity(output);
            return false;
        }
        double inverse = 1.0 / determinant;
        output[0] = (float) (d * inverse);
        output[2] = (float) ((double) Floats.negate(input[2]) * inverse);
        output[4] = (float) (((double) input[5] * (double) input[2] - (double) input[4] * (double) input[3]) * inverse);
        output[1] = (float) ((double) Floats.negate(input[1]) * inverse);
        output[3] = (float) ((double) input[0] * inverse);
        output[5] = (float) (((double) input[4] * (double) input[1] - (double) input[5] * (double) input[0]) * inverse);
        return true;
    }

    public static void translationTransform(float[] t, float x, float y) {
        t[0] = 1.0f;
        t[1] = 0.0f;
        t[2] = 0.0f;
        t[3] = 1.0f;
        t[4] = x;
        t[5] = y;
    }

    public static void scalingTransform(float[] t, float x, float y) {
        t[0] = x;
        t[1] = 0.0f;
        t[2] = 0.0f;
        t[3] = y;
        t[4] = 0.0f;
        t[5] = 0.0f;
    }

    public static void multiplyTransform(float[] t, float[] right) {
        float a = t[0], b = t[1], c = t[2], d = t[3];
        float newB = right[1] * a + right[3] * b;
        float ra = right[0], rc = right[2];
        float newA = a * ra + b * rc;
        float newC = c * ra + rc * d;
        float re = right[4];
        t[1] = newB;
        t[3] = c * right[1] + d * right[3];
        float e = t[4], f = t[5], rb = right[1], rd = right[3], rf = right[5];
        float newE = (ra * e + rc * f) + re;
        float newF = (rb * e + rd * f) + rf;
        t[0] = newA;
        t[2] = newC;
        t[4] = newE;
        t[5] = newF;
    }

    public void updateImage(int image, ByteBuffer data) {
        int[] size = new int[2];
        renderer.textureSize(image, size);
        renderer.updateTexture(image, 0, 0, size[0], size[1], data);
    }

    public int createRgbaImage(int width, int height, int flags, ByteBuffer pixels) {
        return renderer.createTexture(2, width, height, flags, pixels);
    }

    public void deleteImage(int image) {
        renderer.deleteTexture(image);
    }

    public void imageSize(int image, int[] size) {
        renderer.textureSize(image, size);
    }

    private VectorCache.Path lastPath() {
        if (cache.pathCount <= 0) {
            return null;
        }
        return cache.paths[cache.pathCount - 1];
    }

    void addPoint(float x, float y, int flags) {
        VectorCache.Path path = lastPath();
        if (path == null) {
            return;
        }
        int count = cache.pointCount;
        if (count > 0 && path.count > 0) {
            VectorCache.Point last = cache.points[count - 1];
            float dx = x - last.x;
            float dy = y - last.y;
            if (dx * dx + dy * dy < distanceTolerance * distanceTolerance) {
                last.flags |= flags;
                return;
            }
        }
        if (count >= cache.pointCapacity) {
            int capacity = count + cache.pointCapacity / 2 + 1;
            cache.growPoints(capacity);
            cache.pointCapacity = capacity;
            count = cache.pointCount;
        }
        VectorCache.Point point = cache.points[count];
        point.x = x;
        point.y = y;
        point.dx = 0.0f;
        point.dy = 0.0f;
        point.length = 0.0f;
        point.dmx = 0.0f;
        point.dmy = 0.0f;
        point.flags = flags;
        cache.pointCount += 1;
        path.count += 1;
    }

    private static float orderedAbs(float a) {
        float n = Floats.negate(a);
        return n <= a ? a : n;
    }

    void tessellateBezier(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int level,
                          int flags) {
        if (level > 10) {
            return;
        }
        level = level + 1;
        do {
            float dx = x4 - x1;
            float dy = y4 - y1;
            float cross2 = dy * (x2 - x4) - dx * (y2 - y4);
            float cross3 = dy * (x3 - x4) - dx * (y3 - y4);
            float deviation = orderedAbs(cross3) + orderedAbs(cross2);
            if (deviation * deviation < (dx * dx + dy * dy) * tessellationTolerance) {
                addPoint(x4, y4, flags);
                return;
            }
            float x23 = (x2 + x3) * 0.5f;
            float y23 = (y2 + y3) * 0.5f;
            float x12 = (x1 + x2) * 0.5f;
            float y12 = (y1 + y2) * 0.5f;
            float x34 = (x4 + x3) * 0.5f;
            float y34 = (y4 + y3) * 0.5f;
            float x123 = (x23 + x12) * 0.5f;
            float y123 = (y23 + y12) * 0.5f;
            float x234 = (x23 + x34) * 0.5f;
            float y234 = (y23 + y34) * 0.5f;
            float centerX = (x234 + x123) * 0.5f;
            float centerY = (y234 + y123) * 0.5f;
            tessellateBezier(x1, y1, x12, y12, x123, y123, centerX, centerY, level, 0);
            level = level + 1;
            x1 = centerX;
            y1 = centerY;
            x2 = x234;
            y2 = y234;
            x3 = x34;
            y3 = y34;
        } while (level != 12);
    }

    private static void reversePoints(VectorCache.Point[] points, int first, int count) {
        for (long a = 0, b = (long) count - 1; a < b; ++a, --b) {
            VectorCache.Point saved = points[(int) (first + a)];
            points[(int) (first + a)] = points[(int) (first + b)];
            points[(int) (first + b)] = saved;
        }
    }

    void flattenPaths() {
        if (cache.pathCount >= 1) {
            return;
        }
        if (commandCount >= 1) {
            int index = 0;
            do {
                float[] c = commands;
                int stride = 1;
                int command = Sse.cvttss2si(c[index]);
                switch (command) {
                    case 0:
                    case 1:
                        if (command == 0) {
                            int count = cache.pathCount;
                            if (count >= cache.pathCapacity) {
                                int capacity = count + cache.pathCapacity / 2 + 1;
                                cache.growPaths(capacity);
                                cache.pathCapacity = capacity;
                                count = cache.pathCount;
                            }
                            VectorCache.Path path = cache.paths[count];
                            path.clear();
                            path.first = cache.pointCount;
                            path.winding = 1;
                            cache.pathCount += 1;
                        }
                        addPoint(c[index + 1], c[index + 2], 1);
                        stride = 3;
                        break;
                    case 2:
                        if (cache.pointCount > 0) {
                            VectorCache.Point last = cache.points[cache.pointCount - 1];
                            tessellateBezier(last.x, last.y, c[index + 1], c[index + 2], c[index + 3], c[index + 4],
                                    c[index + 5], c[index + 6], 0, 1);
                        }
                        stride = 7;
                        break;
                    case 3: {
                        VectorCache.Path path = lastPath();
                        if (path != null) {
                            path.closed = 1;
                        }
                        break;
                    }
                    case 4: {
                        VectorCache.Path path = lastPath();
                        if (path != null) {
                            path.winding = Sse.cvttss2si(c[index + 1]);
                        }
                        stride = 2;
                        break;
                    }
                    default:
                        break;
                }
                index += stride;
            } while (index < commandCount);
        }
        float[] bounds = cache.bounds;
        bounds[0] = 1000000.0f;
        bounds[1] = 1000000.0f;
        bounds[2] = -1000000.0f;
        bounds[3] = -1000000.0f;
        for (int pathIndex = 0; pathIndex < cache.pathCount; ++pathIndex) {
            VectorCache.Path path = cache.paths[pathIndex];
            VectorCache.Point[] pts = cache.points;
            int first = path.first;
            int count = path.count;
            if (count == 0) {
                continue;
            }
            long lastIndex = (long) count - 1;
            float firstX = pts[first].x;
            float firstY = pts[first].y;
            float dx = firstX - pts[(int) (first + lastIndex)].x;
            float dy = firstY - pts[(int) (first + lastIndex)].y;
            if (count > 1 && dx * dx + dy * dy < distanceTolerance * distanceTolerance) {
                path.count = count - 1;
                path.closed = 1;
                count = path.count;
                --lastIndex;
            }
            if (count > 2) {
                float area = 0.0f;
                for (int i = 2; i < count; ++i) {
                    area = area + ((pts[first + i].x - firstX) * (pts[first + i - 1].y - firstY)
                            - (pts[first + i].y - firstY) * (pts[first + i - 1].x - firstX));
                }
                if (path.winding == 1 && area * 0.5f < 0.0f) {
                    reversePoints(pts, first, count);
                    count = path.count;
                }
                if (path.winding == 2 && area * 0.5f > 0.0f && count > 1) {
                    reversePoints(pts, first, count);
                    count = path.count;
                }
            }
            VectorCache.Point current = pts[(int) (first + lastIndex)];
            for (int i = 0; i < count; ++i) {
                VectorCache.Point next = pts[first + i];
                float edgeX = next.x - current.x;
                float edgeY = next.y - current.y;
                float length = (float) Math.sqrt(edgeX * edgeX + edgeY * edgeY);
                if (length > EPSILON) {
                    float inverse = 1.0f / length;
                    edgeX = edgeX * inverse;
                    edgeY = edgeY * inverse;
                }
                current.dx = edgeX;
                current.dy = edgeY;
                current.length = length;
                bounds[0] = Floats.min(bounds[0], current.x);
                bounds[1] = Floats.min(bounds[1], current.y);
                bounds[2] = Floats.max(bounds[2], current.x);
                bounds[3] = Floats.max(bounds[3], current.y);
                current = next;
            }
        }
    }

    void calculateJoins(float width, int lineJoin, float miterLimit) {
        float inverseWidth = width > 0.0f ? 1.0f / width : 0.0f;
        int pathCount = cache.pathCount;
        VectorCache.Point[] points = cache.points;
        for (int pathIndex = 0; pathIndex < pathCount; ++pathIndex) {
            VectorCache.Path path = cache.paths[pathIndex];
            int count = path.count;
            path.bevelCount = 0;
            int leftCount = 0;
            if (count > 0) {
                int pointIndex = path.first;
                VectorCache.Point previous = points[path.first + count - 1];
                for (int i = 0; i < count; ++i) {
                    VectorCache.Point point = points[pointIndex];
                    point.dmx = (previous.dy + point.dy) * 0.5f;
                    point.dmy = (Floats.negate(point.dx) - previous.dx) * 0.5f;
                    float square = point.dmx * point.dmx + point.dmy * point.dmy;
                    if (square > EPSILON) {
                        float scale = Floats.min(600.0f, 1.0f / square);
                        point.dmx = point.dmx * scale;
                        point.dmy = point.dmy * scale;
                    }
                    float cross = previous.dy * point.dx - previous.dx * point.dy;
                    boolean left = cross > 0.0f;
                    int flags = ((point.flags & 1) + (left ? 2 : 0)) & 0xff;
                    point.flags = flags;
                    float limit = Floats.max(Float.intBitsToFloat(0x3f8147ae),
                            Floats.min(previous.length, point.length) * inverseWidth);
                    if ((square * limit) * limit < 1.0f) {
                        flags |= 8;
                        point.flags = flags;
                    }
                    if ((flags & 1) != 0 && ((lineJoin & ~2) == 1 || (square * miterLimit) * miterLimit < 1.0f)) {
                        flags |= 4;
                        point.flags = flags;
                    }
                    if ((flags & 12) != 0) {
                        path.bevelCount += 1;
                    }
                    leftCount += left ? 1 : 0;
                    previous = point;
                    ++pointIndex;
                }
            }
            path.convex = leftCount == count ? 1 : 0;
        }
    }

    private class Cursor {
        int index;

        void vertex(float x, float y, float u) {
            vertex(x, y, u, 1.0f);
        }

        void vertex(float x, float y, float u, float v) {
            float[] out = cache.vertices;
            int base = index * 4;
            out[base] = x;
            out[base + 1] = y;
            out[base + 2] = u;
            out[base + 3] = v;
            ++index;
        }
    }

    private void bevelJoin(Cursor out, VectorCache.Point previous, VectorCache.Point point, float lw, float rw, float lu,
                           float ru) {
        float px = point.x, py = point.y, dx = point.dx, dy = point.dy, pdx = previous.dx, pdy = previous.dy;
        int flags = point.flags;
        if ((flags & 2) == 0) {
            float r0x, r0y, r1x, r1y;
            float minusRw = Floats.negate(rw);
            if ((flags & 8) != 0) {
                r0x = pdy * minusRw + px;
                r0y = pdx * rw + py;
                r1x = minusRw * dy + px;
                r1y = rw * dx + py;
            } else {
                r0x = r1x = minusRw * point.dmx + px;
                r0y = r1y = minusRw * point.dmy + py;
            }
            float l0x = pdy * lw + px;
            float l0y = Floats.negate(pdx) * lw + py;
            out.vertex(l0x, l0y, lu);
            out.vertex(r0x, r0y, ru);
            float l1x, l1y;
            if ((flags & 4) != 0) {
                out.vertex(l0x, l0y, lu);
                out.vertex(r0x, r0y, ru);
                l1x = px + lw * dy;
                l1y = py + lw * Floats.negate(dx);
                out.vertex(l1x, l1y, lu);
                out.vertex(r1x, r1y, ru);
            } else {
                float mx = px + lw * point.dmx;
                float my = py + lw * point.dmy;
                out.vertex(l0x, l0y, lu);
                out.vertex(px, py, 0.5f);
                out.vertex(mx, my, lu);
                out.vertex(mx, my, lu);
                l1x = px + lw * dy;
                l1y = py + lw * Floats.negate(dx);
                out.vertex(l1x, l1y, lu);
                out.vertex(px, py, 0.5f);
            }
            out.vertex(l1x, l1y, lu);
            out.vertex(r1x, r1y, ru);
        } else {
            float l0x, l0y, l1x, l1y;
            if ((flags & 8) != 0) {
                l0x = pdy * lw + px;
                l0y = Floats.negate(pdx) * lw + py;
                l1x = dy * lw + px;
                l1y = Floats.negate(dx) * lw + py;
            } else {
                l0x = l1x = px + lw * point.dmx;
                l0y = l1y = py + lw * point.dmy;
            }
            float r0x = Floats.negate(pdy) * rw + px;
            float r0y = pdx * rw + py;
            out.vertex(l0x, l0y, lu);
            out.vertex(r0x, r0y, ru);
            float r1x, r1y;
            if ((flags & 4) != 0) {
                out.vertex(l0x, l0y, lu);
                out.vertex(r0x, r0y, ru);
                out.vertex(l1x, l1y, lu);
                r1x = Floats.negate(dy) * rw + px;
                r1y = dx * rw + py;
                out.vertex(r1x, r1y, ru);
            } else {
                float mx = px - point.dmx * rw;
                float my = py - point.dmy * rw;
                out.vertex(px, py, 0.5f);
                out.vertex(r0x, r0y, ru);
                out.vertex(mx, my, ru);
                out.vertex(mx, my, ru);
                out.vertex(px, py, 0.5f);
                r1x = Floats.negate(dy) * rw + px;
                r1y = dx * rw + py;
                out.vertex(r1x, r1y, ru);
            }
            out.vertex(l1x, l1y, lu);
            out.vertex(r1x, r1y, ru);
        }
    }

    void expandFill(float width) {
        float fringe = fringeWidth;
        calculateJoins(width, 4, Float.intBitsToFloat(0x4019999a));
        int pathCount = cache.pathCount;
        int required = 0;
        for (int i = 0; i < pathCount; ++i) {
            VectorCache.Path path = cache.paths[i];
            required += path.count + path.bevelCount + 1;
            if (width > 0.0f) {
                required += (path.bevelCount * 5 + path.count) * 2 + 2;
            }
        }
        if (cache.vertexCapacity < required) {
            int capacity = (required + 255) & ~255;
            cache.growVertices(capacity);
            cache.vertexCapacity = capacity;
            pathCount = cache.pathCount;
        }
        if (pathCount <= 0) {
            return;
        }
        boolean convex = pathCount == 1 && cache.paths[0].convex != 0;
        float halfFringe = fringe * 0.5f;
        float rw = width - halfFringe;
        float lw = convex ? halfFringe : width + halfFringe;
        float lu = convex ? 0.5f : 0.0f;
        Cursor cursor = new Cursor();
        cursor.index = 0;
        for (int pathIndex = 0; pathIndex < pathCount; ++pathIndex) {
            VectorCache.Path path = cache.paths[pathIndex];
            VectorCache.Point[] points = cache.points;
            int first = path.first;
            int count = path.count;
            path.fill = cursor.index;
            if (!(width > 0.0f)) {
                for (int i = 0; i < count; ++i) {
                    cursor.vertex(points[first + i].x, points[first + i].y, 0.5f);
                }
                path.fillCount = cursor.index - path.fill;
                path.stroke = -1;
                path.strokeCount = 0;
                continue;
            }
            if (count > 0) {
                VectorCache.Point previous = points[first + count - 1];
                for (int i = 0; i < count; ++i) {
                    VectorCache.Point point = points[first + i];
                    if ((point.flags & 4) == 0 || (point.flags & 2) != 0) {
                        cursor.vertex(point.dmx * halfFringe + point.x, point.dmy * halfFringe + point.y, 0.5f);
                    } else {
                        cursor.vertex(previous.dy * halfFringe + point.x,
                                Floats.negate(previous.dx) * halfFringe + point.y, 0.5f);
                        cursor.vertex(point.dy * halfFringe + point.x, point.y - point.dx * halfFringe, 0.5f);
                    }
                    previous = point;
                }
            }
            path.fillCount = cursor.index - path.fill;
            path.stroke = cursor.index;
            if (count > 0) {
                VectorCache.Point previous = points[first + count - 1];
                int strokePoints = count;
                for (int i = 0; i < strokePoints; ++i) {
                    VectorCache.Point point = points[first + i];
                    if ((point.flags & 12) != 0) {
                        bevelJoin(cursor, previous, point, lw, rw, lu, 1.0f);
                        strokePoints = path.count;
                    } else {
                        cursor.vertex(point.dmx * lw + point.x, point.dmy * lw + point.y, lu);
                        cursor.vertex(point.x - point.dmx * rw, point.y - point.dmy * rw, 1.0f);
                    }
                    previous = point;
                }
                pathCount = cache.pathCount;
            }

            float[] v = cache.vertices;
            int s0 = path.stroke * 4;
            float x0 = v[s0];
            float y0 = v[s0 + 1];
            cursor.vertex(x0, y0, lu);
            float x1 = v[s0 + 4];
            float y1 = v[s0 + 5];
            cursor.vertex(x1, y1, 1.0f);
            path.strokeCount = cursor.index - path.stroke;
        }
    }

    public void fill() {
        VectorState state = state();
        VectorPaint paint = state.fill.copy();
        flattenPaths();
        expandFill(renderer.edgeAntialias() != 0 && state.shapeAntialias != 0 ? fringeWidth : 0.0f);
        paint.inner.alpha = paint.inner.alpha * state.alpha;
        paint.outer.alpha = state.alpha * paint.outer.alpha;
        renderer.fill(paint, state, state, fringeWidth, cache.bounds, cache, cache.pathCount);
        int count = cache.pathCount;
        if (count <= 0) {
            return;
        }
        int triangles = fillTriangleCount;
        for (int i = 0; i < count; ++i) {
            triangles += cache.paths[i].fillCount + cache.paths[i].strokeCount - 4;
        }
        fillTriangleCount = triangles;
        drawCallCount = drawCallCount + count * 2;
    }

    private static int roundJoinCount(float sweep, int capCount) {
        int n = Sse.cvttss2si(ScalarRounding.ceilGameFloat((sweep / PI) * (float) capCount));
        int result = n < capCount ? n : capCount;
        if (n < 2) {
            result = 2;
        }
        return result;
    }

    private static void roundJoin(Cursor out, VectorCache.Point previous, VectorCache.Point point, float width,
                                  float lu, float ru, int capCount) {
        float px = point.x, py = point.y, pdx = previous.dx, pdy = previous.dy, dx = point.dx, dy = point.dy;
        if ((point.flags & 2) == 0) {
            float r0x, r0y, r1x, r1y;
            if ((point.flags & 8) != 0) {
                r0x = pdy * Floats.negate(width) + px;
                r0y = pdx * width + py;
                r1x = dy * Floats.negate(width) + px;
                r1y = dx * width + py;
            } else {
                r0x = r1x = px + point.dmx * Floats.negate(width);
                r0y = r1y = py + point.dmy * Floats.negate(width);
            }
            float start = CrtMath.atan2f(Floats.negate(pdx), pdy);
            float end = CrtMath.atan2f(Floats.negate(dx), dy);
            float extended = end + TWO_PI;
            if (end < start) {
                end = extended;
            }
            out.vertex(pdy * width + px, Floats.negate(pdx) * width + py, lu);
            out.vertex(r0x, r0y, ru);
            float sweep = end - start;
            int count = roundJoinCount(sweep, capCount);
            float denominator = (float) (count - 1);
            for (int i = 0; i < count; ++i) {
                float angle = ((float) i / denominator) * sweep + start;
                float x = ScalarTrig.cosine(angle) * width + px;
                float y = ScalarTrig.sine(angle) * width + py;
                out.vertex(x, y, lu);
                out.vertex(px, py, 0.5f);
            }
            out.vertex(dy * width + px, Floats.negate(dx) * width + py, lu);
            out.vertex(r1x, r1y, ru);
        } else {
            float l0x, l0y, l1x, l1y;
            if ((point.flags & 8) != 0) {
                l0x = pdy * width + px;
                l0y = Floats.negate(pdx) * width + py;
                l1x = dy * width + px;
                l1y = Floats.negate(dx) * width + py;
            } else {
                l0x = l1x = point.dmx * width + px;
                l0y = l1y = point.dmy * width + py;
            }
            float start = CrtMath.atan2f(pdx, Floats.negate(pdy));
            float end = CrtMath.atan2f(dx, Floats.negate(dy));
            float extended = end + Floats.negate(TWO_PI);
            if (start < end) {
                end = extended;
            }
            out.vertex(l0x, l0y, lu);
            out.vertex(Floats.negate(pdy) * width + px, pdx * width + py, ru);
            int count = roundJoinCount(start - end, capCount);
            float denominator = (float) (count - 1);
            float sweep = end - start;
            for (int i = 0; i < count; ++i) {
                float angle = ((float) i / denominator) * sweep + start;
                float cosine = ScalarTrig.cosine(angle);
                float sine = ScalarTrig.sine(angle);
                out.vertex(px, py, 0.5f);
                out.vertex(cosine * width + px, sine * width + py, ru);
            }
            out.vertex(l1x, l1y, lu);
            out.vertex(Floats.negate(dy) * width + px, dx * width + py, ru);
        }
    }

    private static void startCap(Cursor out, VectorCache.Point point, float dx, float dy, float width, float aa,
                                 float lu, float ru, int cap, int capCount) {
        if (cap == 0 || cap == 2) {
            float distance = cap == 2 ? width - aa : -0.5f * aa;
            float x = point.x - dx * distance;
            float y = point.y - dy * distance;
            float lx = dy * width + x;
            float ly = Floats.negate(dx) * width + y;
            float rx = Floats.negate(dy) * width + x;
            float ry = dx * width + y;
            float ax = Floats.negate(dx) * aa;
            float ay = Floats.negate(dy) * aa;
            out.vertex(ax + lx, ay + ly, lu, 0.0f);
            out.vertex(ax + rx, ay + ry, ru, 0.0f);
            out.vertex(lx, ly, lu);
            out.vertex(rx, ry, ru);
        } else if (cap == 1) {
            float denominator = (float) (capCount - 1);
            for (int i = 0; i < capCount; ++i) {
                float angle = ((float) i / denominator) * PI;
                float cw = ScalarTrig.cosine(angle) * width;
                float sw = ScalarTrig.sine(angle) * width;
                float x = sw * Floats.negate(dx) + (cw * Floats.negate(dy) + point.x);
                float y = sw * Floats.negate(dy) + (cw * dx + point.y);
                out.vertex(x, y, lu);
                out.vertex(point.x, point.y, 0.5f);
            }
            out.vertex(dy * width + point.x, Floats.negate(dx) * width + point.y, lu);
            out.vertex(Floats.negate(dy) * width + point.x, dx * width + point.y, ru);
        }
    }

    private static void endCap(Cursor out, VectorCache.Point point, float dx, float dy, float width, float aa,
                               float lu, float ru, int cap, int capCount) {
        if (cap == 0 || cap == 2) {
            float distance = cap == 2 ? width - aa : -0.5f * aa;
            float x = dx * distance + point.x;
            float y = dy * distance + point.y;
            float lx = dy * width + x;
            float ly = Floats.negate(dx) * width + y;
            float rx = Floats.negate(dy) * width + x;
            float ry = dx * width + y;
            out.vertex(lx, ly, lu);
            out.vertex(rx, ry, ru);
            out.vertex(dx * aa + lx, dy * aa + ly, lu, 0.0f);
            out.vertex(dx * aa + rx, dy * aa + ry, ru, 0.0f);
        } else if (cap == 1) {
            out.vertex(dy * width + point.x, Floats.negate(dx) * width + point.y, lu);
            out.vertex(Floats.negate(dy) * width + point.x, dx * width + point.y, ru);
            float denominator = (float) (capCount - 1);
            for (int i = 0; i < capCount; ++i) {
                float angle = ((float) i / denominator) * PI;
                float cw = ScalarTrig.cosine(angle) * width;
                float sw = ScalarTrig.sine(angle) * width;
                float x = sw * dx + (cw * Floats.negate(dy) + point.x);
                float y = sw * dy + (cw * dx + point.y);
                out.vertex(point.x, point.y, 0.5f);
                out.vertex(x, y, lu);
            }
        }
    }

    private static float[] normalize(float x, float y) {
        float length = (float) Math.sqrt(x * x + y * y);
        float inverse = 1.0f / length;
        float nx = inverse * x;
        float ny = inverse * y;
        if (length > EPSILON) {
            return new float[]{nx, ny};
        }
        return new float[]{x, y};
    }

    void expandStroke(float halfWidth, float aa, int lineCap, int lineJoin, float miterLimit) {
        float angle = CrtMath.acosf(halfWidth / (tessellationTolerance + halfWidth));
        int candidate = Sse.cvttss2si(ScalarRounding.ceilGameFloat(PI / (angle + angle)));
        int capCount = candidate >= 3 ? candidate : 2;
        float width = aa * 0.5f + halfWidth;
        float lu = aa == 0.0f ? 0.5f : 0.0f;
        float ru = aa == 0.0f ? 0.5f : 1.0f;
        calculateJoins(width, lineJoin, miterLimit);
        int pathCount = cache.pathCount;
        int required = 0;
        int joinVertices = lineJoin == 1 ? capCount + 2 : 5;
        for (int i = 0; i < pathCount; ++i) {
            VectorCache.Path path = cache.paths[i];
            required += (path.bevelCount * joinVertices + path.count) * 2 + 2;
            if (path.closed == 0) {
                required += lineCap == 1 ? capCount * 4 + 4 : 12;
            }
        }
        if (required > cache.vertexCapacity) {
            int capacity = (required + 255) & ~255;
            cache.growVertices(capacity);
            cache.vertexCapacity = capacity;
        }
        Cursor cursor = new Cursor();
        cursor.index = 0;
        for (int pathIndex = 0; pathIndex < cache.pathCount; ++pathIndex) {
            VectorCache.Path path = cache.paths[pathIndex];
            VectorCache.Point[] points = cache.points;
            int first = path.first;
            path.fill = -1;
            path.fillCount = 0;
            int closed = path.closed;
            path.stroke = cursor.index;
            int index;
            int end;
            int previous;
            int point;
            if (closed != 0) {
                index = 0;
                end = path.count;
                previous = first + end - 1;
                point = first;
            } else {
                end = path.count - 1;
                index = 1;
                previous = first;
                point = first + 1;
                float[] d = normalize(points[point].x - points[previous].x, points[point].y - points[previous].y);
                startCap(cursor, points[previous], d[0], d[1], width, aa, lu, ru, lineCap, capCount);
            }
            if (index < end) {
                do {
                    VectorCache.Point p = points[point];
                    if ((p.flags & 12) == 0) {
                        float ox = p.dmx * width;
                        float oy = p.dmy * width;
                        cursor.vertex(ox + p.x, oy + p.y, lu);
                        cursor.vertex(p.x - ox, p.y - oy, ru);
                    } else if (lineJoin == 1) {
                        roundJoin(cursor, points[previous], p, width, lu, ru, capCount);
                    } else {
                        bevelJoin(cursor, points[previous], p, width, width, lu, ru);
                    }
                    previous = point;
                    ++point;
                    index = index + 1;
                } while (index != end);
            }
            if (closed != 0) {
                float[] v = cache.vertices;
                int s0 = path.stroke * 4;
                float x0 = v[s0];
                float y0 = v[s0 + 1];
                cursor.vertex(x0, y0, lu);
                float x1 = v[s0 + 4];
                float y1 = v[s0 + 5];
                cursor.vertex(x1, y1, ru);
            } else {
                float[] d = normalize(points[point].x - points[previous].x, points[point].y - points[previous].y);
                endCap(cursor, points[point], d[0], d[1], width, aa, lu, ru, lineCap, capCount);
            }
            path.strokeCount = cursor.index - path.stroke;
        }
    }

    public void stroke() {
        VectorState state = state();
        float[] t = state.transform;
        float sx = (float) Math.sqrt(t[0] * t[0] + t[2] * t[2]);
        float sy = (float) Math.sqrt(t[1] * t[1] + t[3] * t[3]);
        float rawWidth = ((sy + sx) * 0.5f) * state.strokeWidth;
        float width = Floats.min(200.0f, rawWidth);
        if (rawWidth < 0.0f) {
            width = 0.0f;
        }
        VectorPaint paint = state.stroke.copy();
        float fringe = fringeWidth;
        if (width < fringe) {
            float ratio = width / fringe;
            float fraction = Floats.min(1.0f, ratio);
            if (ratio < 0.0f) {
                fraction = 0.0f;
            }
            float alpha = fraction * fraction;
            paint.inner.alpha = paint.inner.alpha * alpha;
            paint.outer.alpha = alpha * paint.outer.alpha;
            width = fringe;
        }
        paint.inner.alpha = paint.inner.alpha * state.alpha;
        paint.outer.alpha = paint.outer.alpha * state.alpha;
        flattenPaths();
        float aa = renderer.edgeAntialias() != 0 && state.shapeAntialias != 0 ? fringeWidth : 0.0f;
        expandStroke(0.5f * width, aa, state.lineCap, state.lineJoin, state.miterLimit);
        renderer.stroke(paint, state, state, fringeWidth, width, cache, cache.pathCount);
        int count = cache.pathCount;
        if (count <= 0) {
            return;
        }
        int triangles = strokeTriangleCount;
        for (int i = 0; i < count; ++i) {
            triangles += cache.paths[i].strokeCount - 2;
        }
        strokeTriangleCount = triangles;
        drawCallCount = drawCallCount + count;
    }

    public void beginFrame(float width, float height, float pixelRatio) {
        stateCount = 1;
        resetState();
        tessellationTolerance = 0.25f / pixelRatio;
        distanceTolerance = 0.01f / pixelRatio;
        fringeWidth = 1.0f / pixelRatio;
        devicePixelRatio = pixelRatio;
        renderer.viewport(width, height, pixelRatio);
        drawCallCount = 0;
        fillTriangleCount = 0;
        strokeTriangleCount = 0;
        textTriangleCount = 0;
    }

    public void cancelFrame() {
        renderer.cancel();
    }

    public void endFrame() {
        renderer.flush();
        if (fontImageIndex == 0) {
            return;
        }
        int image = fontImages[fontImageIndex];
        fontImages[fontImageIndex] = 0;
        if (image == 0) {
            return;
        }
        int[] size = new int[2];
        renderer.textureSize(image, size);
        int width = size[0];
        int height = size[1];
        int count = 0;
        for (int i = 0; i < fontImageIndex; ++i) {
            int oldImage = fontImages[i];
            if (oldImage == 0) {
                continue;
            }
            fontImages[i] = 0;
            int[] old = new int[2];
            renderer.textureSize(oldImage, old);
            if (old[0] < width || old[1] < height) {
                renderer.deleteTexture(oldImage);
            } else {
                fontImages[count] = oldImage;
                ++count;
            }
        }
        fontImages[count] = fontImages[0];
        fontImages[0] = image;
        fontImageIndex = 0;
    }

    private float fontScale(VectorState s) {
        float x = (float) Math.sqrt(s.transform[0] * s.transform[0] + s.transform[2] * s.transform[2]);
        float y = (float) Math.sqrt(s.transform[1] * s.transform[1] + s.transform[3] * s.transform[3]);
        float quantized = (float) Sse.cvttss2si((((y + x) * 0.5f) / 0.01f) + 0.5f) * 0.01f;
        return Floats.min(quantized, 4.0f) * devicePixelRatio;
    }

    private void configure(VectorState s, float scale) {
        FontStash.State f = fontContext.state();
        f.size = s.fontSize * scale;
        f.blur = s.fontBlur * scale;
        f.spacing = s.letterSpacing * scale;
        f.alignment = s.textAlignment;
        f.font = s.font;
    }

    private void flushTexture() {
        FontStash f = fontContext;
        int x = f.dirty[0];
        int y = f.dirty[1];
        int width = f.dirty[2] - x;
        int height = f.dirty[3] - y;
        if (width <= 0 || height <= 0) {
            return;
        }
        f.dirty[0] = f.width;
        f.dirty[1] = f.height;
        f.dirty[2] = 0;
        f.dirty[3] = 0;
        int image = fontImages[fontImageIndex];
        if (image != 0) {
            renderer.updateTexture(image, x, y, width, height, f.textureBuffer());
        }
    }

    private void submitText(float[] vertices, int count) {
        VectorState s = state();
        VectorPaint paint = s.fill.copy();
        paint.image = fontImages[fontImageIndex];
        paint.inner.alpha = paint.inner.alpha * s.alpha;
        paint.outer.alpha = s.alpha * paint.outer.alpha;
        renderer.triangles(paint, s, s, vertices, count, fringeWidth);
        drawCallCount = drawCallCount + 1;
        textTriangleCount = textTriangleCount + count / 3;
    }

    public int findFont(byte[] name) {
        return name == null ? -1 : fontContext.findFont(name);
    }

    public int createFontMemory(byte[] name, byte[] data, int size, int free) {
        return fontContext.addFontMemory(name, data, size, free, 0);
    }

    public int allocateTextAtlas() {
        flushTexture();
        int index = fontImageIndex;
        if (index >= 3) {
            return 0;
        }
        int[] size = new int[2];
        int image = fontImages[index + 1];
        int w;
        int h;
        if (image != 0) {
            renderer.textureSize(image, size);
            w = size[0];
            h = size[1];
        } else {
            renderer.textureSize(fontImages[index], size);
            w = size[0];
            h = size[1];
            if (w > h) {
                h = h * 2;
            } else {
                w = w * 2;
            }
            if (w > 2048 || h > 2048) {
                w = h = 2048;
            }
            int created = renderer.createTexture(1, w, h, 0, null);
            fontImages[fontImageIndex + 1] = created;
        }
        fontImageIndex = fontImageIndex + 1;
        fontContext.resetAtlas(w, h);
        return 1;
    }

    public float text(float x, float y, byte[] string, int start, int end) {
        VectorState s = state();
        float[] originalTransform = s.transform.clone();
        float scale = fontScale(s);
        if (end < 0) {
            end = strlen(string, start);
        }
        if (s.font == -1) {
            return x;
        }
        configure(s, scale);
        int byteCount = end - start;
        int maximum = Math.max(2, byteCount) * 6;
        if (cache.vertexCapacity < maximum) {
            int capacity = (maximum + 255) & 0x7fffff00;
            cache.growVertices(capacity);
            cache.vertexCapacity = capacity;
        }
        float[] vertices = cache.vertices;
        FontTextIterator iterator = new FontTextIterator();
        FontQuad quad = new FontQuad();
        fontContext.initializeIterator(iterator, x * scale, y * scale, string, start, end, 2);
        FontTextIterator previous = iterator.copy();
        float inverse = 1.0f / scale;
        float determinant = originalTransform[3] * originalTransform[0] - originalTransform[1] * originalTransform[2];
        int count = 0;
        while (fontContext.nextIterator(iterator, quad)) {
            if (iterator.previousGlyph == -1) {
                if (count != 0) {
                    submitText(vertices, count);
                }
                if (allocateTextAtlas() == 0) {
                    count = 0;
                    break;
                }
                iterator.set(previous);
                fontContext.nextIterator(iterator, quad);
                count = 0;
                if (iterator.previousGlyph == -1) {
                    break;
                }
            }
            previous.set(iterator);
            if (determinant < 0.0f) {
                float swap = quad.y0;
                quad.y0 = quad.y1;
                quad.y1 = swap;
                swap = quad.t0;
                quad.t0 = quad.t1;
                quad.t1 = swap;
            }
            if (count + 6 <= maximum) {
                float[] t = s.transform;
                float x0a = (quad.x0 * inverse) * t[0];
                float x0b = (quad.x0 * inverse) * t[1];
                float y0c = (quad.y0 * inverse) * t[2];
                float y0d = (quad.y0 * inverse) * t[3];
                float x1a = (quad.x1 * inverse) * t[0];
                float x1b = (quad.x1 * inverse) * t[1];
                float y1c = (quad.y1 * inverse) * t[2];
                float y1d = (quad.y1 * inverse) * t[3];
                float ax = (x0a + y0c) + t[4];
                float ay = (x0b + y0d) + t[5];
                float bx = (x1a + y1c) + t[4];
                float by = (x1b + y1d) + t[5];
                putVertex(vertices, count, ax, ay, quad.s0, quad.t0);
                putVertex(vertices, count + 1, bx, by, quad.s1, quad.t1);
                putVertex(vertices, count + 2, (x1a + y0c) + t[4], (x1b + y0d) + t[5], quad.s1, quad.t0);
                putVertex(vertices, count + 3, ax, ay, quad.s0, quad.t0);
                putVertex(vertices, count + 4, (y1c + x0a) + t[4], (y1d + x0b) + t[5], quad.s0, quad.t1);
                putVertex(vertices, count + 5, bx, by, quad.s1, quad.t1);
                count = count + 6;
            }
        }
        flushTexture();
        submitText(vertices, count);
        return iterator.nextX / scale;
    }

    private static void putVertex(float[] v, int index, float x, float y, float u, float w) {
        int base = index * 4;
        v[base] = x;
        v[base + 1] = y;
        v[base + 2] = u;
        v[base + 3] = w;
    }

    public static int strlen(byte[] string, int start) {
        int end = start;
        while (end < string.length && string[end] != 0) {
            ++end;
        }
        return end;
    }

    public float textBounds(float x, float y, byte[] string, int start, int end, float[] bounds) {
        VectorState s = state();
        if (s.font == -1) {
            return 0.0f;
        }
        float scale = fontScale(s);
        float inverse = 1.0f / scale;
        configure(s, scale);
        float scaledY = y * scale;
        float advance = fontContext.textBounds(x * scale, scaledY, string, start, end, bounds);
        if (bounds != null) {
            float[] minMax = new float[2];
            if (fontContext.lineBounds(scaledY, minMax)) {
                bounds[1] = minMax[0];
                bounds[3] = minMax[1];
            }
            for (int i = 0; i < 4; ++i) {
                bounds[i] = inverse * bounds[i];
            }
        }
        return inverse * advance;
    }

    public boolean textMetrics(float[] output) {
        VectorState s = state();
        if (s.font == -1) {
            return false;
        }
        float scale = fontScale(s);
        configure(s, scale);
        fontContext.fontMetrics(output);
        float inverse = 1.0f / scale;
        output[0] = output[0] * inverse;
        output[1] = output[1] * inverse;
        output[2] = inverse * output[2];
        return true;
    }
}

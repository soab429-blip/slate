package yt.ren.slate.render;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Arrays;

public class DrawList {
    public static final int LINES = 1;
    public static final int TRIANGLES = 4;

    private static final float[] CUBE = {-1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f,
            1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f};
    private static final int[] CUBE_FILL = strip(3, 7, 1, 5, 4, 7, 6, 3, 2, 1, 0, 4, 2, 6);
    private static final int[] CUBE_OUTLINE = {0, 1, 1, 5, 5, 4, 4, 0, 2, 3, 3, 7, 7, 6, 6, 2, 0, 2, 1, 3, 4, 6, 5, 7};
    private static final float[] QUAD = {0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f};
    private static final int[] QUAD_FILL = strip(0, 1, 3, 2);
    private static final int[] QUAD_OUTLINE = loop(0, 1, 2, 3);
    private static final float[] POINTER = {0.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.5f, 1.0f, 1.0f};
    private static final int[] POINTER_FILL = {0, 1, 2, 0, 2, 3};
    private static final int[] POINTER_OUTLINE = loop(0, 1, 2, 3);
    private static final int BATCH_STRIDE = 4;
    private static final int MAX_DEPTH = 32;

    private final DrawBackend backend;
    private final float[] matrices = new float[16 * MAX_DEPTH];
    private final float[] rotation = new float[16];
    private final FloatBuffer external = BufferUtils.createFloatBuffer(16);
    private int depth;
    private int color;
    private float lineWidth;
    private float[] positions = new float[3 * 1024];
    private int[] colors = new int[1024];
    private int vertexCount;
    private int[] batches = new int[BATCH_STRIDE * 64];
    private int batchCount;

    public DrawList(DrawBackend backend) {
        this.backend = backend;
    }

    private static int[] strip(int... order) {
        int[] triangles = new int[(order.length - 2) * 3];
        for (int i = 0; i < order.length - 2; ++i) {
            triangles[i * 3] = order[i];
            triangles[i * 3 + 1] = order[i + 1];
            triangles[i * 3 + 2] = order[i + 2];
        }
        return triangles;
    }

    private static int[] loop(int... order) {
        int[] lines = new int[order.length * 2];
        for (int i = 0; i < order.length; ++i) {
            lines[i * 2] = order[i];
            lines[i * 2 + 1] = order[(i + 1) % order.length];
        }
        return lines;
    }

    public static int pack(float red, float green, float blue, float alpha) {
        return channel(red) | channel(green) << 8 | channel(blue) << 16 | channel(alpha) << 24;
    }

    private static int channel(float value) {
        float clamped = value < 0.0f ? 0.0f : value > 1.0f ? 1.0f : value;
        return (int) (clamped * 255.0f + 0.5f);
    }

    public void readMatrices(FloatBuffer modelview, FloatBuffer projection) {
        backend.readMatrices(modelview, projection);
    }

    public void begin() {
        depth = 0;
        identity(matrices, 0);
        color = 0xffffffff;
        lineWidth = 1.0f;
        vertexCount = 0;
        batchCount = 0;
    }

    public void flush() {
        if (vertexCount != 0) {
            backend.draw(this);
        }
        vertexCount = 0;
        batchCount = 0;
    }

    public void beginExternal() {
        flush();
        external.clear();
        external.put(matrices, depth * 16, 16).flip();
        backend.pushMatrix(external);
    }

    public void endExternal() {
        backend.popMatrix();
    }

    public int vertexCount() {
        return vertexCount;
    }

    public float[] positions() {
        return positions;
    }

    public int[] colors() {
        return colors;
    }

    public int batchCount() {
        return batchCount;
    }

    public int batchMode(int batch) {
        return batches[batch * BATCH_STRIDE];
    }

    public float batchLineWidth(int batch) {
        return Float.intBitsToFloat(batches[batch * BATCH_STRIDE + 1]);
    }

    public int batchFirst(int batch) {
        return batches[batch * BATCH_STRIDE + 2];
    }

    public int batchVertices(int batch) {
        return batches[batch * BATCH_STRIDE + 3];
    }

    private static void identity(float[] target, int offset) {
        Arrays.fill(target, offset, offset + 16, 0.0f);
        target[offset] = 1.0f;
        target[offset + 5] = 1.0f;
        target[offset + 10] = 1.0f;
        target[offset + 15] = 1.0f;
    }

    public void push() {
        if (depth + 1 >= MAX_DEPTH) {
            throw new IllegalStateException("draw list matrix stack overflow");
        }
        System.arraycopy(matrices, depth * 16, matrices, (depth + 1) * 16, 16);
        ++depth;
    }

    public void pop() {
        if (depth == 0) {
            throw new IllegalStateException("draw list matrix stack underflow");
        }
        --depth;
    }

    public void translate(float x, float y, float z) {
        int m = depth * 16;
        for (int row = 0; row < 4; ++row) {
            matrices[m + 12 + row] += matrices[m + row] * x + matrices[m + 4 + row] * y + matrices[m + 8 + row] * z;
        }
    }

    public void scale(float x, float y, float z) {
        int m = depth * 16;
        for (int row = 0; row < 4; ++row) {
            matrices[m + row] *= x;
            matrices[m + 4 + row] *= y;
            matrices[m + 8 + row] *= z;
        }
    }

    public void rotate(float angle, float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length == 0.0f) {
            return;
        }
        x /= length;
        y /= length;
        z /= length;
        double radians = Math.toRadians(angle);
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        float t = 1.0f - c;
        float[] r = rotation;
        identity(r, 0);
        r[0] = x * x * t + c;
        r[1] = y * x * t + z * s;
        r[2] = x * z * t - y * s;
        r[4] = x * y * t - z * s;
        r[5] = y * y * t + c;
        r[6] = y * z * t + x * s;
        r[8] = x * z * t + y * s;
        r[9] = y * z * t - x * s;
        r[10] = z * z * t + c;
        multiply(r);
    }

    private void multiply(float[] r) {
        int m = depth * 16;
        for (int row = 0; row < 4; ++row) {
            float a0 = matrices[m + row];
            float a1 = matrices[m + 4 + row];
            float a2 = matrices[m + 8 + row];
            float a3 = matrices[m + 12 + row];
            for (int column = 0; column < 4; ++column) {
                matrices[m + column * 4 + row] = a0 * r[column * 4] + a1 * r[column * 4 + 1]
                        + a2 * r[column * 4 + 2] + a3 * r[column * 4 + 3];
            }
        }
    }

    public void color(float red, float green, float blue) {
        color(red, green, blue, 1.0f);
    }

    public void color(float red, float green, float blue, float alpha) {
        color = pack(red, green, blue, alpha);
    }

    public void lineWidth(float width) {
        lineWidth = width;
    }

    private void open(int mode) {
        int widthBits = mode == LINES ? Float.floatToRawIntBits(lineWidth) : 0;
        if (batchCount != 0) {
            int last = (batchCount - 1) * BATCH_STRIDE;
            if (batches[last] == mode && batches[last + 1] == widthBits) {
                return;
            }
        }
        if ((batchCount + 1) * BATCH_STRIDE > batches.length) {
            batches = Arrays.copyOf(batches, batches.length * 2);
        }
        int at = batchCount * BATCH_STRIDE;
        batches[at] = mode;
        batches[at + 1] = widthBits;
        batches[at + 2] = vertexCount;
        batches[at + 3] = 0;
        ++batchCount;
    }

    private void vertex(float x, float y, float z, int packed) {
        if (vertexCount == colors.length) {
            colors = Arrays.copyOf(colors, colors.length * 2);
            positions = Arrays.copyOf(positions, positions.length * 2);
        }
        int m = depth * 16;
        int at = vertexCount * 3;
        positions[at] = matrices[m] * x + matrices[m + 4] * y + matrices[m + 8] * z + matrices[m + 12];
        positions[at + 1] = matrices[m + 1] * x + matrices[m + 5] * y + matrices[m + 9] * z + matrices[m + 13];
        positions[at + 2] = matrices[m + 2] * x + matrices[m + 6] * y + matrices[m + 10] * z + matrices[m + 14];
        colors[vertexCount] = packed;
        ++vertexCount;
        ++batches[(batchCount - 1) * BATCH_STRIDE + 3];
    }

    public void line(float x1, float y1, float z1, float x2, float y2, float z2) {
        open(LINES);
        vertex(x1, y1, z1, color);
        vertex(x2, y2, z2, color);
    }

    public void line(float x1, float y1, float x2, float y2) {
        line(x1, y1, 0.0f, x2, y2, 0.0f);
    }

    public void triangle(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        open(TRIANGLES);
        vertex(x1, y1, z1, color);
        vertex(x2, y2, z2, color);
        vertex(x3, y3, z3, color);
    }

    public void rect(float x1, float y1, float x2, float y2) {
        triangle(x1, y1, 0.0f, x2, y1, 0.0f, x1, y2, 0.0f);
        triangle(x2, y1, 0.0f, x1, y2, 0.0f, x2, y2, 0.0f);
    }

    public void lineStrip(float[] points, int count) {
        for (int i = 0; i + 1 < count; ++i) {
            int a = i * 3;
            int b = a + 3;
            line(points[a], points[a + 1], points[a + 2], points[b], points[b + 1], points[b + 2]);
        }
    }

    public void lines(float[] points, int[] pointColors, short[] indices, int count) {
        open(LINES);
        for (int i = 0; i + 1 < count; i += 2) {
            int a = indices[i] & 0xFFFF;
            int b = indices[i + 1] & 0xFFFF;
            vertex(points[a * 3], points[a * 3 + 1], points[a * 3 + 2], pointColors[a]);
            vertex(points[b * 3], points[b * 3 + 1], points[b * 3 + 2], pointColors[b]);
        }
    }

    private void mesh(int mode, float[] points, int dimensions, int[] indices) {
        open(mode);
        for (int index : indices) {
            int at = index * dimensions;
            vertex(points[at], points[at + 1], dimensions == 3 ? points[at + 2] : 0.0f, color);
        }
    }

    public void fillCube() {
        mesh(TRIANGLES, CUBE, 3, CUBE_FILL);
    }

    public void outlineCube() {
        mesh(LINES, CUBE, 3, CUBE_OUTLINE);
    }

    public void fillQuad() {
        mesh(TRIANGLES, QUAD, 2, QUAD_FILL);
    }

    public void outlineQuad() {
        mesh(LINES, QUAD, 2, QUAD_OUTLINE);
    }

    public void fillPointer() {
        mesh(TRIANGLES, POINTER, 2, POINTER_FILL);
    }

    public void outlinePointer() {
        mesh(LINES, POINTER, 2, POINTER_OUTLINE);
    }
}

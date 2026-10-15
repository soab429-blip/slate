package yt.ren.slate.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class Primitives {
    private static final int VERTEX_ARRAY = 0x8074;

    private static final FloatBuffer CUBE_VERTICES = floats(-1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f,
            -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f);
    private static final ByteBuffer CUBE_FILL = bytes(3, 7, 1, 5, 4, 7, 6, 3, 2, 1, 0, 4, 2, 6);
    private static final ByteBuffer CUBE_OUTLINE = bytes(0, 1, 1, 5, 5, 4, 4, 0, 2, 3, 3, 7, 7, 6, 6, 2, 0, 2, 1, 3,
            4, 6, 5, 7);
    private static final FloatBuffer QUAD_VERTICES = floats(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f);
    private static final ByteBuffer QUAD_FILL = bytes(0, 1, 3, 2);
    private static final ByteBuffer QUAD_OUTLINE = bytes(0, 1, 2, 3);
    private static final FloatBuffer POINTER_VERTICES = floats(0.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.5f, 1.0f, 1.0f);
    private static final ByteBuffer POINTER_FILL = bytes(0, 1, 2, 3);
    private static final ByteBuffer POINTER_OUTLINE = bytes(0, 1, 2, 3);

    private static FloatBuffer floats(float... values) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(values.length);
        buffer.put(values).flip();
        return buffer;
    }

    private static ByteBuffer bytes(int... values) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(values.length);
        for (int value : values) {
            buffer.put((byte) value);
        }
        buffer.flip();
        return buffer;
    }

    public static void beginCubeVertices() {
        GL11.glVertexPointer(3, 0, CUBE_VERTICES);
        GL11.glEnableClientState(VERTEX_ARRAY);
    }

    public static void drawFilledCube() {
        GL11.glDrawElements(5, CUBE_FILL);
    }

    public static void drawCubeOutline() {
        GL11.glDrawElements(1, CUBE_OUTLINE);
    }

    public static void beginQuadVertices() {
        GL11.glVertexPointer(2, 0, QUAD_VERTICES);
        GL11.glEnableClientState(VERTEX_ARRAY);
    }

    public static void drawFilledQuad() {
        GL11.glDrawElements(5, QUAD_FILL);
    }

    public static void drawQuadOutline() {
        GL11.glDrawElements(2, QUAD_OUTLINE);
    }

    public static void beginPointerVertices() {
        GL11.glVertexPointer(2, 0, POINTER_VERTICES);
        GL11.glEnableClientState(VERTEX_ARRAY);
    }

    public static void drawFilledPointer() {
        GL11.glDrawElements(6, POINTER_FILL);
    }

    public static void drawPointerOutline() {
        GL11.glDrawElements(2, POINTER_OUTLINE);
    }

    public static void endPrimitiveVertices() {
        GL11.glDisableClientState(VERTEX_ARRAY);
    }
}

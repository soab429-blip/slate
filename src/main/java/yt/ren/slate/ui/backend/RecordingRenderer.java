package yt.ren.slate.ui.backend;

import yt.ren.slate.ui.vector.VectorCache;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.ui.vector.VectorPaint;
import yt.ren.slate.ui.vector.VectorRenderer;
import yt.ren.slate.ui.vector.VectorState;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class RecordingRenderer implements VectorRenderer {
    public static final int FLAG_ANTIALIAS = 1;
    public static final int FLAG_STENCIL_STROKES = 2;

    public static final int CALL_FILL = 1;
    public static final int CALL_CONVEX_FILL = 2;
    public static final int CALL_STROKE = 3;
    public static final int CALL_TRIANGLES = 4;

    public static final int TEXTURE_ALPHA = 1;
    public static final int TEXTURE_RGBA = 2;

    public static final int IMAGE_GENERATE_MIPMAPS = 1;
    public static final int IMAGE_REPEAT_X = 2;
    public static final int IMAGE_REPEAT_Y = 4;
    public static final int IMAGE_FLIP_Y = 8;
    public static final int IMAGE_PREMULTIPLIED = 0x10;
    public static final int IMAGE_NEAREST = 0x20;
    public static final int IMAGE_NO_DELETE = 0x10000;
    public static final int IMAGE_RENDER_TARGET = 0x20000;

    public static final int BLEND_ZERO = 0;
    public static final int BLEND_ONE = 1;
    public static final int BLEND_SRC_COLOR = 0x300;
    public static final int BLEND_ONE_MINUS_SRC_COLOR = 0x301;
    public static final int BLEND_SRC_ALPHA = 0x302;
    public static final int BLEND_ONE_MINUS_SRC_ALPHA = 0x303;
    public static final int BLEND_DST_ALPHA = 0x304;
    public static final int BLEND_ONE_MINUS_DST_ALPHA = 0x305;
    public static final int BLEND_DST_COLOR = 0x306;
    public static final int BLEND_ONE_MINUS_DST_COLOR = 0x307;
    public static final int BLEND_SRC_ALPHA_SATURATE = 0x308;
    private static final int BLEND_INVALID = 0x500;

    public static final int UNIFORM_FLOATS = 44;
    public static final int UNIFORM_STRIDE_FLOATS = 45;
    public static final int UNIFORM_STRIDE = UNIFORM_STRIDE_FLOATS * 4;

    public static class Texture {
        public int id;
        public long handle;
        public Object data;
        public int width;
        public int height;
        public int type;
        public int flags;

        void clear() {
            id = 0;
            handle = 0;
            data = null;
            width = 0;
            height = 0;
            type = 0;
            flags = 0;
        }
    }

    public static class Call {
        public int type;
        public int image;
        public int pathOffset;
        public int pathCount;
        public int triangleOffset;
        public int triangleCount;
        public int uniformOffset;
        public final int[] blend = new int[4];
    }

    protected final int flags;
    protected final float[] view = new float[2];
    protected Texture[] textures = new Texture[0];
    protected int textureCount;
    private int textureCapacity;
    private int textureId;
    protected Call[] calls = new Call[0];
    protected int callCount;
    private int callCapacity;
    protected int[] paths = new int[0];
    protected int pathCount;
    private int pathCapacity;
    protected float[] vertices = new float[0];
    protected int vertexCount;
    private int vertexCapacity;
    protected float[] uniforms = new float[0];
    protected int uniformCount;
    private int uniformCapacity;

    protected RecordingRenderer(int flags) {
        this.flags = flags;
    }

    protected abstract void uploadTexture(Texture texture, ByteBuffer data);

    protected abstract void updateTexture(Texture texture, int x, int y, int width, int height, ByteBuffer data);

    protected abstract void releaseTexture(Texture texture);

    @Override
    public int edgeAntialias() {
        return flags & FLAG_ANTIALIAS;
    }

    protected Texture findTexture(int id) {
        for (int i = 0; i < textureCount; ++i) {
            if (textures[i].id == id) {
                return textures[i];
            }
        }
        return null;
    }

    private Texture allocateTexture() {
        Texture texture = null;
        for (int i = 0; i < textureCount; ++i) {
            if (textures[i].id == 0) {
                texture = textures[i];
                break;
            }
        }
        if (texture == null) {
            if (textureCount >= textureCapacity) {
                int capacity = Math.max(textureCount, 3) + textureCapacity / 2 + 1;
                Texture[] next = Arrays.copyOf(textures, capacity);
                for (int i = textures.length; i < capacity; ++i) {
                    next[i] = new Texture();
                }
                textures = next;
                textureCapacity = capacity;
            }
            texture = textures[textureCount++];
        }
        texture.clear();
        texture.id = ++textureId;
        return texture;
    }

    @Override
    public int createTexture(int type, int width, int height, int textureFlags, ByteBuffer data) {
        Texture texture = allocateTexture();
        texture.width = width;
        texture.height = height;
        texture.type = type;
        texture.flags = textureFlags;
        uploadTexture(texture, data);
        return texture.id;
    }

    @Override
    public int deleteTexture(int image) {
        Texture texture = findTexture(image);
        if (texture == null) {
            return 0;
        }
        if ((texture.flags & IMAGE_NO_DELETE) == 0) {
            releaseTexture(texture);
        }
        texture.clear();
        return 1;
    }

    @Override
    public int updateTexture(int image, int x, int y, int width, int height, ByteBuffer data) {
        Texture texture = findTexture(image);
        if (texture == null) {
            return 0;
        }
        updateTexture(texture, x, y, width, height, data);
        return 1;
    }

    @Override
    public int textureSize(int image, int[] size) {
        Texture texture = findTexture(image);
        if (texture == null) {
            return 0;
        }
        size[0] = texture.width;
        size[1] = texture.height;
        return 1;
    }

    @Override
    public void viewport(float width, float height, float pixelRatio) {
        view[0] = width;
        view[1] = height;
    }

    @Override
    public void cancel() {
        reset();
    }

    protected void reset() {
        vertexCount = 0;
        pathCount = 0;
        callCount = 0;
        uniformCount = 0;
    }

    protected void releaseAllTextures() {
        for (int i = 0; i < textureCount; ++i) {
            if (textures[i].id != 0 && (textures[i].flags & IMAGE_NO_DELETE) == 0) {
                releaseTexture(textures[i]);
            }
        }
    }

    private static int blendFactor(int value) {
        switch (value) {
            case 1:
                return BLEND_ZERO;
            case 2:
                return BLEND_ONE;
            case 4:
                return BLEND_SRC_COLOR;
            case 8:
                return BLEND_ONE_MINUS_SRC_COLOR;
            case 16:
                return BLEND_DST_COLOR;
            case 32:
                return BLEND_ONE_MINUS_DST_COLOR;
            case 64:
                return BLEND_SRC_ALPHA;
            case 128:
                return BLEND_ONE_MINUS_SRC_ALPHA;
            case 256:
                return BLEND_DST_ALPHA;
            case 512:
                return BLEND_ONE_MINUS_DST_ALPHA;
            case 1024:
                return BLEND_SRC_ALPHA_SATURATE;
            default:
                return BLEND_INVALID;
        }
    }

    private static void convertBlend(int[] output, VectorState composite) {
        output[0] = blendFactor(composite.sourceRgb);
        output[1] = blendFactor(composite.destinationRgb);
        output[2] = blendFactor(composite.sourceAlpha);
        output[3] = blendFactor(composite.destinationAlpha);
        if (output[0] == BLEND_INVALID || output[1] == BLEND_INVALID || output[2] == BLEND_INVALID
                || output[3] == BLEND_INVALID) {
            output[0] = BLEND_ONE;
            output[1] = BLEND_ONE_MINUS_SRC_ALPHA;
            output[2] = BLEND_ONE;
            output[3] = BLEND_ONE_MINUS_SRC_ALPHA;
        }
    }

    private static void matrix(float[] output, int base, float[] input) {
        output[base] = input[0];
        output[base + 1] = input[1];
        output[base + 2] = 0.0f;
        output[base + 3] = 0.0f;
        output[base + 4] = input[2];
        output[base + 5] = input[3];
        output[base + 6] = 0.0f;
        output[base + 7] = 0.0f;
        output[base + 8] = input[4];
        output[base + 9] = input[5];
        output[base + 10] = 1.0f;
        output[base + 11] = 0.0f;
    }

    private void convertPaint(int u, VectorPaint paint, VectorState scissor, float width, float fringe, float threshold) {
        float[] o = uniforms;
        Arrays.fill(o, u, u + UNIFORM_FLOATS, 0.0f);
        float innerAlpha = paint.inner.alpha;
        o[u + 24] = innerAlpha * paint.inner.red;
        o[u + 25] = innerAlpha * paint.inner.green;
        o[u + 26] = paint.inner.blue * innerAlpha;
        o[u + 27] = innerAlpha;
        float outerAlpha = paint.outer.alpha;
        o[u + 28] = outerAlpha * paint.outer.red;
        o[u + 29] = outerAlpha * paint.outer.green;
        o[u + 30] = paint.outer.blue * outerAlpha;
        o[u + 31] = outerAlpha;
        float[] inverse = new float[6];
        if (scissor.scissorExtent[0] < -0.5f || scissor.scissorExtent[1] < -0.5f) {
            Arrays.fill(o, u, u + 12, 0.0f);
            o[u + 32] = 1.0f;
            o[u + 33] = 1.0f;
            o[u + 34] = 1.0f;
            o[u + 35] = 1.0f;
        } else {
            VectorContext.inverseTransform(inverse, scissor.scissorTransform);
            matrix(o, u, inverse);
            o[u + 32] = scissor.scissorExtent[0];
            o[u + 33] = scissor.scissorExtent[1];
            float[] t = scissor.scissorTransform;
            o[u + 34] = (float) Math.sqrt(t[0] * t[0] + t[2] * t[2]) / fringe;
            o[u + 35] = (float) Math.sqrt(t[1] * t[1] + t[3] * t[3]) / fringe;
        }
        o[u + 36] = paint.extent[0];
        o[u + 37] = paint.extent[1];
        o[u + 40] = (width * 0.5f + fringe * 0.5f) / fringe;
        o[u + 41] = threshold;
        if (paint.image == 0) {
            o[u + 43] = 0.0f;
            o[u + 38] = paint.radius;
            o[u + 39] = paint.feather;
            VectorContext.inverseTransform(inverse, paint.transform);
        } else {
            Texture texture = findTexture(paint.image);
            if (texture == null) {
                return;
            }
            if ((texture.flags & IMAGE_FLIP_Y) != 0) {
                float[] temporary = new float[6];
                float[] flipped = new float[6];
                VectorContext.translationTransform(temporary, 0.0f, paint.extent[1] * 0.5f);
                VectorContext.multiplyTransform(temporary, paint.transform);
                VectorContext.scalingTransform(flipped, 1.0f, -1.0f);
                VectorContext.multiplyTransform(flipped, temporary);
                VectorContext.translationTransform(temporary, 0.0f, o[u + 37] * -0.5f);
                VectorContext.multiplyTransform(temporary, flipped);
                VectorContext.inverseTransform(inverse, temporary);
            } else {
                VectorContext.inverseTransform(inverse, paint.transform);
            }
            o[u + 43] = 1.0f;
            o[u + 42] = texture.type == TEXTURE_RGBA ? ((texture.flags & IMAGE_PREMULTIPLIED) != 0 ? 0.0f : 1.0f) : 2.0f;
        }
        matrix(o, u + 12, inverse);
    }

    private Call allocateCall(int type) {
        if (callCount >= callCapacity) {
            int capacity = Math.max(callCount, 127) + callCapacity / 2 + 1;
            Call[] next = Arrays.copyOf(calls, capacity);
            for (int i = calls.length; i < capacity; ++i) {
                next[i] = new Call();
            }
            calls = next;
            callCapacity = capacity;
        }
        Call call = calls[callCount++];
        call.type = type;
        call.image = 0;
        call.pathOffset = 0;
        call.pathCount = 0;
        call.triangleOffset = 0;
        call.triangleCount = 0;
        call.uniformOffset = 0;
        Arrays.fill(call.blend, 0);
        return call;
    }

    private int allocatePaths(int count) {
        int end = pathCount + count;
        if (pathCapacity < end) {
            int capacity = pathCapacity / 2 + Math.max(end, 128);
            paths = Arrays.copyOf(paths, capacity * 4);
            pathCapacity = capacity;
        }
        int start = pathCount;
        pathCount = end;
        return start;
    }

    private int allocateVertices(int count) {
        int end = vertexCount + count;
        if (vertexCapacity < end) {
            int capacity = vertexCapacity / 2 + Math.max(end, 4096);
            vertices = Arrays.copyOf(vertices, capacity * 4);
            vertexCapacity = capacity;
        }
        int start = vertexCount;
        vertexCount = end;
        return start;
    }

    private int allocateUniforms(int count) {
        int end = uniformCount + count;
        if (uniformCapacity < end) {
            int capacity = uniformCapacity / 2 + Math.max(end, 128);
            uniforms = Arrays.copyOf(uniforms, capacity * UNIFORM_STRIDE_FLOATS);
            uniformCapacity = capacity;
        }
        int offset = uniformCount * UNIFORM_STRIDE;
        uniformCount = end;
        return offset;
    }

    private static int pathVertexCount(VectorCache cache, int count) {
        int result = 0;
        for (int i = 0; i < count; ++i) {
            result += cache.paths[i].fillCount + cache.paths[i].strokeCount;
        }
        return result;
    }

    @Override
    public void fill(VectorPaint paint, VectorState composite, VectorState scissor, float fringe, float[] bounds,
                     VectorCache cache, int count) {
        Call call = allocateCall(CALL_FILL);
        call.triangleCount = 4;
        call.pathOffset = allocatePaths(count);
        call.pathCount = count;
        call.image = paint.image;
        convertBlend(call.blend, composite);
        if (count == 1 && cache.paths[0].convex != 0) {
            call.type = CALL_CONVEX_FILL;
            call.triangleCount = 0;
        }
        int vertex = allocateVertices(pathVertexCount(cache, count) + call.triangleCount);
        for (int i = 0; i < count; ++i) {
            int target = (call.pathOffset + i) * 4;
            Arrays.fill(paths, target, target + 4, 0);
            VectorCache.Path path = cache.paths[i];
            if (path.fillCount > 0) {
                paths[target] = vertex;
                paths[target + 1] = path.fillCount;
                System.arraycopy(cache.vertices, path.fill * 4, vertices, vertex * 4, path.fillCount * 4);
                vertex += path.fillCount;
            }
            if (path.strokeCount > 0) {
                paths[target + 2] = vertex;
                paths[target + 3] = path.strokeCount;
                System.arraycopy(cache.vertices, path.stroke * 4, vertices, vertex * 4, path.strokeCount * 4);
                vertex += path.strokeCount;
            }
        }
        if (call.type != CALL_FILL) {
            call.uniformOffset = allocateUniforms(1);
            convertPaint(call.uniformOffset / 4, paint, scissor, fringe, fringe, -1.0f);
            return;
        }
        call.triangleOffset = vertex;
        float[] quad = {
                bounds[2], bounds[3], 0.5f, 1.0f,
                bounds[2], bounds[1], 0.5f, 1.0f,
                bounds[0], bounds[3], 0.5f, 1.0f,
                bounds[0], bounds[1], 0.5f, 1.0f
        };
        System.arraycopy(quad, 0, vertices, vertex * 4, quad.length);
        call.uniformOffset = allocateUniforms(2);
        int simple = call.uniformOffset / 4;
        Arrays.fill(uniforms, simple, simple + UNIFORM_FLOATS, 0.0f);
        uniforms[simple + 41] = -1.0f;
        uniforms[simple + 43] = 2.0f;
        convertPaint((UNIFORM_STRIDE + call.uniformOffset) / 4, paint, scissor, fringe, fringe, -1.0f);
    }

    @Override
    public void stroke(VectorPaint paint, VectorState composite, VectorState scissor, float fringe, float width,
                       VectorCache cache, int count) {
        Call call = allocateCall(CALL_STROKE);
        call.pathOffset = allocatePaths(count);
        call.pathCount = count;
        call.image = paint.image;
        convertBlend(call.blend, composite);
        int vertex = allocateVertices(pathVertexCount(cache, count));
        for (int i = 0; i < count; ++i) {
            int target = (call.pathOffset + i) * 4;
            Arrays.fill(paths, target, target + 4, 0);
            VectorCache.Path path = cache.paths[i];
            if (path.strokeCount != 0) {
                paths[target + 2] = vertex;
                paths[target + 3] = path.strokeCount;
                System.arraycopy(cache.vertices, path.stroke * 4, vertices, vertex * 4, path.strokeCount * 4);
                vertex += path.strokeCount;
            }
        }
        if ((flags & FLAG_STENCIL_STROKES) != 0) {
            call.uniformOffset = allocateUniforms(2);
            convertPaint(call.uniformOffset / 4, paint, scissor, width, fringe, -1.0f);
            convertPaint((UNIFORM_STRIDE + call.uniformOffset) / 4, paint, scissor, width, fringe,
                    Float.intBitsToFloat(0x3f7f7f7f));
        } else {
            call.uniformOffset = allocateUniforms(1);
            convertPaint(call.uniformOffset / 4, paint, scissor, width, fringe, -1.0f);
        }
    }

    @Override
    public void triangles(VectorPaint paint, VectorState composite, VectorState scissor, float[] source, int count,
                          float fringe) {
        Call call = allocateCall(CALL_TRIANGLES);
        call.image = paint.image;
        convertBlend(call.blend, composite);
        call.triangleOffset = allocateVertices(count);
        call.triangleCount = count;
        System.arraycopy(source, 0, vertices, call.triangleOffset * 4, count * 4);
        call.uniformOffset = allocateUniforms(1);
        int u = call.uniformOffset / 4;
        convertPaint(u, paint, scissor, 1.0f, fringe, -1.0f);
        uniforms[u + 43] = 3.0f;
    }
}

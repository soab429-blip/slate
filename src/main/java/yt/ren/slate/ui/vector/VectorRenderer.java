package yt.ren.slate.ui.vector;

import java.nio.ByteBuffer;

public interface VectorRenderer {
    int edgeAntialias();

    int create();

    int createTexture(int type, int width, int height, int flags, ByteBuffer data);

    int deleteTexture(int image);

    int updateTexture(int image, int x, int y, int width, int height, ByteBuffer data);

    int textureSize(int image, int[] size);

    void viewport(float width, float height, float pixelRatio);

    void cancel();

    void flush();

    void fill(VectorPaint paint, VectorState composite, VectorState scissor, float fringe, float[] bounds,
              VectorCache cache, int pathCount);

    void stroke(VectorPaint paint, VectorState composite, VectorState scissor, float fringe, float width,
                VectorCache cache, int pathCount);

    void triangles(VectorPaint paint, VectorState composite, VectorState scissor, float[] vertices, int count,
                   float fringe);

    void destroy();
}

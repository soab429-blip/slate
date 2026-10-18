package yt.ren.slate.render;

import java.nio.FloatBuffer;

public interface DrawBackend {
    void readMatrices(FloatBuffer modelview, FloatBuffer projection);

    void draw(DrawList drawList);

    void pushMatrix(FloatBuffer matrix);

    void popMatrix();
}

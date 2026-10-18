package yt.ren.slate.ui.backend.gl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.render.DrawBackend;
import yt.ren.slate.render.DrawList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GlDrawBackend implements DrawBackend {
    private FloatBuffer positions = BufferUtils.createFloatBuffer(0);
    private ByteBuffer colors = BufferUtils.createByteBuffer(0);

    @Override
    public void readMatrices(FloatBuffer modelview, FloatBuffer projection) {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
    }

    @Override
    public void draw(DrawList drawList) {
        int count = drawList.vertexCount();
        if (positions.capacity() < count * 3) {
            positions = BufferUtils.createFloatBuffer(count * 6);
            colors = BufferUtils.createByteBuffer(count * 8);
        }
        positions.clear();
        positions.put(drawList.positions(), 0, count * 3).flip();
        colors.clear();
        colors.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(drawList.colors(), 0, count);
        colors.limit(count * 4);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glVertexPointer(3, 0, positions);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 0, colors);
        for (int batch = 0; batch < drawList.batchCount(); ++batch) {
            int mode = drawList.batchMode(batch);
            if (mode == DrawList.LINES) {
                GL11.glLineWidth(drawList.batchLineWidth(batch));
            }
            GL11.glDrawArrays(mode, drawList.batchFirst(batch), drawList.batchVertices(batch));
        }
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    @Override
    public void pushMatrix(FloatBuffer matrix) {
        GL11.glPushMatrix();
        GL11.glMultMatrix(matrix);
    }

    @Override
    public void popMatrix() {
        GL11.glPopMatrix();
    }
}

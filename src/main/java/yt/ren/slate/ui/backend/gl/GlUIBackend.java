package yt.ren.slate.ui.backend.gl;

import org.lwjgl.opengl.GL11;
import yt.ren.slate.ui.backend.RenderTarget;
import yt.ren.slate.ui.backend.UIBackend;
import yt.ren.slate.ui.vector.VectorContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GlUIBackend implements UIBackend {
    private static final class GlTarget implements RenderTarget {
        final GlRenderer.Framebuffer framebuffer;
        final int width;
        final int height;
        int previous;

        GlTarget(GlRenderer.Framebuffer framebuffer, int width, int height) {
            this.framebuffer = framebuffer;
            this.width = width;
            this.height = height;
        }

        @Override
        public int image() {
            return framebuffer.image;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }
    }

    private final FloatBuffer clearColor = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer();

    @Override
    public String name() {
        return "OpenGL";
    }

    @Override
    public VectorContext createVector(int flags) {
        return GlRenderer.createVector(flags);
    }

    @Override
    public RenderTarget createTarget(VectorContext vector, int width, int height) {
        GlRenderer.Framebuffer framebuffer = GlRenderer.createFramebuffer(vector, width, height, 0);
        return framebuffer == null ? null : new GlTarget(framebuffer, width, height);
    }

    @Override
    public void destroyTarget(RenderTarget target) {
        if (target instanceof GlTarget) {
            GlRenderer.deleteFramebuffer(((GlTarget) target).framebuffer);
        }
    }

    @Override
    public void beginTarget(RenderTarget target) {
        GlTarget gl = (GlTarget) target;
        gl.previous = GlRenderer.currentFramebuffer();
        GlRenderer.bindFramebuffer(GlRenderer.handle(gl.framebuffer));
        clearColor.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
    }

    @Override
    public void endTarget(RenderTarget target) {
        GlRenderer.bindFramebuffer(((GlTarget) target).previous);
        GL11.glClearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
    }

    @Override
    public void pushState() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    }

    @Override
    public void popState() {
        GL11.glPopAttrib();
    }
}

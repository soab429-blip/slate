package yt.ren.slate.render;

import net.minecraft.client.render.platform.GlStateManager;

public class GLSM {
    public static void enableDepth() {
        GlStateManager.enableDepthTest();
    }

    public static void disableDepth() {
        GlStateManager.disableDepthTest();
    }

    public static void enableLighting() {
        GlStateManager.enableLighting();
    }

    public static void disableLighting() {
        GlStateManager.disableLighting();
    }

    public static void enableTexture() {
        GlStateManager.enableTexture();
    }

    public static void disableTexture() {
        GlStateManager.disableTexture();
    }

    public static void enableBlend() {
        GlStateManager.enableBlend();
    }

    public static void disableBlend() {
        GlStateManager.disableBlend();
    }

    public static void enableCull() {
        GlStateManager.enableCull();
    }

    public static void disableCull() {
        GlStateManager.disableCull();
    }

    public static void enableAlpha() {
        GlStateManager.enableAlphaTest();
    }

    public static void disableAlpha() {
        GlStateManager.disableAlphaTest();
    }

    public static void blendFunc(int source, int destination) {
        GlStateManager.blendFunc(source, destination);
    }

    public static void color(float red, float green, float blue, float alpha) {
        GlStateManager.color4f(red, green, blue, alpha);
    }
}

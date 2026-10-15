package yt.ren.slate.ui;

import yt.ren.slate.ui.backend.RenderTarget;
import yt.ren.slate.ui.backend.UIBackend;
import yt.ren.slate.ui.backend.UIBackends;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.ui.vector.VectorPaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UIDrawing implements UISurface {
    static final int VECTOR_FLAGS = 1 | 2;
    static final int ICON_FLAGS = 0;

    public final UIBackend backend;
    public final VectorContext vector;

    public double now;
    public int pixelWidth;
    public int pixelHeight;
    public float width;
    public float height;
    public float scale;
    RenderTarget target;
    public final int[] fonts = new int[2];
    public int iconImage;
    public float iconWidth;
    public float iconHeight;
    private UIDrawing(UIBackend backend, VectorContext vector) {
        this.backend = backend;
        this.vector = vector;
    }

    private static byte[] resource(String name) {
        try (InputStream in = UIDrawing.class.getResourceAsStream("/slate/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing GUI asset " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static UIDrawing create() {
        return create(VECTOR_FLAGS, ICON_FLAGS);
    }

    static UIDrawing create(int vectorFlags, int iconFlags) {
        UIBackend backend = UIBackends.create();
        if (backend == null) {
            return null;
        }
        VectorContext vector = backend.createVector(vectorFlags);
        if (vector == null) {
            return null;
        }
        UIDrawing drawing = new UIDrawing(backend, vector);
        byte[] medium = resource("Inter-Medium.ttf");
        byte[] bold = resource("Inter-Bold.ttf");
        if (drawing.addFont(0, UIText.b("Inter-Medium"), medium) != 0
                || drawing.addFont(1, UIText.b("Inter-Bold"), bold) != 0) {
            return null;
        }
        drawing.iconImage = createImageMemory(vector, iconFlags, resource("gui-image-15a360.png"));
        int[] size = new int[2];
        vector.imageSize(drawing.iconImage, size);
        drawing.iconWidth = (float) size[0];
        drawing.iconHeight = (float) size[1];
        return drawing;
    }

    static int createImageMemory(VectorContext vector, int flags, byte[] data) {
        UIImageDecoder.Image image = UIImageDecoder.decodePng(new UIImageDecoder.Input(data), 4);
        if (image == null) {
            return 0;
        }
        byte[] pixels = image.pixels;
        int width = image.width;
        int height = image.height;
        if (UIImageDecoder.flipVertically != 0 && (height >> 1) > 0) {
            for (int y = 0; y < (height >> 1); ++y) {
                int upper = y * width * 4;
                int lower = (height - 1 - y) * width * 4;
                for (int x = 0; x < width * 4; ++x) {
                    byte swap = pixels[upper + x];
                    pixels[upper + x] = pixels[lower + x];
                    pixels[lower + x] = swap;
                }
            }
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        buffer.put(pixels, 0, width * height * 4).flip();
        return vector.createRgbaImage(width, height, flags, buffer);
    }

    int addFont(int index, byte[] name, byte[] data) {
        int font = vector.createFontMemory(name, data, data.length, 0);
        if (font == -1) {
            return 0x1772;
        }
        fonts[index] = font;
        return 0;
    }

    @Override
    public void setScale(float value) {
        scale = value;
        width = (float) pixelWidth / value;
        height = (float) pixelHeight / value;
    }

    @Override
    public boolean resize(int newWidth, int newHeight) {
        boolean changed = newWidth != pixelWidth || newHeight != pixelHeight;
        if (changed) {
            if (target != null) {
                backend.destroyTarget(target);
            }
            target = backend.createTarget(vector, newWidth, newHeight);
        }
        pixelWidth = newWidth;
        pixelHeight = newHeight;
        return changed;
    }

    @Override
    public void beginPresent(double time) {
        backend.pushState();
        vector.beginFrame((float) pixelWidth, (float) pixelHeight, 1.0f);
        vector.scale(scale, scale);
        now = time;
    }

    @Override
    public void beginFrame(double time) {
        backend.pushState();
        if (target != null) {
            backend.beginTarget(target);
        }
        vector.beginFrame((float) pixelWidth, (float) pixelHeight, 1.0f);
        vector.scale(scale, scale);
        now = time;
    }

    @Override
    public void present() {
        if (target == null) {
            return;
        }
        VectorPaint paint = VectorContext.imagePattern(0.0f, 0.0f, width, height, 0.0f, target.image(), 1.0f);
        vector.beginPath();
        vector.rect(0.0f, 0.0f, width, height);
        vector.setFillPaint(paint);
        vector.fill();
    }

    @Override
    public void finishPresent() {
        vector.endFrame();
        backend.popState();
    }

    @Override
    public void finishFrame() {
        vector.endFrame();
        if (target != null) {
            backend.endTarget(target);
        }
        backend.popState();
    }

    public void selectFont(int index) {
        vector.setFontFace(fonts[index]);
    }

    public static UIColor rgb(float r, float g, float b) {
        return new UIColor(r, g, b, 1.0f);
    }

    public static UIColor rgba(float r, float g, float b, float a) {
        return new UIColor(r, g, b, a);
    }

    public static UIColor gray(float value) {
        return new UIColor(value, value, value, 1.0f);
    }

    public static UIColor replaceAlpha(UIColor color, float alpha) {
        color.alpha = alpha;
        return color.copy();
    }

    public static float transitionCurve(float fraction) {
        float square = fraction * fraction;
        float twice = fraction + fraction;
        float slope = 3.0f - twice;
        return slope * square;
    }

    public void roundedRectangle(UIRect rect, float radius) {
        UIRect saved = rect.copy();
        vector.beginPath();
        vector.roundedRect(saved.x, saved.y, saved.width, saved.height, radius);
        vector.fill();
    }

    public void strokeRoundedRectangle(UIRect rect, float radius) {
        UIRect saved = rect.copy();
        vector.beginPath();
        vector.roundedRect(saved.x, saved.y, saved.width, saved.height, radius);
        vector.stroke();
    }

    public void rectangle(UIRect rect) {
        vector.beginPath();
        vector.rect(rect.x, rect.y, rect.width, rect.height);
        vector.fill();
    }

    public float text(float x, float y, byte[] text) {
        return vector.text(x, y, text, 0, text.length);
    }

    public float textBounds(float x, float y, byte[] text) {
        return vector.textBounds(x, y, text, 0, text.length, null);
    }

    public float alignedText(UIRect rect, int alignment, int font, byte[] text) {
        UIRect saved = rect.copy();
        selectFont(font);
        vector.setTextAlignment(UIText.TEXT_ALIGNMENTS[alignment]);
        float x = saved.x;
        if (alignment != 0) {
            x = alignment == 2 ? x + saved.width : (float) ((double) saved.width * 0.5 + (double) x);
        }
        float y = (float) ((double) saved.height * 0.5 + (double) (saved.y + 1.0f));
        return vector.text(x, y, text, 0, text.length);
    }

    public float textRange(UIRect rect, int alignment, int font, byte[] text, int end) {
        selectFont(font);
        vector.setTextAlignment(UIText.TEXT_ALIGNMENTS[alignment]);
        float x = rect.x;
        float y = rect.y;
        rect.y = y + 1.0f;
        if (alignment != 0) {
            x = alignment == 2 ? x + rect.width : rect.width * 0.5f + x;
        }
        float center = (float) ((double) rect.height * 0.5 + (double) rect.y);
        return vector.text(x, center, text, 0, end);
    }

    public void icon(UIRect rect, int icon, float alpha) {
        float[][] atlas = {{0, 0}, {20, 0}, {40, 0}, {0, 20}};
        VectorPaint paint = VectorContext.imagePattern(rect.x - atlas[icon][0] * (rect.width / 20.0f),
                rect.y - atlas[icon][1] * (rect.height / 20.0f), (rect.width / 20.0f) * iconWidth,
                (rect.height / 20.0f) * iconHeight, 0.0f, iconImage, alpha);
        vector.beginPath();
        vector.rect(rect.x, rect.y, rect.width, rect.height);
        vector.setFillPaint(paint);
        vector.fill();
    }
}

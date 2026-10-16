package yt.ren.slate.ui.font;

import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.Sse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class FontStash {
    private static final int[] UTF8 = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
            0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12,
            12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24,
            12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
            12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    };
    private static final int SCRATCH_SIZE = 96000;

    public static class State {
        public int font;
        public int alignment;
        public float size;
        public int color;
        public float blur;
        public float spacing;

        void set(State o) {
            font = o.font;
            alignment = o.alignment;
            size = o.size;
            color = o.color;
            blur = o.blur;
            spacing = o.spacing;
        }
    }

    static class Font {
        TrueType info;
        final byte[] name = new byte[64];
        byte[] data;
        int dataSize;
        int freeData;
        float ascender;
        float descender;
        float lineHeight;
        int glyphCapacity;
        int glyphCount;
        int[] glyphCodepoint;
        int[] glyphIndex;
        int[] glyphNext;
        short[] glyphSize;
        short[] glyphBlur;
        short[] glyphX0;
        short[] glyphY0;
        short[] glyphX1;
        short[] glyphY1;
        short[] glyphAdvance;
        short[] glyphOffsetX;
        short[] glyphOffsetY;
        final int[] hash = new int[256];
        final int[] fallbacks = new int[20];
        int fallbackCount;

        void allocateGlyphs(int capacity) {
            glyphCodepoint = Arrays.copyOf(glyphCodepoint == null ? new int[0] : glyphCodepoint, capacity);
            glyphIndex = Arrays.copyOf(glyphIndex == null ? new int[0] : glyphIndex, capacity);
            glyphNext = Arrays.copyOf(glyphNext == null ? new int[0] : glyphNext, capacity);
            glyphSize = Arrays.copyOf(glyphSize == null ? new short[0] : glyphSize, capacity);
            glyphBlur = Arrays.copyOf(glyphBlur == null ? new short[0] : glyphBlur, capacity);
            glyphX0 = Arrays.copyOf(glyphX0 == null ? new short[0] : glyphX0, capacity);
            glyphY0 = Arrays.copyOf(glyphY0 == null ? new short[0] : glyphY0, capacity);
            glyphX1 = Arrays.copyOf(glyphX1 == null ? new short[0] : glyphX1, capacity);
            glyphY1 = Arrays.copyOf(glyphY1 == null ? new short[0] : glyphY1, capacity);
            glyphAdvance = Arrays.copyOf(glyphAdvance == null ? new short[0] : glyphAdvance, capacity);
            glyphOffsetX = Arrays.copyOf(glyphOffsetX == null ? new short[0] : glyphOffsetX, capacity);
            glyphOffsetY = Arrays.copyOf(glyphOffsetY == null ? new short[0] : glyphOffsetY, capacity);
            glyphCapacity = capacity;
        }

        float units() {
            return (float) info.big16(info.head + 0x12);
        }
    }

    public int width;
    public int height;
    final int flags;
    float inverseWidth;
    float inverseHeight;
    byte[] texture;
    private ByteBuffer upload;
    public final int[] dirty = new int[4];
    Font[] fonts = new Font[4];
    int fontCount;

    int atlasWidth;
    int atlasHeight;
    short[] nodeX = new short[256];
    short[] nodeY = new short[256];
    short[] nodeWidth = new short[256];
    int nodeCount;
    int nodeCapacity;
    int scratchUsed;
    private final State[] states = new State[20];
    private int stateCount;

    private FontStash(int width, int height, int flags) {
        this.width = width;
        this.height = height;
        this.flags = flags;
        for (int i = 0; i < states.length; ++i) {
            states[i] = new State();
        }
    }

    public static FontStash create(int width, int height, int flags) {
        FontStash c = new FontStash(width, height, flags);
        c.atlasWidth = width;
        c.atlasHeight = height;
        c.nodeCapacity = 256;
        c.nodeCount = 1;
        c.nodeWidth[0] = (short) width;
        c.inverseWidth = 1.0f / (float) width;
        c.inverseHeight = 1.0f / (float) height;
        c.texture = new byte[width * height];
        c.dirty[0] = width;
        c.dirty[1] = height;
        c.dirty[2] = 0;
        c.dirty[3] = 0;
        c.whiteRectangle();
        c.stateCount = 1;
        c.clearState();
        return c;
    }

    boolean allocateScratch(int bytes, boolean checkBase) {
        int next = scratchUsed + bytes;
        if (next > SCRATCH_SIZE) {
            return false;
        }
        scratchUsed = next;
        return true;
    }

    public ByteBuffer textureBuffer() {
        if (upload == null || upload.capacity() != texture.length) {
            upload = ByteBuffer.allocateDirect(texture.length).order(ByteOrder.nativeOrder());
        }
        upload.clear();
        upload.put(texture);
        upload.flip();
        return upload;
    }

    public State state() {
        return states[stateCount - 1];
    }

    public void pushState() {
        if (stateCount < 20) {
            if (stateCount > 0) {
                states[stateCount].set(states[stateCount - 1]);
            }
            stateCount = stateCount + 1;
        }
    }

    public void popState() {
        if (stateCount >= 2) {
            --stateCount;
        }
    }

    public void clearState() {
        State s = state();
        s.font = 0;
        s.alignment = 65;
        s.size = 12.0f;
        s.color = 0xffffffff;
        s.blur = 0.0f;
        s.spacing = 0.0f;
    }

    private static int utf8Decode(int[] stateAndCodepoint, int value) {
        int type = UTF8[value];
        int state = stateAndCodepoint[0];
        stateAndCodepoint[1] = state != 0 ? ((stateAndCodepoint[1] << 6) | (value & 63)) : ((0xff >>> (type & 31)) & value);
        stateAndCodepoint[0] = UTF8[256 + type + state];
        return stateAndCodepoint[0];
    }

    private void flush() {
        if (dirty[0] < dirty[2] && dirty[1] < dirty[3]) {
            dirty[0] = width;
            dirty[1] = height;
            dirty[2] = 0;
            dirty[3] = 0;
        }
    }

    private void whiteRectangle() {
        int[] position = new int[2];
        if (!addAtlasRectangle(2, 2, position)) {
            return;
        }
        int x = position[0];
        int y = position[1];
        int p = width * y + x;
        texture[p] = (byte) 255;
        texture[p + 1] = (byte) 255;
        texture[p + width] = (byte) 255;
        texture[p + width + 1] = (byte) 255;
        dirty[0] = Math.min(dirty[0], x);
        dirty[1] = Math.min(dirty[1], y);
        dirty[2] = Math.max(dirty[2], x + 2);
        dirty[3] = Math.max(dirty[3], y + 2);
    }

    private Font selectedFont() {
        int index = state().font;
        return index >= 0 && index < fontCount && fonts[index].data != null ? fonts[index] : null;
    }

    private float baseline(Font f, short size, int align) {
        float offset = 0.0f;
        float s = size;
        if ((flags & 1) != 0) {
            if ((align & 8) != 0) {
                offset = (f.ascender * s) / 10.0f;
            } else if ((align & 16) != 0) {
                offset = (((f.ascender + f.descender) * 0.5f) * s) / 10.0f;
            } else if ((align & 0x60) == 0x20) {
                offset = (f.descender * s) / 10.0f;
            }
        } else {
            if ((align & 8) != 0) {
                offset = (f.ascender * s) / -10.0f;
            } else if ((align & 16) != 0) {
                offset = (((f.ascender + f.descender) * -0.5f) * s) / 10.0f;
            } else if ((align & 0x60) == 0x20) {
                offset = (f.descender * s) / -10.0f;
            }
        }
        return offset;
    }

    public int addFallback(int base, int fallback) {
        Font f = fonts[base];
        int n = f.fallbackCount;
        if (n < 20) {
            f.fallbackCount = n + 1;
            f.fallbacks[n] = fallback;
        }
        return n < 20 ? 1 : 0;
    }

    private static int strcmp(byte[] a, byte[] b) {
        int i = 0;
        while (true) {
            int x = i < a.length ? a[i] & 0xff : 0;
            int y = i < b.length ? b[i] & 0xff : 0;
            if (x != y) {
                return x - y;
            }
            if (x == 0) {
                return 0;
            }
            ++i;
        }
    }

    public int findFont(byte[] name) {
        for (int i = 0; i < fontCount; ++i) {
            if (strcmp(fonts[i].name, name) == 0) {
                return i;
            }
        }
        return -1;
    }

    public int addFontMemory(byte[] name, byte[] data, int size, int freeData, int index) {
        if (fontCount >= fonts.length) {
            fonts = Arrays.copyOf(fonts, fonts.length * 2);
        }
        Font f = new Font();
        f.allocateGlyphs(256);
        int id = fontCount;
        fontCount = id + 1;
        fonts[id] = f;
        for (int i = 0; i < 64; ++i) {
            f.name[i] = i < name.length ? name[i] : 0;
            if (i < name.length && name[i] == 0) {
                Arrays.fill(f.name, i, 64, (byte) 0);
                break;
            }
        }
        f.name[63] = 0;
        Arrays.fill(f.hash, -1);
        f.data = data;
        f.dataSize = size;
        f.freeData = freeData;
        scratchUsed = 0;
        f.info = new TrueType(this, data);
        int offset = TrueType.fontOffset(data, index);
        if (offset != -1 && f.info.initialize(offset) != 0) {
            TrueType t = f.info;
            int descender = t.signed16(t.hhea + 6);
            int ascender = t.signed16(t.hhea + 4) + t.signed16(t.hhea + 8);
            float lineHeight = (float) (ascender - descender);
            f.ascender = (float) ascender / lineHeight;
            f.descender = (float) descender / lineHeight;
            f.lineHeight = f.ascender - f.descender;
            return id;
        }
        fontCount = fontCount - 1;
        return -1;
    }

    boolean addAtlasRectangle(int rectWidth, int rectHeight, int[] position) {
        int best = -1;
        int bestY = -1;
        int bestX = -1;
        int bestHeight = atlasHeight;
        int bestWidth = atlasWidth;
        for (int i = 0; i < nodeCount; ++i) {
            int nx = nodeX[i];
            if (nx + rectWidth > atlasWidth) {
                continue;
            }
            int yy = nodeY[i];
            int remaining = rectWidth;
            int j = i;
            boolean fits = true;
            while (remaining > 0) {
                if (j >= nodeCount) {
                    fits = false;
                    break;
                }
                yy = Math.max(yy, nodeY[j]);
                if (yy + rectHeight > atlasHeight) {
                    fits = false;
                    break;
                }
                remaining -= nodeWidth[j++];
            }
            if (!fits || yy == -1) {
                continue;
            }
            int top = yy + rectHeight;
            if (top < bestHeight || (top == bestHeight && nodeWidth[i] < bestWidth)) {
                best = i;
                bestY = yy;
                bestX = nx;
                bestHeight = top;
                bestWidth = nodeWidth[i];
            }
        }
        if (best == -1) {
            return false;
        }
        if (nodeCount >= nodeCapacity) {
            nodeCapacity = nodeCapacity != 0 ? nodeCapacity * 2 : 8;
            nodeX = Arrays.copyOf(nodeX, nodeCapacity);
            nodeY = Arrays.copyOf(nodeY, nodeCapacity);
            nodeWidth = Arrays.copyOf(nodeWidth, nodeCapacity);
        }
        for (int i = nodeCount; i > best; --i) {
            nodeX[i] = nodeX[i - 1];
            nodeY[i] = nodeY[i - 1];
            nodeWidth[i] = nodeWidth[i - 1];
        }
        nodeX[best] = (short) bestX;
        nodeY[best] = (short) (bestY + rectHeight);
        nodeWidth[best] = (short) rectWidth;
        ++nodeCount;
        int i = best + 1;
        while (i < nodeCount) {
            int end = nodeX[best] + nodeWidth[best];
            if (end <= nodeX[i]) {
                break;
            }
            int shrink = end - nodeX[i];
            nodeX[i] = (short) end;
            nodeWidth[i] = (short) (nodeWidth[i] - shrink);
            if (nodeWidth[i] > 0) {
                break;
            }
            for (int j = i; j < nodeCount - 1; ++j) {
                nodeX[j] = nodeX[j + 1];
                nodeY[j] = nodeY[j + 1];
                nodeWidth[j] = nodeWidth[j + 1];
            }
            --nodeCount;
        }
        for (int j = 0; j < nodeCount - 1; ) {
            if (nodeY[j] == nodeY[j + 1]) {
                nodeWidth[j] = (short) (nodeWidth[j] + nodeWidth[j + 1]);
                for (int k = j + 1; k < nodeCount - 1; ++k) {
                    nodeX[k] = nodeX[k + 1];
                    nodeY[k] = nodeY[k + 1];
                    nodeWidth[k] = nodeWidth[k + 1];
                }
                --nodeCount;
            } else {
                ++j;
            }
        }
        position[0] = bestX;
        position[1] = bestY;
        return true;
    }

    private static int blurAlpha(short blur) {
        float input = -2.3f / ((float) blur * 0.57735f + 1.0f);
        int magnitude = Float.floatToRawIntBits(input) & 0x7fffffff;
        int k = 0;
        float hi = input;
        float lo = 0.0f;
        float x = input;
        if (magnitude >= 0x3eb17219) {
            k = magnitude <= 0x3f851592 ? -1 : Sse.cvttss2si(input * 1.442695f - 0.5f);
            hi = input + (float) k * -0.69314575f;
            lo = (float) k * 1.4286068e-06f;
            x = hi - lo;
        }
        float square = x * x;
        float correction = x - ((-0.0027667333f * square + 0.16666625f) * square);
        float result = ((((x * correction) / (2.0f - correction)) - lo) + hi) + 1.0f;
        float scaled = Float.intBitsToFloat(Float.floatToRawIntBits(result) + k * 0x800000);
        return Sse.cvttss2si((1.0f - scaled) * 65536.0f);
    }

    private static void blurGlyph(byte[] data, int base, int width, int height, int stride, short blur) {
        int alpha = blurAlpha(blur);
        for (int pass = 0; pass < 2; ++pass) {
            for (int x = 0; x < width; ++x) {
                int p = base + x;
                int z = 0;
                for (int y = 1; y < height; ++y) {
                    z = step(z, data[p + y * stride] & 0xff, alpha);
                    data[p + y * stride] = (byte) (z >>> 7);
                }
                data[p + (height - 1) * stride] = 0;
                z = 0;
                for (int y = height - 2; y >= 0; --y) {
                    z = step(z, data[p + y * stride] & 0xff, alpha);
                    data[p + y * stride] = (byte) (z >>> 7);
                }
                data[p] = 0;
            }
            for (int y = 0; y < height; ++y) {
                int p = base + y * stride;
                int z = 0;
                for (int x = 1; x < width; ++x) {
                    z = step(z, data[p + x] & 0xff, alpha);
                    data[p + x] = (byte) (z >>> 7);
                }
                data[p + width - 1] = 0;
                z = 0;
                for (int x = width - 2; x >= 0; --x) {
                    z = step(z, data[p + x] & 0xff, alpha);
                    data[p + x] = (byte) (z >>> 7);
                }
                data[p] = 0;
            }
        }
    }

    private static int step(int z, int value, int alpha) {
        return z + (((value * 128 - z) * alpha) >> 16);
    }

    int getGlyph(Font base, int codepoint, short size, short blur, int option) {
        if (size < 2) {
            return -1;
        }
        short limited = (short) Math.min(20, blur);
        scratchUsed = 0;
        int hash = ~(codepoint << 15) + codepoint;
        hash = (hash ^ (hash >>> 10)) * 9;
        hash ^= hash >>> 6;
        hash = ~(hash << 11) + hash;
        hash = (hash ^ (hash >>> 16)) & 255;
        int glyph = -1;
        for (int i = base.hash[hash]; i != -1; i = base.glyphNext[i]) {
            if (base.glyphCodepoint[i] == codepoint && base.glyphSize[i] == size && base.glyphBlur[i] == limited) {
                if (option == 1 || (base.glyphX0[i] >= 0 && base.glyphY0[i] >= 0)) {
                    return i;
                }
                glyph = i;
                break;
            }
        }
        Font font = base;
        int index = font.info.glyphIndex(codepoint);
        if (index == 0) {
            int count = base.fallbackCount;
            for (int i = 0; i < count; ++i) {
                font = fonts[base.fallbacks[i]];
                index = font.info.glyphIndex(codepoint);
                if (index != 0) {
                    break;
                }
            }
            if (index == 0) {
                font = base;
            }
        }
        int pad = limited + 2;
        float scale = ((float) size / 10.0f) / font.units();
        TrueType info = font.info;
        int metricCount = info.big16(info.hhea + 0x22);
        int metricIndex = index < metricCount ? index : metricCount - 1;
        int advance = info.signed16(info.hmtx + metricIndex * 4);
        int x0;
        int y0;
        int x1;
        int y1;
        int[] box = new int[4];
        if (info.glyphBox(index, box)) {
            x0 = Sse.cvttss2si(ScalarRounding.floorGameFloat((float) box[0] * scale + 0.0f));
            y0 = Sse.cvttss2si(ScalarRounding.floorGameFloat((float) (-box[3]) * scale + 0.0f));
            x1 = Sse.cvttss2si(ScalarRounding.ceilGameFloat((float) box[2] * scale + 0.0f));
            y1 = Sse.cvttss2si(ScalarRounding.ceilGameFloat((float) (-box[1]) * scale + 0.0f));
        } else {
            x0 = 0;
            y0 = 0;
            x1 = 0;
            y1 = 0;
        }
        int glyphWidth = x1 - x0 + pad * 2;
        int glyphHeight = y1 - y0 + pad * 2;
        int atlasX = -1;
        int atlasY = -1;
        if (option == 2) {
            int[] position = new int[2];
            if (!addAtlasRectangle(glyphWidth, glyphHeight, position)) {
                return -1;
            }
            atlasX = position[0];
            atlasY = position[1];
        }
        if (glyph == -1) {
            if (base.glyphCount >= base.glyphCapacity) {
                base.allocateGlyphs(base.glyphCapacity != 0 ? base.glyphCapacity * 2 : 8);
            }
            int count = base.glyphCount++;
            glyph = count;
            base.glyphCodepoint[glyph] = codepoint;
            base.glyphSize[glyph] = size;
            base.glyphBlur[glyph] = limited;
            base.glyphNext[glyph] = base.hash[hash];
            base.hash[hash] = count;
        }
        base.glyphIndex[glyph] = index;
        base.glyphX0[glyph] = (short) atlasX;
        base.glyphY0[glyph] = (short) atlasY;
        base.glyphX1[glyph] = (short) (atlasX + glyphWidth);
        base.glyphY1[glyph] = (short) (atlasY + glyphHeight);
        base.glyphAdvance[glyph] = (short) Sse.cvttss2si(((float) advance * scale) * 10.0f);
        base.glyphOffsetX[glyph] = (short) (x0 - pad);
        base.glyphOffsetY[glyph] = (short) (y0 - pad);
        if (option != 1) {
            int gx0 = base.glyphX0[glyph];
            int gy0 = base.glyphY0[glyph];
            info.rasterizeGlyph(texture, (gy0 + pad) * width + gx0 + pad, x1 - x0, y1 - y0, width, scale, scale, 0.0f,
                    0.0f, index);
            int bitmap = gy0 * width + gx0;
            for (int y = 0; y < glyphHeight; ++y) {
                texture[bitmap + y * width] = 0;
                texture[bitmap + y * width + glyphWidth - 1] = 0;
            }
            for (int x = 0; x < glyphWidth; ++x) {
                texture[bitmap + x] = 0;
                texture[bitmap + (glyphHeight - 1) * width + x] = 0;
            }
            if (blur > 0) {
                scratchUsed = 0;
                blurGlyph(texture, bitmap, glyphWidth, glyphHeight, width, limited);
            }
            dirty[0] = Math.min(dirty[0], base.glyphX0[glyph]);
            dirty[1] = Math.min(dirty[1], base.glyphY0[glyph]);
            dirty[2] = Math.max(dirty[2], base.glyphX1[glyph]);
            dirty[3] = Math.max(dirty[3], base.glyphY1[glyph]);
        }
        return glyph;
    }

    void getQuad(Font font, int previous, Font glyphFont, int g, float scale, float spacing, float[] xy, FontQuad q) {
        if (previous != -1) {
            int kern = font.info.kerning(previous, glyphFont.glyphIndex[g]);
            xy[0] = (float) Sse.cvttss2si(((float) kern * scale + spacing) + 0.5f) + xy[0];
        }
        float ox = (short) (glyphFont.glyphOffsetX[g] + 1);
        float oy = (short) (glyphFont.glyphOffsetY[g] + 1);
        float x0 = (float) (glyphFont.glyphX0[g] + 1);
        float y0 = (float) (glyphFont.glyphY0[g] + 1);
        float x1 = (float) (glyphFont.glyphX1[g] - 1);
        float y1 = (float) (glyphFont.glyphY1[g] - 1);
        q.x0 = ScalarRounding.floorGameFloat(ox + xy[0]);
        float yy = xy[1];
        if ((flags & 1) != 0) {
            q.y0 = ScalarRounding.floorGameFloat(yy + oy);
            q.y1 = (q.y0 + y1) - y0;
        } else {
            q.y0 = ScalarRounding.floorGameFloat(yy - oy);
            q.y1 = (q.y0 - y1) + y0;
        }
        q.x1 = (q.x0 + x1) - x0;
        q.s0 = x0 * inverseWidth;
        q.t0 = y0 * inverseHeight;
        q.s1 = x1 * inverseWidth;
        q.t1 = y1 * inverseHeight;
        xy[0] = (float) Sse.cvttss2si(((float) glyphFont.glyphAdvance[g] / 10.0f) + 0.5f) + xy[0];
    }

    public float textBounds(float x, float y, byte[] string, int start, int end, float[] bounds) {
        Font font = selectedFont();
        if (font == null) {
            return 0.0f;
        }
        State state = state();
        short size = (short) Sse.cvttss2si(state.size * 10.0f);
        short blur = (short) Sse.cvttss2si(state.blur);
        y = y + baseline(font, size, state.alignment);
        float scale = ((float) size / 10.0f) / font.units();
        if (end < 0) {
            end = VectorContext.strlen(string, start);
        }
        float[] xy = {x, y};
        float minx = x;
        float miny = y;
        float maxx = x;
        float maxy = y;
        int[] decoder = new int[2];
        int previous = -1;
        FontQuad q = new FontQuad();
        for (int p = start; p != end; ++p) {
            if (utf8Decode(decoder, string[p] & 0xff) != 0) {
                continue;
            }
            int glyph = getGlyph(font, decoder[1], size, blur, 1);
            if (glyph == -1) {
                previous = -1;
                continue;
            }
            getQuad(font, previous, font, glyph, scale, state.spacing, xy, q);
            previous = font.glyphIndex[glyph];
            float low = q.y0;
            float high = q.y1;
            if ((flags & 1) == 0) {
                low = q.y1;
                high = q.y0;
            }
            miny = Floats.min(low, miny);
            maxy = high > maxy ? high : maxy;
            minx = Floats.min(q.x0, minx);
            maxx = Floats.max(q.x1, maxx);
        }
        float advance = xy[0] - x;
        if ((state.alignment & 1) == 0) {
            if ((state.alignment & 4) != 0) {
                minx = minx - advance;
                maxx = maxx - advance;
            } else if ((state.alignment & 2) != 0) {
                minx = minx + advance * -0.5f;
                maxx = maxx + advance * -0.5f;
            }
        }
        if (bounds != null) {
            bounds[0] = minx;
            bounds[1] = miny;
            bounds[2] = maxx;
            bounds[3] = maxy;
        }
        return advance;
    }

    public boolean initializeIterator(FontTextIterator i, float x, float y, byte[] string, int start, int end,
                                      int option) {
        i.clear();
        State state = state();
        if (state.font < 0 || state.font >= fontCount) {
            return false;
        }
        i.font = fonts[state.font];
        if (i.font.data == null) {
            return false;
        }
        int size = Sse.cvttss2si(state.size * 10.0f);
        i.size = (short) size;
        i.blur = (short) Sse.cvttss2si(state.blur);
        i.scale = ((float) size / 10.0f) / i.font.units();
        if ((state.alignment & 1) == 0) {
            if ((state.alignment & 4) != 0) {
                x = x - textBounds(x, y, string, start, end, null);
            } else if ((state.alignment & 2) != 0) {
                x = x + textBounds(x, y, string, start, end, null) * -0.5f;
            }
        }
        float offset = baseline(i.font, i.size, state.alignment);
        if (end < 0) {
            end = VectorContext.strlen(string, start);
        }
        i.x = x;
        i.nextX = x;
        i.y = y + offset;
        i.nextY = i.y;
        i.spacing = state.spacing;
        i.text = string;
        i.string = start;
        i.next = start;
        i.end = end;
        i.previousGlyph = -1;
        i.bitmapOption = option;
        return true;
    }

    public boolean nextIterator(FontTextIterator i, FontQuad q) {
        int p = i.next;
        i.string = p;
        if (p == i.end) {
            return false;
        }
        int[] decoder = {i.utf8State, i.codepoint};
        do {
            utf8Decode(decoder, i.text[p] & 0xff);
            i.utf8State = decoder[0];
            i.codepoint = decoder[1];
            if (i.utf8State == 0) {
                i.x = i.nextX;
                i.y = i.nextY;
                int g = getGlyph(i.font, i.codepoint, i.size, i.blur, i.bitmapOption);
                if (g != -1) {
                    float[] xy = {i.nextX, i.nextY};
                    getQuad(i.font, i.previousGlyph, i.font, g, i.scale, i.spacing, xy, q);
                    i.nextX = xy[0];
                    i.nextY = xy[1];
                    i.previousGlyph = i.font.glyphIndex[g];
                } else {
                    i.previousGlyph = -1;
                }
                break;
            }
            ++p;
        } while (p != i.end);
        i.next = p == i.end ? p : p + 1;
        return true;
    }

    public boolean fontMetrics(float[] output) {
        Font f = selectedFont();
        if (f == null) {
            return false;
        }
        float size = (short) Sse.cvttss2si(state().size * 10.0f);
        output[0] = (size * f.ascender) / 10.0f;
        output[1] = (size * f.descender) / 10.0f;
        output[2] = (size * f.lineHeight) / 10.0f;
        return true;
    }

    public boolean lineBounds(float y, float[] output) {
        Font f = selectedFont();
        if (f == null) {
            return false;
        }
        State s = state();
        short isize = (short) Sse.cvttss2si(s.size * 10.0f);
        float size = isize;
        float offset = baseline(f, isize, s.alignment);
        if ((flags & 1) != 0) {
            output[0] = (y + offset) - (f.ascender * size) / 10.0f;
            output[1] = (size * f.lineHeight) / 10.0f + output[0];
        } else {
            output[1] = ((f.descender * size) / 10.0f + y) + offset;
            output[0] = output[1] - (size * f.lineHeight) / 10.0f;
        }
        return true;
    }

    public int resetAtlas(int w, int h) {
        flush();
        atlasWidth = w;
        atlasHeight = h;
        nodeX[0] = 0;
        nodeY[0] = 0;
        nodeWidth[0] = (short) w;
        nodeCount = 1;
        texture = new byte[w * h];
        dirty[0] = w;
        dirty[1] = h;
        dirty[2] = 0;
        dirty[3] = 0;
        for (int i = 0; i < fontCount; ++i) {
            fonts[i].glyphCount = 0;
            Arrays.fill(fonts[i].hash, -1);
        }
        width = w;
        height = h;
        inverseWidth = 1.0f / (float) w;
        inverseHeight = 1.0f / (float) h;
        whiteRectangle();
        return 1;
    }
}

package yt.ren.slate.ui.font;

import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.Sse;

import java.util.Arrays;

public class TrueType {
    final FontStash owner;
    final byte[] data;
    int fontStart;
    int numGlyphs;
    int loca;
    int head;
    int glyf;
    int hhea;
    int hmtx;
    int kern;
    int gpos;
    int indexMap;
    int indexToLocFormat;

    TrueTypeCff.Buffer cff = new TrueTypeCff.Buffer();
    TrueTypeCff.Buffer charstrings = new TrueTypeCff.Buffer();
    TrueTypeCff.Buffer globalSubroutines = new TrueTypeCff.Buffer();
    TrueTypeCff.Buffer localSubroutines = new TrueTypeCff.Buffer();
    TrueTypeCff.Buffer fontDictionaries = new TrueTypeCff.Buffer();
    TrueTypeCff.Buffer fdSelect = new TrueTypeCff.Buffer();

    TrueType(FontStash owner, byte[] data) {
        this.owner = owner;
        this.data = data;
    }

    private int u8(int offset) {
        return data[offset] & 0xff;
    }

    int big16(int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    int signed16(int offset) {
        return (short) big16(offset);
    }

    int big32(int offset) {
        return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static boolean fontSignature(byte[] data) {
        int b0 = data[0] & 0xff;
        if (b0 < 0x4f) {
            if (b0 == 0) {
                if ((data[1] & 0xff) != 1) {
                    return false;
                }
            } else {
                if (b0 != 0x31 || (data[1] & 0xff) != 0) {
                    return false;
                }
            }
            return data[2] == 0 && data[3] == 0;
        }
        if (b0 == 0x4f) {
            return data[1] == 'T' && data[2] == 'T' && data[3] == 'O';
        }
        if (b0 != 't') {
            return false;
        }
        return (data[1] == 'r' && data[2] == 'u' && data[3] == 'e') || (data[1] == 'y' && data[2] == 'p' && data[3] == '1');
    }

    private int table(int start, String tag) {
        int position = start + 12;
        int count = big16(start + 4);
        for (int i = 0; i < count; ++i, position += 16) {
            if (data[position] == tag.charAt(0) && data[position + 1] == tag.charAt(1)
                    && data[position + 2] == tag.charAt(2) && data[position + 3] == tag.charAt(3)) {
                return big32(position + 8);
            }
        }
        return 0;
    }

    static int fontOffset(byte[] data, int index) {
        if (fontSignature(data)) {
            return index == 0 ? 0 : -1;
        }
        if (data[0] != 't' || data[1] != 't' || data[2] != 'c' || data[3] != 'f') {
            return -1;
        }
        int version = ((data[4] & 0xff) << 24) | ((data[5] & 0xff) << 16) | ((data[6] & 0xff) << 8) | (data[7] & 0xff);
        int count = ((data[8] & 0xff) << 24) | ((data[9] & 0xff) << 16) | ((data[10] & 0xff) << 8) | (data[11] & 0xff);
        if ((version == 0x10000 || version == 0x20000) && index < count) {
            int p = 12 + index * 4;
            return ((data[p] & 0xff) << 24) | ((data[p + 1] & 0xff) << 16) | ((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff);
        }
        return -1;
    }

    int initialize(int offset) {
        fontStart = offset;
        int cmap = table(offset, "cmap");
        loca = table(offset, "loca");
        head = table(offset, "head");
        glyf = table(offset, "glyf");
        hhea = table(offset, "hhea");
        hmtx = table(offset, "hmtx");
        kern = table(offset, "kern");
        gpos = table(offset, "GPOS");
        if (cmap == 0 || head == 0 || hhea == 0 || hmtx == 0) {
            return 0;
        }
        if (glyf != 0) {
            if (loca == 0) {
                return 0;
            }
        } else {
            int cffOffset = table(offset, "CFF ");
            if (cffOffset == 0) {
                return 0;
            }
            if (!TrueTypeCff.initialize(this, cffOffset)) {
                return 0;
            }
        }
        int maxp = table(offset, "maxp");
        numGlyphs = maxp == 0 ? 65535 : big16(maxp + 4);
        indexMap = 0;
        int entry = cmap + 4;
        int count = big16(cmap + 2);
        for (int i = 0; i < count; ++i, entry += 8) {
            int platform = big16(entry);
            if (platform == 0 || (platform == 3 && (big16(entry + 2) == 10 || big16(entry + 2) == 1))) {
                indexMap = big32(entry + 4) + cmap;
            }
        }
        if (indexMap == 0) {
            return 0;
        }
        indexToLocFormat = big16(head + 0x32);
        return 1;
    }

    int glyphIndex(int codepoint) {
        int map = indexMap;
        int format = big16(map);
        if (format == 0) {
            return codepoint < big16(map + 2) - 6 ? u8(map + 6 + codepoint) : 0;
        }
        if (format == 6) {
            int first = big16(map + 6);
            int count = big16(map + 8);
            return Integer.compareUnsigned(first, codepoint) <= 0 && Integer.compareUnsigned(codepoint, first + count) < 0
                    ? big16(map + 10 + (codepoint - first) * 2) : 0;
        }
        if (format == 4) {
            if (codepoint >= 0x10000) {
                return 0;
            }
            int segmentCountX2 = big16(map + 6);
            int searchRange = big16(map + 8);
            short selector = (short) big16(map + 10);
            int rangeShift = big16(map + 12) & 0xfffe;
            if (codepoint < big16(map + 14 + rangeShift)) {
                rangeShift = 0;
            }
            int search = map + 12 + rangeShift;
            while (selector != 0) {
                searchRange >>>= 1;
                int step = searchRange & 0xfffe;
                if (codepoint > big16(search + step)) {
                    search += step;
                }
                selector = (short) (selector - 1);
            }
            int itemBytes = (search - map + 0x1fff4) & 0x1fffe;
            int first = big16(map + 0x10 + (segmentCountX2 & 0xfffe) + itemBytes);
            if (first > codepoint) {
                return 0;
            }
            int segmentCount = segmentCountX2 >>> 1;
            int rangeOffsetPosition = itemBytes + 0x10 + segmentCount * 6;
            int offset = big16(map + rangeOffsetPosition);
            if (offset == 0) {
                return (big16(map + itemBytes + 0x10 + segmentCount * 4) + codepoint) & 0xffff;
            }
            return big16(map + rangeOffsetPosition + offset + (codepoint - first) * 2);
        }
        if (format == 12 || format == 13) {
            int low = 0;
            int high = big32(map + 12);
            while (low < high) {
                int middle = ((high - low) >>> 1) + low;
                int offset = middle * 12;
                int first = big32(map + 0x10 + offset);
                if (Integer.compareUnsigned(codepoint, first) < 0) {
                    high = middle;
                } else if (Integer.compareUnsigned(codepoint, big32(map + 0x14 + offset)) <= 0) {
                    return big32(map + 0x18 + offset) + (format == 12 ? codepoint - first : 0);
                } else {
                    low = middle + 1;
                }
            }
        }
        return 0;
    }

    private int coverage(int coverage, int glyph) {
        int format = big16(coverage);
        int count = big16(coverage + 2);
        int low = 0;
        int high = count - 1;
        if (format == 1) {
            while (low <= high) {
                int middle = (low + high) >> 1;
                int index = big16(coverage + 4 + middle * 2);
                if (glyph < index) {
                    high = middle - 1;
                } else if (Integer.compareUnsigned(glyph, index) <= 0) {
                    return middle;
                } else {
                    low = middle + 1;
                }
            }
        } else if (format == 2) {
            while (low <= high) {
                int middle = (low + high) >> 1;
                int record = coverage + 4 + middle * 6;
                int first = big16(record);
                if (glyph < first) {
                    high = middle - 1;
                } else if (Integer.compareUnsigned(glyph, big16(record + 2)) <= 0) {
                    return glyph - first + big16(record + 4);
                } else {
                    low = middle + 1;
                }
            }
        }
        return -1;
    }

    private int classDefinition(int definition, int glyph) {
        int format = big16(definition);
        if (format == 1) {
            int first = big16(definition + 2);
            int count = big16(definition + 4);
            if (glyph >= first && Integer.compareUnsigned(glyph, first + count) < 0) {
                return big16(definition + 6 + (glyph - first) * 2);
            }
        } else if (format == 2) {
            int low = 0;
            int high = big16(definition + 2) - 1;
            while (low <= high) {
                int middle = (low + high) >> 1;
                int record = definition + 4 + middle * 6;
                if (glyph < big16(record)) {
                    high = middle - 1;
                } else if (Integer.compareUnsigned(glyph, big16(record + 2)) <= 0) {
                    return big16(record + 4);
                } else {
                    low = middle + 1;
                }
            }
        }
        return -1;
    }

    int kerning(int left, int right) {
        if (gpos == 0) {
            if (kern == 0) {
                return 0;
            }
            int table = kern;
            if (big16(table + 2) == 0 || big16(table + 8) != 1) {
                return 0;
            }
            int low = 0;
            int high = big16(table + 10) - 1;
            int key = (left << 16) | right;
            while (low <= high) {
                int middle = (low + high) >> 1;
                int entry = table + 0x12 + middle * 6;
                int pair = big32(entry);
                if (Integer.compareUnsigned(key, pair) < 0) {
                    high = middle - 1;
                } else if (Integer.compareUnsigned(key, pair) <= 0) {
                    return signed16(entry + 4);
                } else {
                    low = middle + 1;
                }
            }
            return 0;
        }
        int gposTable = gpos;
        if (big16(gposTable) != 1 || big16(gposTable + 2) != 0) {
            return 0;
        }
        int lookups = gposTable + big16(gposTable + 8);
        int count = big16(lookups);
        for (int i = 0; i < count; ++i) {
            int lookup = lookups + big16(lookups + 2 + i * 2);
            if (big16(lookup) != 2) {
                continue;
            }
            int subtableCount = big16(lookup + 4);
            for (int j = 0; j < subtableCount; ++j) {
                int subtable = lookup + big16(lookup + 6 + j * 2);
                int covered = coverage(subtable + big16(subtable + 2), left);
                if (covered == -1) {
                    continue;
                }
                int format = big16(subtable);
                if (format == 1) {
                    if (big16(subtable + 4) != 4 || big16(subtable + 6) != 0) {
                        return 0;
                    }
                    int pairs = subtable + big16(subtable + 10 + covered * 2);
                    int low = 0;
                    int high = big16(pairs) - 1;
                    while (low <= high) {
                        int middle = (low + high) >> 1;
                        int entry = pairs + 2 + middle * 4;
                        int second = big16(entry);
                        if (right < second) {
                            high = middle - 1;
                        } else if (Integer.compareUnsigned(right, second) <= 0) {
                            return signed16(entry + 2);
                        } else {
                            low = middle + 1;
                        }
                    }
                } else if (format == 2) {
                    int valueFormat = big16(subtable + 4);
                    int secondFormat = big16(subtable + 6);
                    int secondOffset = big16(subtable + 10);
                    int firstClass = classDefinition(subtable + big16(subtable + 8), left);
                    int secondClass = classDefinition(subtable + secondOffset, right);
                    if (valueFormat != 4 || secondFormat != 0) {
                        return 0;
                    }
                    if (firstClass >= 0 && Integer.compareUnsigned(firstClass, big16(subtable + 12)) < 0 && secondClass >= 0) {
                        int columns = big16(subtable + 14);
                        if (Integer.compareUnsigned(secondClass, columns) < 0) {
                            return signed16(subtable + 0x10 + firstClass * columns * 2 + secondClass * 2);
                        }
                    }
                }
            }
        }
        return 0;
    }

    boolean glyphBox(int glyph, int[] box) {
        if (cff.size != 0) {
            TrueTypeCff.Context context = new TrueTypeCff.Context();
            context.bounds = 1;
            boolean success = TrueTypeCff.run(this, glyph, context);
            box[0] = success ? context.minimumX : 0;
            box[1] = success ? context.minimumY : 0;
            box[2] = success ? context.maximumX : 0;
            box[3] = success ? context.maximumY : 0;
            return true;
        }
        if (glyph >= numGlyphs || indexToLocFormat > 1) {
            return false;
        }
        int begin;
        int end;
        if (indexToLocFormat == 0) {
            begin = big16(loca + glyph * 2) * 2;
            end = big16(loca + glyph * 2 + 2) * 2;
        } else {
            begin = big32(loca + glyph * 4);
            end = big32(loca + glyph * 4 + 4);
        }
        int offset = glyf + begin;
        if (offset < 0 || begin == end) {
            return false;
        }
        box[0] = signed16(offset + 2);
        box[1] = signed16(offset + 4);
        box[2] = signed16(offset + 6);
        box[3] = signed16(offset + 8);
        return true;
    }

    static class Vertex {
        short x;
        short y;
        short controlX;
        short controlY;
        short control2X;
        short control2Y;
        int type;

        void set(Vertex o) {
            x = o.x;
            y = o.y;
            controlX = o.controlX;
            controlY = o.controlY;
            control2X = o.control2X;
            control2Y = o.control2Y;
            type = o.type;
        }
    }

    private static short midpoint(short a, short b) {
        return (short) ((a + b) >>> 1);
    }

    private static short converted(float value, boolean saturate) {
        int integer = Sse.cvttss2si(value);
        if (saturate) {
            if (integer > 32767) {
                integer = 32767;
            }
            if (integer < -32768) {
                integer = -32768;
            }
        }
        return (short) integer;
    }

    private static class Builder {
        final Vertex[] vertices;
        int count;

        Builder(Vertex[] vertices) {
            this.vertices = vertices;
        }

        void emit(int type, short x, short y, short cx, short cy) {
            Vertex v = vertices[count++];
            v.type = type;
            v.x = x;
            v.y = y;
            v.controlX = cx;
            v.controlY = cy;
        }

        void emit(int type, short x, short y) {
            emit(type, x, y, (short) 0, (short) 0);
        }

        void closeContour(boolean startOff, boolean previousOff, short sx, short sy, short scx, short scy, short cx,
                          short cy) {
            if (startOff) {
                if (previousOff) {
                    emit(3, midpoint(cx, scx), midpoint(cy, scy), cx, cy);
                }
                emit(3, sx, sy, scx, scy);
            } else if (previousOff) {
                emit(3, sx, sy, cx, cy);
            } else {
                emit(2, sx, sy);
            }
        }
    }

    private static Vertex[] newVertices(int count) {
        Vertex[] result = new Vertex[count];
        for (int i = 0; i < count; ++i) {
            result[i] = new Vertex();
        }
        return result;
    }

    private static void transformComponent(Vertex[] vertices, int count, float a, float b, float c, float d, float tx,
                                           float ty) {
        float scaleX = (float) Math.sqrt(c * c + d * d);
        float scaleY = (float) Math.sqrt(a * a + b * b);
        int bulk = count < 9 ? 0 : count - ((count & 7) != 0 ? (count & 7) : 8);
        for (int i = 0; i < count; ++i) {
            Vertex v = vertices[i];
            float x = v.x;
            float y = v.y;
            float cx = v.controlX;
            float cy = v.controlY;
            boolean bulkItem = i < bulk;
            int lane = i & 7;
            boolean saturated = !bulkItem || lane == 1 || lane == 2 || lane == 3;
            float nx = ((a * x + c * y) + tx) * scaleY;
            float ny = ((x * b + y * d) + ty) * scaleX;
            float ncx = ((a * cx + c * cy) + tx) * scaleY;
            float ncy = ((cx * b + cy * d) + ty) * scaleX;
            v.x = converted(nx, saturated);
            v.y = converted(ny, saturated);
            v.controlX = converted(ncx, saturated && (!bulkItem || lane != 2));
            v.controlY = converted(ncy, saturated);
        }
    }

    static class Shape {
        Vertex[] vertices;
        int count;
    }

    Shape glyphShape(int glyph) {
        Shape result = new Shape();
        if (cff.size != 0) {
            TrueTypeCff.Context bounds = new TrueTypeCff.Context();
            bounds.bounds = 1;
            if (TrueTypeCff.run(this, glyph, bounds)) {
                if (owner.allocateScratch((bounds.count * 14 + 15) & ~15, false)) {
                    TrueTypeCff.Context shape = new TrueTypeCff.Context();
                    shape.vertices = newVertices(bounds.count);
                    if (TrueTypeCff.run(this, glyph, shape)) {
                        result.vertices = shape.vertices;
                        result.count = shape.count;
                    }
                }
            }
            return result;
        }
        if (glyph >= numGlyphs || indexToLocFormat >= 2) {
            return result;
        }
        boolean shortOffsets = indexToLocFormat == 0;
        int begin = shortOffsets ? big16(loca + glyph * 2) * 2 : big32(loca + glyph * 4);
        int end = shortOffsets ? big16(loca + glyph * 2 + 2) * 2 : big32(loca + glyph * 4 + 4);
        if (begin == end) {
            return result;
        }
        int offset = begin + glyf;
        if (offset < 0) {
            return result;
        }
        int bytes = offset;
        int contours = signed16(bytes);
        if (contours == 0) {
            return result;
        }
        if (contours < 0) {
            int cursor = bytes + 10;
            Vertex[] all = null;
            int total = 0;
            while (true) {
                int flags = big16(cursor);
                int component = big16(cursor + 2);
                cursor += 4;
                float tx = 0.0f, ty = 0.0f, a = 1.0f, b = 0.0f, c = 0.0f, d = 1.0f;
                if ((flags & 2) != 0) {
                    if ((flags & 1) != 0) {
                        tx = (float) signed16(cursor);
                        ty = (float) signed16(cursor + 2);
                        cursor += 4;
                    } else {
                        tx = data[cursor];
                        ty = data[cursor + 1];
                        cursor += 2;
                    }
                }
                float unit = Float.intBitsToFloat(0x38800000);
                if ((flags & 8) != 0) {
                    a = d = (float) signed16(cursor) * unit;
                    cursor += 2;
                } else if ((flags & 64) != 0) {
                    a = (float) signed16(cursor) * unit;
                    d = (float) signed16(cursor + 2) * unit;
                    cursor += 4;
                } else if ((flags & 128) != 0) {
                    a = (float) signed16(cursor) * unit;
                    b = (float) signed16(cursor + 2) * unit;
                    c = (float) signed16(cursor + 4) * unit;
                    d = (float) signed16(cursor + 6) * unit;
                    cursor += 8;
                }
                Shape part = glyphShape(component);
                int count = part.count;
                if (count > 0) {
                    transformComponent(part.vertices, count, a, b, c, d, tx, ty);
                    int next = total + count;
                    if (!owner.allocateScratch((next * 14 + 15) & ~15, true)) {
                        return new Shape();
                    }
                    Vertex[] combined = newVertices(next);
                    for (int i = 0; i < total; ++i) {
                        combined[i].set(all[i]);
                    }
                    for (int i = 0; i < count; ++i) {
                        combined[total + i].set(part.vertices[i]);
                    }
                    all = combined;
                    total = next;
                }
                if ((flags & 32) == 0) {
                    result.vertices = all;
                    result.count = total;
                    return result;
                }
            }
        }
        int pointCount = big16(bytes + 8 + contours * 2) + 1;
        int allocation = ((pointCount + contours * 2) * 14 + 15) & 0x7ffffff0;
        if (!owner.allocateScratch(allocation, true)) {
            return result;
        }
        Vertex[] vertices = newVertices(pointCount + contours * 2);
        int rawBase = contours * 2;
        int cursor = bytes + 12 + contours * 2 + big16(bytes + 10 + contours * 2);
        int flags = 0;
        int repeat = 0;
        for (int i = 0; i < pointCount; ++i) {
            if (repeat != 0) {
                --repeat;
            } else {
                flags = u8(cursor++);
                if ((flags & 8) != 0) {
                    repeat = u8(cursor++);
                }
            }
            vertices[rawBase + i].type = flags;
        }
        short position = 0;
        for (int i = 0; i < pointCount; ++i) {
            int flag = vertices[rawBase + i].type;
            if ((flag & 2) != 0) {
                int delta = u8(cursor++);
                position = (short) (position + ((flag & 16) != 0 ? delta : -delta));
            } else if ((flag & 16) == 0) {
                position = (short) (position + big16(cursor));
                cursor += 2;
            }
            vertices[rawBase + i].x = position;
        }
        position = 0;
        for (int i = 0; i < pointCount; ++i) {
            int flag = vertices[rawBase + i].type;
            if ((flag & 4) != 0) {
                int delta = u8(cursor++);
                position = (short) (position + ((flag & 32) != 0 ? delta : -delta));
            } else if ((flag & 32) == 0) {
                position = (short) (position + big16(cursor));
                cursor += 2;
            }
            vertices[rawBase + i].y = position;
        }
        Builder out = new Builder(vertices);
        int next = 0;
        int contour = 0;
        boolean startOff = false;
        boolean previousOff = false;
        short sx = 0, sy = 0, scx = 0, scy = 0, cx = 0, cy = 0;
        for (int i = 0; i < pointCount; ++i) {
            Vertex raw = vertices[rawBase + i];
            short x = raw.x;
            short y = raw.y;
            int flag = raw.type;
            if (i == next) {
                if (i != 0) {
                    out.closeContour(startOff, previousOff, sx, sy, scx, scy, cx, cy);
                }
                startOff = (flag & 1) == 0;
                if (startOff) {
                    scx = x;
                    scy = y;
                    Vertex following = vertices[rawBase + i + 1];
                    if ((following.type & 1) == 0) {
                        x = midpoint(x, following.x);
                        y = midpoint(y, following.y);
                    } else {
                        ++i;
                        x = vertices[rawBase + i].x;
                        y = vertices[rawBase + i].y;
                    }
                }
                sx = x;
                sy = y;
                out.emit(1, x, y);
                previousOff = false;
                next = big16(bytes + 10 + (contour++) * 2) + 1;
            } else if ((flag & 1) == 0) {
                if (previousOff) {
                    out.emit(3, midpoint(cx, x), midpoint(cy, y), cx, cy);
                }
                cx = x;
                cy = y;
                previousOff = true;
            } else {
                if (previousOff) {
                    out.emit(3, x, y, cx, cy);
                } else {
                    out.emit(2, x, y);
                }
                previousOff = false;
            }
        }
        out.closeContour(startOff, previousOff, sx, sy, scx, scy, cx, cy);
        result.vertices = vertices;
        result.count = out.count;
        return result;
    }

    private static class Points {
        float[] xy;
        int count;

        void point(float x, float y) {
            if (xy != null) {
                xy[count * 2] = x;
                xy[count * 2 + 1] = y;
            }
            count = count + 1;
        }
    }

    private static void flattenQuadratic(Points out, float x0, float y0, float x1, float y1, float x2, float y2,
                                         float flatnessSquared, int level) {
        if (level > 16) {
            return;
        }
        float twiceX = (x1 + x1) + x0;
        float twiceY = (y1 + y1) + y0;
        level = level + 1;
        do {
            float middleX = (twiceX + x2) * 0.25f;
            float middleY = (twiceY + y2) * 0.25f;
            float dx = (x2 + x0) * 0.5f - middleX;
            float dy = (y2 + y0) * 0.5f - middleY;
            if (!(dx * dx + dy * dy > flatnessSquared)) {
                out.point(x2, y2);
                return;
            }
            flattenQuadratic(out, x0, y0, (x1 + x0) * 0.5f, (y0 + y1) * 0.5f, middleX, middleY, flatnessSquared, level);
            x1 = (x1 + x2) * 0.5f;
            y1 = (y1 + y2) * 0.5f;
            twiceX = (x1 + x1) + middleX;
            twiceY = (y1 + y1) + middleY;
            level = level + 1;
            x0 = middleX;
            y0 = middleY;
        } while (level != 18);
    }

    private static float distanceSquared(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return dx * dx + dy * dy;
    }

    private static void flattenCubic(Points out, float x0, float y0, float x1, float y1, float x2, float y2, float x3,
                                     float y3, float flatnessSquared, int level) {
        if (level > 16) {
            return;
        }
        do {
            level = level + 1;
            float chord = (float) Math.sqrt(distanceSquared(x0, y0, x3, y3));
            double first = Math.sqrt(distanceSquared(x0, y0, x1, y1));
            double second = Math.sqrt(distanceSquared(x1, y1, x2, y2));
            double last = Math.sqrt(distanceSquared(x2, y2, x3, y3));
            float length = (float) ((first + second) + last);
            if (!(length * length - chord * chord > flatnessSquared)) {
                out.point(x3, y3);
                return;
            }
            float middleX = (x1 + x2) * 0.5f;
            float middleY = (y1 + y2) * 0.5f;
            float firstX = (x0 + x1) * 0.5f;
            float firstY = (y0 + y1) * 0.5f;
            float lastX = (x3 + x2) * 0.5f;
            float lastY = (y3 + y2) * 0.5f;
            float leftX = (middleX + firstX) * 0.5f;
            float leftY = (middleY + firstY) * 0.5f;
            float rightX = (middleX + lastX) * 0.5f;
            float rightY = (middleY + lastY) * 0.5f;
            float centerX = (rightX + leftX) * 0.5f;
            float centerY = (rightY + leftY) * 0.5f;
            flattenCubic(out, x0, y0, firstX, firstY, leftX, leftY, centerX, centerY, flatnessSquared, level);
            x0 = centerX;
            y0 = centerY;
            x1 = rightX;
            y1 = rightY;
            x2 = lastX;
            y2 = lastY;
        } while (level != 17);
    }

    private static class Edge {
        float x0;
        float y0;
        float x1;
        float y1;
        int invert;

        void set(Edge o) {
            x0 = o.x0;
            y0 = o.y0;
            x1 = o.x1;
            y1 = o.y1;
            invert = o.invert;
        }
    }

    private static void swapEdge(Edge[] edges, int a, int b) {
        Edge saved = edges[a];
        edges[a] = edges[b];
        edges[b] = saved;
    }

    private static void partitionEdges(Edge[] edges, int base, int count) {
        while (count > 12) {
            int middle = count >>> 1;
            int last = count - 1;
            float firstY = edges[base].y0;
            float middleY = edges[base + middle].y0;
            float lastY = edges[base + last].y0;
            if ((!(firstY < middleY)) == (middleY < lastY)) {
                int chosen = (firstY < lastY) == (middleY < lastY) ? 0 : last;
                swapEdge(edges, base + chosen, base + middle);
            }
            swapEdge(edges, base, base + middle);
            int nextLeft = 1;
            int nextRight = last;
            int left;
            int right;
            for (; ; ) {
                float pivot = edges[base].y0;
                do {
                    left = nextLeft++;
                } while (pivot > edges[base + left].y0);
                do {
                    right = nextRight--;
                } while (edges[base + right].y0 > pivot);
                if (left >= right) {
                    break;
                }
                swapEdge(edges, base + left, base + right);
            }
            int rightCount = count - left;
            int leftCount = right;
            if (leftCount < rightCount) {
                partitionEdges(edges, base, leftCount);
                base += left;
                count = rightCount;
            } else {
                partitionEdges(edges, base + left, rightCount);
                count = leftCount;
            }
        }
    }

    private static class ActiveEdge {
        ActiveEdge next;
        float x;
        float dx;
        float dy;
        float direction;
        float startY;
        float endY;
    }

    private static void clippedEdge(float[] scan, int scanBase, int pixel, ActiveEdge edge, float x0, float y0, float x1,
                                    float y1) {
        if (y0 == y1 || y0 > edge.endY || edge.startY > y1) {
            return;
        }
        if (edge.startY > y0) {
            x0 = ((edge.startY - y0) * (x1 - x0)) / (y1 - y0) + x0;
            y0 = edge.startY;
        }
        if (y1 > edge.endY) {
            x1 = ((edge.endY - y1) * (x1 - x0)) / (y1 - y0) + x1;
            y1 = edge.endY;
        }
        float left = (float) pixel;
        float right = (float) (pixel + 1);
        int at = scanBase + pixel;
        if (x0 <= left && x1 <= left) {
            scan[at] = (y1 - y0) * edge.direction + scan[at];
        } else if (!(x0 >= right) || !(x1 >= right)) {
            float signedHeight = (y1 - y0) * edge.direction;
            float factor = ((x1 - left) + (x0 - left)) * -0.5f + 1.0f;
            scan[at] = factor * signedHeight + scan[at];
        }
    }

    private static void fillActiveEdges(float[] buffer, int scanBase, int fillBase, int width, ActiveEdge first,
                                        float y0, float y1) {
        float right = (float) width;
        for (ActiveEdge edge = first; edge != null; edge = edge.next) {
            float x0 = edge.x;
            float dx = edge.dx;
            if (dx == 0.0f) {
                if (x0 < right) {
                    if (x0 >= 0.0f) {
                        int x = Sse.cvttss2si(x0);
                        clippedEdge(buffer, scanBase, x, edge, x0, y0, x0, y1);
                        clippedEdge(buffer, fillBase, x + 1, edge, x0, y0, x0, y1);
                    } else {
                        clippedEdge(buffer, fillBase, 0, edge, x0, y0, x0, y1);
                    }
                }
                continue;
            }
            float x3 = dx + x0;
            float xTop = edge.startY > y0 ? (edge.startY - y0) * dx + x0 : x0;
            float xBottom = edge.endY < y1 ? (edge.endY - y0) * dx + x0 : x3;
            if (xTop >= 0.0f && xBottom >= 0.0f && xTop < right && xBottom < right) {
                float sy = Floats.max(edge.startY, y0);
                float ey = Floats.min(edge.endY, y1);
                int leftPixel = Sse.cvttss2si(xTop);
                int rightPixel = Sse.cvttss2si(xBottom);
                float direction = edge.direction;
                if (leftPixel == rightPixel) {
                    float x = (float) leftPixel;
                    float factor = ((xBottom - x) + (xTop - x)) * -0.5f + 1.0f;
                    buffer[scanBase + leftPixel] = (factor * direction) * (ey - sy) + buffer[scanBase + leftPixel];
                    buffer[fillBase + leftPixel + 1] = (ey - sy) * direction + buffer[fillBase + leftPixel + 1];
                    continue;
                }
                float inverse = edge.dy;
                float baseX = x0;
                if (xBottom < xTop) {
                    float reflectedEnd = (y0 - sy) + y1;
                    sy = (y0 - ey) + y1;
                    ey = reflectedEnd;
                    inverse = Floats.negate(inverse);
                    baseX = x3;
                    int temporary = leftPixel;
                    leftPixel = rightPixel;
                    rightPixel = temporary;
                    float temporaryX = xTop;
                    xTop = xBottom;
                    xBottom = temporaryX;
                }
                int next = leftPixel + 1;
                float crossing = ((float) next - baseX) * inverse + y0;
                float area = (crossing - sy) * direction;
                buffer[scanBase + leftPixel] = ((xTop - (float) leftPixel + 1.0f) * -0.5f + 1.0f) * area
                        + buffer[scanBase + leftPixel];
                if (rightPixel > next) {
                    float step = inverse * direction;
                    float halfStep = step * 0.5f;
                    for (int x = next; x < rightPixel; ++x) {
                        buffer[scanBase + x] = (halfStep + area) + buffer[scanBase + x];
                        area = area + step;
                    }
                }
                float lastCrossing = inverse * (float) (rightPixel - next) + crossing;
                float factor = ((xBottom - (float) rightPixel) + 0.0f) * -0.5f + 1.0f;
                buffer[scanBase + rightPixel] = ((ey - lastCrossing) * (factor * direction) + area)
                        + buffer[scanBase + rightPixel];
                buffer[fillBase + rightPixel + 1] = (ey - sy) * direction + buffer[fillBase + rightPixel + 1];
                continue;
            }
            for (int pixel = 0; pixel < width; ++pixel) {
                float left = (float) pixel;
                float rightPixel = (float) (pixel + 1);
                float atLeft = (left - x0) / dx + y0;
                float atRight = (rightPixel - x0) / dx + y0;
                if (x0 < left && x3 > rightPixel) {
                    clippedEdge(buffer, scanBase, pixel, edge, x0, y0, left, atLeft);
                    clippedEdge(buffer, scanBase, pixel, edge, left, atLeft, rightPixel, atRight);
                    clippedEdge(buffer, scanBase, pixel, edge, rightPixel, atRight, x3, y1);
                } else if (x3 < left && x0 > rightPixel) {
                    clippedEdge(buffer, scanBase, pixel, edge, x0, y0, rightPixel, atRight);
                    clippedEdge(buffer, scanBase, pixel, edge, rightPixel, atRight, left, atLeft);
                    clippedEdge(buffer, scanBase, pixel, edge, left, atLeft, x3, y1);
                } else if ((x0 < left && x3 > left) || (x3 < left && x0 > left)) {
                    clippedEdge(buffer, scanBase, pixel, edge, x0, y0, left, atLeft);
                    clippedEdge(buffer, scanBase, pixel, edge, left, atLeft, x3, y1);
                } else if ((x0 < rightPixel && x3 > rightPixel) || (x3 < rightPixel && x0 > rightPixel)) {
                    clippedEdge(buffer, scanBase, pixel, edge, x0, y0, rightPixel, atRight);
                    clippedEdge(buffer, scanBase, pixel, edge, rightPixel, atRight, x3, y1);
                } else {
                    clippedEdge(buffer, scanBase, pixel, edge, x0, y0, x3, y1);
                }
            }
        }
    }

    private void rasterizeShape(byte[] pixels, int pixelBase, int width, int height, int stride, float flatness,
                                Vertex[] vertices, int count, float scaleX, float scaleY, float shiftX, float shiftY,
                                int offsetX, int offsetY, int invert) {
        if (count < 1) {
            return;
        }
        int contours = 0;
        for (int i = 0; i < count; ++i) {
            if (vertices[i].type == 1) {
                ++contours;
            }
        }
        if (contours == 0) {
            return;
        }
        if (!owner.allocateScratch((contours * 4 + 15) & ~15, true)) {
            return;
        }
        int[] lengths = new int[contours];
        float objectFlatness = flatness / Floats.min(scaleY, scaleX);
        float flatnessSquared = objectFlatness * objectFlatness;
        Points points = new Points();
        int contourStart = 0;
        for (int pass = 0; pass < 2; ++pass) {
            if (pass != 0) {
                if (!owner.allocateScratch((points.count * 8 + 15) & ~15, true)) {
                    return;
                }
                points.xy = new float[points.count * 2];
            }
            points.count = 0;
            int contour = -1;
            float x = 0.0f;
            float y = 0.0f;
            for (int i = 0; i < count; ++i) {
                Vertex v = vertices[i];
                float vx = v.x;
                float vy = v.y;
                switch (v.type) {
                    case 1:
                        if (contour >= 0) {
                            lengths[contour] = points.count - contourStart;
                        }
                        ++contour;
                        contourStart = points.count;
                        points.point(vx, vy);
                        x = vx;
                        y = vy;
                        break;
                    case 2:
                        points.point(vx, vy);
                        x = vx;
                        y = vy;
                        break;
                    case 3:
                        flattenQuadratic(points, x, y, v.controlX, v.controlY, vx, vy, flatnessSquared, 0);
                        x = vx;
                        y = vy;
                        break;
                    case 4:
                        flattenCubic(points, x, y, v.controlX, v.controlY, v.control2X, v.control2Y, vx, vy,
                                flatnessSquared, 0);
                        x = vx;
                        y = vy;
                        break;
                    default:
                        break;
                }
            }
            lengths[contour] = points.count - contourStart;
        }
        int totalPoints = 0;
        for (int i = 0; i < contours; ++i) {
            totalPoints += lengths[i];
        }
        if (invert != 0) {
            scaleY = Floats.negate(scaleY);
        }
        if (!owner.allocateScratch((totalPoints * 20 + 35) & ~15, true)) {
            return;
        }
        Edge[] edges = new Edge[totalPoints + 1];
        for (int i = 0; i < edges.length; ++i) {
            edges[i] = new Edge();
        }
        float[] xy = points.xy;
        int edgeCount = 0;
        int pointStart = 0;
        for (int contour = 0; contour < contours; ++contour) {
            int length = lengths[contour];
            int previous = length - 1;
            for (int current = 0; current < length; ++current) {
                int prior = (pointStart + previous) * 2;
                int now = (pointStart + current) * 2;
                if (xy[prior + 1] != xy[now + 1]) {
                    int first = current;
                    int last = previous;
                    int direction = 0;
                    if ((invert == 0 && xy[prior + 1] < xy[now + 1]) || (invert != 0 && xy[now + 1] < xy[prior + 1])) {
                        first = previous;
                        last = current;
                        direction = 1;
                    }
                    int p = (pointStart + first) * 2;
                    int q = (pointStart + last) * 2;
                    Edge e = edges[edgeCount++];
                    e.x0 = xy[p] * scaleX + shiftX;
                    e.y0 = xy[p + 1] * scaleY + shiftY;
                    e.x1 = xy[q] * scaleX + shiftX;
                    e.y1 = xy[q + 1] * scaleY + shiftY;
                    e.invert = direction;
                }
                previous = current;
            }
            pointStart = pointStart + length;
        }
        partitionEdges(edges, 0, edgeCount);
        for (int i = 1; i < edgeCount; ++i) {
            Edge value = new Edge();
            value.set(edges[i]);
            int destination = i;
            while (destination != 0 && !(edges[destination - 1].y0 <= value.y0)) {
                edges[destination].set(edges[destination - 1]);
                --destination;
            }
            if (destination != i) {
                edges[destination].set(value);
            }
        }
        float[] buffer;
        if (width > 64) {
            owner.allocateScratch((width * 8 + 19) & ~15, false);
            buffer = new float[width * 2 + 1];
        } else {
            buffer = new float[129];
        }
        edges[edgeCount].y0 = (float) (offsetY + height) + 1.0f;
        if (height <= 0) {
            return;
        }
        int scanBase = 0;
        int fillBase = width;
        ActiveEdge active = null;
        ActiveEdge freeList = null;
        int available = 0;
        int edgeIndex = 0;
        int y = offsetY;
        float xOffset = (float) offsetX;
        for (int row = 0; row < height; ++row, y = y + 1) {
            float top = (float) y;
            float bottom = top + 1.0f;
            Arrays.fill(buffer, scanBase, scanBase + width, 0.0f);
            Arrays.fill(buffer, fillBase, fillBase + width + 1, 0.0f);
            ActiveEdge previousLink = null;
            ActiveEdge link = active;
            while (link != null) {
                ActiveEdge edge = link;
                if (edge.endY <= top) {
                    ActiveEdge following = edge.next;
                    if (previousLink == null) {
                        active = following;
                    } else {
                        previousLink.next = following;
                    }
                    edge.direction = 0.0f;
                    edge.next = freeList;
                    freeList = edge;
                    link = following;
                } else {
                    previousLink = edge;
                    link = edge.next;
                }
            }
            while (edges[edgeIndex].y0 <= bottom) {
                Edge source = edges[edgeIndex++];
                if (source.y0 == source.y1) {
                    continue;
                }
                ActiveEdge edge;
                if (freeList != null) {
                    edge = freeList;
                    freeList = freeList.next;
                } else {
                    if (available == 0) {
                        if (!owner.allocateScratch(0x6410, true)) {
                            available = 0;
                            freeList = null;
                            continue;
                        }
                        available = 800;
                    }
                    --available;
                    edge = new ActiveEdge();
                }
                float dx = (source.x1 - source.x0) / (source.y1 - source.y0);
                edge.dx = dx;
                edge.dy = dx != 0.0f ? 1.0f / dx : 0.0f;
                edge.x = (dx * (top - source.y0) + source.x0) - xOffset;
                edge.direction = source.invert != 0 ? 1.0f : -1.0f;
                edge.startY = source.y0;
                edge.endY = source.y1;
                edge.next = null;
                if (top > edge.endY && row == 0 && offsetY != 0) {
                    edge.endY = top;
                }
                edge.next = active;
                active = edge;
            }
            fillActiveEdges(buffer, scanBase, fillBase, width, active, top, bottom);
            float accumulated = 0.0f;
            for (int column = 0; column < width; ++column) {
                accumulated = accumulated + buffer[fillBase + column];
                float coverage = Floats.magnitude(buffer[scanBase + column] + accumulated);
                int value = Sse.cvttss2si(coverage * 255.0f + 0.5f);
                pixels[pixelBase + row * stride + column] = (byte) (value > 254 ? 255 : value);
            }
            for (ActiveEdge edge = active; edge != null; edge = edge.next) {
                edge.x = edge.dx + edge.x;
            }
        }
    }

    void rasterizeGlyph(byte[] output, int outputBase, int width, int height, int stride, float scaleX, float scaleY,
                        float shiftX, float shiftY, int glyph) {
        Shape shape = glyphShape(glyph);
        int offsetX = 0;
        int offsetY = 0;
        int[] box = new int[4];
        if (glyphBox(glyph, box)) {
            offsetX = Sse.cvttss2si(ScalarRounding.floorGameFloat((float) box[0] * scaleX + shiftX));
            offsetY = Sse.cvttss2si(ScalarRounding.floorGameFloat((float) (-box[3]) * scaleY + shiftY));
        }
        if (width != 0 && height != 0) {
            rasterizeShape(output, outputBase, width, height, stride, Float.intBitsToFloat(0x3eb33333), shape.vertices,
                    shape.count, scaleX, scaleY, shiftX, shiftY, offsetX, offsetY, 1);
        }
    }
}

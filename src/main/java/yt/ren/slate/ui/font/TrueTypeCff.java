package yt.ren.slate.ui.font;

import yt.ren.slate.util.math.Sse;

class TrueTypeCff {
    static class Buffer {
        int base;
        int cursor;
        int size;

        Buffer() {
        }

        Buffer(int base, int cursor, int size) {
            this.base = base;
            this.cursor = cursor;
            this.size = size;
        }

        Buffer copy() {
            return new Buffer(base, cursor, size);
        }
    }

    private static int at(byte[] data, int index) {
        return index >= 0 && index < data.length ? data[index] & 0xff : 0;
    }

    static int byteAt(byte[] data, Buffer b) {
        if (b.cursor >= b.size) {
            return 0;
        }
        int value = at(data, b.base + b.cursor);
        b.cursor = b.cursor + 1;
        return value;
    }

    static int integer(byte[] data, Buffer b, int bytes) {
        int value = 0;
        for (int i = 0; i < bytes; ++i) {
            value = (value << 8) | byteAt(data, b);
        }
        return value;
    }

    static void seek(Buffer b, int position) {
        b.cursor = position < 0 || position >= b.size ? b.size : position;
    }

    static Buffer range(Buffer b, int start, int count) {
        if ((start | count) < 0 || start > b.size || count > b.size - start) {
            return new Buffer();
        }
        return new Buffer(b.base + start, 0, count);
    }

    static Buffer index(byte[] data, Buffer b) {
        int start = b.cursor;
        int count = integer(data, b, 2);
        if (count != 0) {
            int width = byteAt(data, b);
            seek(b, b.cursor + count * width);
            int end = integer(data, b, width);
            seek(b, b.cursor + end - 1);
        }
        return range(b, start, b.cursor - start);
    }

    static Buffer indexItem(byte[] data, Buffer b, int index) {
        seek(b, 0);
        int count = integer(data, b, 2);
        int width = byteAt(data, b);
        seek(b, b.cursor + index * width);
        int first = integer(data, b, width);
        int last = integer(data, b, width);
        int start = width * (count + 1) + first + 2;
        return range(b, start, last - first);
    }

    static int cffInteger(byte[] data, Buffer b) {
        int value = byteAt(data, b);
        if (((value - 32) & 0xff) < 215) {
            return value - 139;
        }
        if (((value + 9) & 0xff) < 4) {
            return byteAt(data, b) + value * 256 - 0xf694;
        }
        if (((value + 5) & 0xff) < 4) {
            return 0xfa94 - ((value << 8) | byteAt(data, b));
        }
        if (value == 29) {
            return integer(data, b, 4);
        }
        if (value == 28) {
            return integer(data, b, 2);
        }
        return 0;
    }

    static void dictionaryValues(byte[] data, Buffer b, int key, int count, int[] output) {
        seek(b, 0);
        while (b.cursor < b.size) {
            int start = b.cursor;
            while (b.cursor < b.size && at(data, b.base + b.cursor) >= 28) {
                if (at(data, b.base + b.cursor) == 30) {
                    seek(b, b.cursor + 1);
                    while (b.cursor < b.size) {
                        int nibble = byteAt(data, b);
                        if ((nibble & 15) == 15 || (nibble & 0xf0) == 0xf0) {
                            break;
                        }
                    }
                } else {
                    cffInteger(data, b);
                }
            }
            int end = b.cursor;
            if (end >= b.size) {
                return;
            }
            int operation = byteAt(data, b);
            if (operation == 12) {
                operation = 0x100 | byteAt(data, b);
            }
            if (operation == key) {
                Buffer values = range(b, start, end - start);
                if (values.size < 1) {
                    return;
                }
                int i = 0;
                do {
                    output[i++] = cffInteger(data, values);
                } while (i < count && values.cursor < values.size);
                return;
            }
        }
    }

    static Buffer subroutines(byte[] data, Buffer cff, Buffer dictionary) {
        int[] privateValues = new int[2];
        dictionaryValues(data, dictionary, 18, 2, privateValues);
        if (privateValues[0] == 0 || privateValues[1] == 0) {
            return new Buffer();
        }
        Buffer privateDictionary = range(cff, privateValues[1], privateValues[0]);
        int[] subroutines = new int[1];
        dictionaryValues(data, privateDictionary, 19, 1, subroutines);
        if (subroutines[0] == 0) {
            return new Buffer();
        }
        seek(cff, privateValues[1] + subroutines[0]);
        return index(data, cff);
    }

    static boolean initialize(TrueType t, int cffOffset) {
        byte[] data = t.data;
        t.fdSelect = new Buffer();
        t.fontDictionaries = new Buffer();
        Buffer cff = new Buffer(cffOffset, 0, 0x20000000);
        t.cff = cff.copy();
        seek(cff, 2);
        int headerSize = byteAt(data, cff);
        seek(cff, headerSize);
        index(data, cff);
        Buffer topDictionaryIndex = index(data, cff);
        Buffer topDictionary = indexItem(data, topDictionaryIndex, 0);
        index(data, cff);
        t.globalSubroutines = index(data, cff);
        int[] charstrings = {0};
        int[] charstringType = {2};
        int[] fontDictionaries = {0};
        int[] fdSelect = {0};
        dictionaryValues(data, topDictionary, 17, 1, charstrings);
        dictionaryValues(data, topDictionary, 0x106, 1, charstringType);
        dictionaryValues(data, topDictionary, 0x124, 1, fontDictionaries);
        dictionaryValues(data, topDictionary, 0x125, 1, fdSelect);
        Buffer subroutineCff = cff.copy();
        t.localSubroutines = subroutines(data, subroutineCff, topDictionary);
        if (charstrings[0] == 0 || charstringType[0] != 2) {
            return false;
        }
        if (fontDictionaries[0] != 0) {
            if (fdSelect[0] == 0) {
                return false;
            }
            seek(cff, fontDictionaries[0]);
            t.fontDictionaries = index(data, cff);
            t.fdSelect = range(cff, fdSelect[0], cff.size - fdSelect[0]);
        }
        seek(cff, charstrings[0]);
        t.charstrings = index(data, cff);
        return true;
    }

    static class Context {
        int bounds;
        int started;
        float firstX;
        float firstY;
        float x;
        float y;
        int minimumX;
        int maximumX;
        int minimumY;
        int maximumY;
        TrueType.Vertex[] vertices;
        int count;
    }

    private static void boundPoint(Context c, int x, int y) {
        if (c.maximumX < x || c.started == 0) {
            c.maximumX = x;
        }
        if (c.maximumY < y || c.started == 0) {
            c.maximumY = y;
        }
        if (x < c.minimumX || c.started == 0) {
            c.minimumX = x;
        }
        if (y < c.minimumY || c.started == 0) {
            c.minimumY = y;
        }
        c.started = 1;
    }

    private static void emitPoint(Context c, int type, float x, float y) {
        int ix = Sse.cvttss2si(x);
        int iy = Sse.cvttss2si(y);
        if (c.bounds != 0) {
            boundPoint(c, ix, iy);
        } else {
            TrueType.Vertex vertex = c.vertices[c.count];
            vertex.type = type;
            vertex.x = (short) ix;
            vertex.y = (short) iy;
            vertex.controlX = 0;
            vertex.controlY = 0;
            vertex.control2X = 0;
            vertex.control2Y = 0;
        }
        c.count = c.count + 1;
    }

    private static void line(Context c, float dx, float dy) {
        c.x = c.x + dx;
        c.y = c.y + dy;
        emitPoint(c, 2, c.x, c.y);
    }

    private static int fontDictionary(byte[] data, Buffer source, int glyph) {
        Buffer selector = source.copy();
        seek(selector, 0);
        int format = byteAt(data, selector);
        if (format == 0) {
            seek(selector, selector.cursor + glyph);
            return byteAt(data, selector);
        }
        if (format == 3) {
            int ranges = integer(data, selector, 2);
            int first = integer(data, selector, 2);
            for (int i = 0; i < ranges; ++i) {
                int dictionary = byteAt(data, selector);
                int last = integer(data, selector, 2);
                if (glyph < last && first <= glyph) {
                    return dictionary;
                }
                first = last;
            }
        }
        return -1;
    }

    static void close(Context c) {
        if (c.firstX == c.x && c.firstY == c.y) {
            return;
        }
        emitPoint(c, 2, c.firstX, c.firstY);
    }

    static void move(Context c, float dx, float dy) {
        close(c);
        float x = dx + c.x;
        float y = dy + c.y;
        c.firstX = c.x = x;
        c.firstY = c.y = y;
        emitPoint(c, 1, x, y);
    }

    static void curve(Context c, float dx1, float dy1, float dx2, float dy2, float dx3, float dy3) {
        float controlX = dx1 + c.x;
        float controlY = dy1 + c.y;
        float control2X = dx2 + controlX;
        float control2Y = dy2 + controlY;
        c.x = dx3 + control2X;
        c.y = dy3 + control2Y;
        int x = Sse.cvttss2si(c.x);
        int y = Sse.cvttss2si(c.y);
        int cx = Sse.cvttss2si(controlX);
        int cy = Sse.cvttss2si(controlY);
        int cx2 = Sse.cvttss2si(control2X);
        int cy2 = Sse.cvttss2si(control2Y);
        if (c.bounds != 0) {
            boundPoint(c, x, y);
            boundPoint(c, cx, cy);
            boundPoint(c, cx2, cy2);
        } else {
            TrueType.Vertex vertex = c.vertices[c.count];
            vertex.type = 4;
            vertex.x = (short) x;
            vertex.y = (short) y;
            vertex.controlX = (short) cx;
            vertex.controlY = (short) cy;
            vertex.control2X = (short) cx2;
            vertex.control2Y = (short) cy2;
        }
        c.count = c.count + 1;
    }

    private static float negative(float a) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(a) ^ 0x80000000);
    }

    private static float absolute(float a) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(a) & 0x7fffffff);
    }

    static boolean run(TrueType t, int glyph, Context context) {
        byte[] data = t.data;
        Buffer localSubroutines = t.localSubroutines.copy();
        Buffer characters = t.charstrings.copy();
        Buffer program = indexItem(data, characters, glyph);
        float[] stack = new float[48];
        int count = 0;
        Buffer[] returns = new Buffer[10];
        int depth = 0;
        int maskBits = 0;
        boolean inHeader = true;
        boolean localSelected = false;
        while (program.cursor < program.size) {
            int operationPosition = program.cursor;
            int operation = byteAt(data, program);
            switch (operation) {
                case 1:
                case 3:
                case 18:
                case 23:
                    maskBits += count / 2;
                    break;
                case 4:
                    if (count < 1) {
                        return false;
                    }
                    move(context, 0.0f, stack[count - 1]);
                    inHeader = false;
                    break;
                case 5:
                    if (count < 2) {
                        return false;
                    }
                    for (int i = 0; i + 1 < count; i += 2) {
                        line(context, stack[i], stack[i + 1]);
                    }
                    break;
                case 6:
                case 7: {
                    if (count < 1) {
                        return false;
                    }
                    boolean horizontal = operation == 6;
                    for (int i = 0; i < count; ++i, horizontal = !horizontal) {
                        if (horizontal) {
                            context.x = stack[i] + context.x;
                            context.y = 0.0f + context.y;
                        } else {
                            context.x = 0.0f + context.x;
                            context.y = stack[i] + context.y;
                        }
                        emitPoint(context, 2, context.x, context.y);
                    }
                    break;
                }
                case 8:
                    if (count < 6) {
                        return false;
                    }
                    for (int i = 0; i + 5 < count; i += 6) {
                        curve(context, stack[i], stack[i + 1], stack[i + 2], stack[i + 3], stack[i + 4], stack[i + 5]);
                    }
                    break;
                case 10:
                case 29: {
                    boolean selected = localSelected;
                    if (operation == 10) {
                        selected = true;
                        if (!localSelected) {
                            Buffer selector = t.fdSelect;
                            if (selector.size != 0) {
                                int selectedDictionary = fontDictionary(data, selector, glyph);
                                Buffer dictionaries = t.fontDictionaries.copy();
                                Buffer dictionary = indexItem(data, dictionaries, selectedDictionary);
                                Buffer cff = t.cff.copy();
                                localSubroutines = subroutines(data, cff, dictionary);
                            }
                        }
                    }
                    if (count < 1 || depth > 9) {
                        return false;
                    }
                    float operand = stack[--count];
                    returns[depth] = program;
                    Buffer index = operation == 10 ? localSubroutines.copy() : t.globalSubroutines.copy();
                    seek(index, 0);
                    int entries = integer(data, index, 2);
                    int bias = Integer.compareUnsigned(entries, 33900) > 0 ? 32768
                            : (Integer.compareUnsigned(entries, 1239) > 0 ? 1131 : 107);
                    int target = bias + Sse.cvttss2si(operand);
                    program = Integer.compareUnsigned(target, entries) < 0 ? indexItem(data, index, target) : new Buffer();
                    if (program.size == 0) {
                        return false;
                    }
                    ++depth;
                    program.cursor = 0;
                    localSelected = selected;
                    continue;
                }
                case 11:
                    if (depth < 1) {
                        return false;
                    }
                    program = returns[--depth];
                    continue;
                case 12: {
                    if (program.cursor >= program.size) {
                        return false;
                    }
                    int escaped = byteAt(data, program);
                    if (escaped == 34) {
                        if (count < 7) {
                            return false;
                        }
                        curve(context, stack[0], 0.0f, stack[1], stack[2], stack[3], 0.0f);
                        curve(context, stack[4], 0.0f, stack[5], negative(stack[2]), stack[6], 0.0f);
                    } else if (escaped == 35) {
                        if (count < 13) {
                            return false;
                        }
                        curve(context, stack[0], stack[1], stack[2], stack[3], stack[4], stack[5]);
                        curve(context, stack[6], stack[7], stack[8], stack[9], stack[10], stack[11]);
                    } else if (escaped == 36) {
                        if (count < 9) {
                            return false;
                        }
                        curve(context, stack[0], stack[1], stack[2], stack[3], stack[4], 0.0f);
                        float dy = negative((stack[1] + stack[3]) + stack[7]);
                        curve(context, stack[5], 0.0f, stack[6], stack[7], stack[8], dy);
                    } else if (escaped == 37) {
                        if (count < 11) {
                            return false;
                        }
                        float dx = (((stack[0] + stack[2]) + stack[4]) + stack[6]) + stack[8];
                        float dy = (((stack[1] + stack[3]) + stack[5]) + stack[7]) + stack[9];
                        float lastX = absolute(dx) > absolute(dy) ? stack[10] : negative(dx);
                        float lastY = absolute(dy) < absolute(dx) ? negative(dy) : stack[10];
                        curve(context, stack[0], stack[1], stack[2], stack[3], stack[4], stack[5]);
                        curve(context, stack[6], stack[7], stack[8], stack[9], lastX, lastY);
                    } else {
                        return false;
                    }
                    break;
                }
                case 14:
                    close(context);
                    return true;
                case 19:
                case 20: {
                    if (inHeader) {
                        maskBits += count / 2;
                    }
                    int bytes = (maskBits + 7) / 8;
                    seek(program, program.cursor + bytes);
                    inHeader = false;
                    break;
                }
                case 21:
                    if (count < 2) {
                        return false;
                    }
                    move(context, stack[count - 2], stack[count - 1]);
                    inHeader = false;
                    break;
                case 22:
                    if (count < 1) {
                        return false;
                    }
                    move(context, stack[count - 1], 0.0f);
                    inHeader = false;
                    break;
                case 24: {
                    if (count < 8) {
                        return false;
                    }
                    int i = 0;
                    do {
                        curve(context, stack[i], stack[i + 1], stack[i + 2], stack[i + 3], stack[i + 4], stack[i + 5]);
                        i += 6;
                    } while (i < count - 7);
                    if (count <= i + 1) {
                        return false;
                    }
                    line(context, stack[i], stack[i + 1]);
                    break;
                }
                case 25: {
                    if (count < 8) {
                        return false;
                    }
                    int i = 0;
                    do {
                        line(context, stack[i], stack[i + 1]);
                        i += 2;
                    } while (i + 1 < count - 6);
                    if (count <= i + 5) {
                        return false;
                    }
                    curve(context, stack[i], stack[i + 1], stack[i + 2], stack[i + 3], stack[i + 4], stack[i + 5]);
                    break;
                }
                case 26:
                case 27: {
                    if (count < 4) {
                        return false;
                    }
                    int i = count & 1;
                    float extra = (count & 1) != 0 ? stack[0] : 0.0f;
                    if (i + 3 < count) {
                        if (operation == 27) {
                            curve(context, stack[i], extra, stack[i + 1], stack[i + 2], stack[i + 3], 0.0f);
                        } else {
                            curve(context, extra, stack[i], stack[i + 1], stack[i + 2], 0.0f, stack[i + 3]);
                        }
                        for (i += 4; i + 3 < count; i += 4) {
                            if (operation == 27) {
                                curve(context, stack[i], 0.0f, stack[i + 1], stack[i + 2], stack[i + 3], 0.0f);
                            } else {
                                curve(context, 0.0f, stack[i], stack[i + 1], stack[i + 2], 0.0f, stack[i + 3]);
                            }
                        }
                    }
                    break;
                }
                case 30:
                case 31: {
                    if (count < 4) {
                        return false;
                    }
                    boolean vertical = operation == 30;
                    for (int i = 0; i + 3 < count; i += 4, vertical = !vertical) {
                        float extra = count - i == 5 ? stack[i + 4] : 0.0f;
                        if (vertical) {
                            curve(context, 0.0f, stack[i], stack[i + 1], stack[i + 2], stack[i + 3], extra);
                        } else {
                            curve(context, stack[i], 0.0f, stack[i + 1], stack[i + 2], extra, stack[i + 3]);
                        }
                    }
                    break;
                }
                default: {
                    if (operation < 32 && operation != 28) {
                        return false;
                    }
                    float value;
                    if (operation == 255) {
                        value = (float) integer(data, program, 4) * Float.intBitsToFloat(0x37800000);
                    } else {
                        seek(program, operationPosition);
                        value = (short) cffInteger(data, program);
                    }
                    if (count > 47) {
                        return false;
                    }
                    stack[count++] = value;
                    continue;
                }
            }
            count = 0;
        }
        return false;
    }
}

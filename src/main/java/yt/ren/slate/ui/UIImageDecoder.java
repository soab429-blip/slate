package yt.ren.slate.ui;

import java.util.Arrays;

class UIImageDecoder {
    static String failure;
    static int flipVertically;
    static int unpremultiply;
    static int convertIphone;
    private static final byte[] FIXED_DISTANCES = new byte[32];
    private static final byte[] FIXED_LENGTHS = new byte[288];
    private static final char[] UNKNOWN_PNG_CHUNK = "XXXX PNG chunk not known".toCharArray();

    private static final int[] CODE_ORDER = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
    private static final int[] LENGTH_BASE = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59,
            67, 83, 99, 115, 131, 163, 195, 227, 258, 0, 0};
    private static final int[] LENGTH_EXTRA = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4,
            5, 5, 5, 5, 0, 0, 0};
    private static final int[] DISTANCE_BASE = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385,
            513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577, 0, 0};
    private static final int[] DISTANCE_EXTRA = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10,
            10, 11, 11, 12, 12, 13, 13, 0, 0};
    private static final int[] FIRST_ROW_FILTER = {0, 1, 0, 5, 6};
    private static final int[] DEPTH_SCALE = {0, 255, 85, 0, 17, 0, 0, 0, 1};
    private static final int[] PNG_SIGNATURE = {137, 80, 78, 71, 13, 10, 26, 10};
    private static final int[] ADAM7_X_ORIGIN = {0, 4, 0, 2, 0, 1, 0};
    private static final int[] ADAM7_Y_ORIGIN = {0, 0, 4, 0, 2, 0, 1};
    private static final int[] ADAM7_X_STEP = {8, 8, 4, 4, 2, 2, 1};
    private static final int[] ADAM7_Y_STEP = {8, 8, 8, 4, 4, 2, 2};

    private static boolean fail(String message) {
        failure = message;
        return false;
    }

    private static boolean below(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0;
    }

    static class Input {
        int width;
        int height;
        int components;
        int outputComponents;
        final byte[] data;
        int current;
        int end;
        final int originalEnd;

        Input(byte[] data) {
            this.data = data;
            this.current = 0;
            this.end = data.length;
            this.originalEnd = data.length;
        }

        int readByte() {
            if (current < end) {
                return data[current++] & 0xff;
            }
            return 0;
        }

        int readBig32() {
            int first = readByte();
            int second = readByte();
            int third = readByte();
            int fourth = readByte();
            return (first << 24) | (second << 16) | (third << 8) | fourth;
        }

        void skip(int bytes) {
            if (bytes < 0) {
                current = end;
                return;
            }
            current += bytes;
        }

        boolean read(byte[] destination, int offset, int bytes) {
            if (end - current < bytes) {
                return false;
            }
            if (bytes != 0) {
                System.arraycopy(data, current, destination, offset, bytes);
            }
            current += bytes;
            return true;
        }
    }

    static class Png {
        final Input input;
        byte[] compressed;
        byte[] expanded;
        byte[] pixels;

        Png(Input input) {
            this.input = input;
        }
    }

    static class Huffman {
        final char[] fast = new char[512];
        final char[] firstCode = new char[16];
        final int[] maxCode = new int[17];
        final char[] firstSymbol = new char[16];
        final byte[] size = new byte[288];
        final char[] value = new char[288];
    }

    private static class Inflate {
        byte[] in;
        int input;
        int inputEnd;
        int numBits;
        int codeBuffer;
        byte[] out;
        int output;
        int outputEnd;
        int expandable;
        final Huffman lengths = new Huffman();
        final Huffman distances = new Huffman();
    }

    private static int reverse16(int value) {
        value = ((value & 0xff) << 8) | ((value >>> 8) & 0xff);
        value = ((value & 0xf0f0) >>> 4) | ((value & 0x0f0f) << 4);
        value = ((value >>> 2) & 0x3333) + ((value & 0x3333) << 2);
        return ((value >>> 1) & 0x5555) + ((value & 0x5555) << 1);
    }

    private static int nextByte(Inflate c) {
        return c.input < c.inputEnd ? c.in[c.input++] & 0xff : 0;
    }

    private static void fillBits(Inflate c) {
        do {
            c.codeBuffer |= nextByte(c) << (c.numBits & 31);
            c.numBits += 8;
        } while (c.numBits <= 24);
    }

    private static int receive(Inflate c, int bits) {
        if (c.numBits < bits) {
            fillBits(c);
        }
        int value = c.codeBuffer & ((1 << bits) - 1);
        c.codeBuffer >>>= bits;
        c.numBits -= bits;
        return value;
    }

    private static int decode(Inflate c, Huffman table) {
        if (c.numBits < 16) {
            fillBits(c);
        }
        int fast = table.fast[c.codeBuffer & 511];
        if (fast != 0) {
            int size = fast >>> 9;
            c.codeBuffer >>>= size;
            c.numBits -= size;
            return fast & 511;
        }
        int reversed = reverse16(c.codeBuffer);
        int size = 10;
        while (Integer.compareUnsigned(table.maxCode[size], reversed) <= 0) {
            ++size;
        }
        if (size == 16) {
            return -1;
        }
        int index = (reversed >>> (16 - size)) - table.firstCode[size] + table.firstSymbol[size];
        c.codeBuffer >>>= size;
        c.numBits -= size;
        return table.value[index];
    }

    private static boolean expand(Inflate c, int cursor, int additional) {
        c.output = cursor;
        if (c.expandable == 0) {
            return fail("output buffer limit");
        }
        int used = cursor;
        int capacity = c.outputEnd;
        int required = used + additional;
        while (capacity < required) {
            capacity *= 2;
        }
        if (capacity < 0) {
            return fail("outofmem");
        }
        c.out = Arrays.copyOf(c.out, capacity);
        c.output = cursor;
        c.outputEnd = capacity;
        return true;
    }

    private static boolean uncompressed(Inflate c) {
        if ((c.numBits & 7) != 0) {
            receive(c, c.numBits & 7);
        }
        int[] header = new int[4];
        int position = 0;
        while (c.numBits > 0) {
            header[position++] = c.codeBuffer & 0xff;
            c.codeBuffer >>>= 8;
            c.numBits -= 8;
        }
        while (position < 4) {
            header[position++] = nextByte(c);
        }
        int count = header[0] | (header[1] << 8);
        int complement = header[2] | (header[3] << 8);
        if ((count ^ complement) != 65535) {
            return fail("zlib corrupt");
        }
        if (c.inputEnd - c.input < count) {
            return fail("read past buffer");
        }
        int cursor = c.output;
        if (c.outputEnd - cursor < count && !expand(c, cursor, count)) {
            return false;
        }
        System.arraycopy(c.in, c.input, c.out, cursor, count);
        c.input += count;
        c.output = cursor + count;
        return true;
    }

    private static boolean dynamicTables(Inflate c) {
        int literalCount = receive(c, 5) + 257;
        int distanceCount = receive(c, 5) + 1;
        int codeCount = receive(c, 4) + 4;
        byte[] codes = new byte[19];
        for (int i = 0; i < codeCount; ++i) {
            codes[CODE_ORDER[i]] = (byte) receive(c, 3);
        }
        Huffman codeTable = new Huffman();
        if (!buildHuffman(codeTable, codes, 0, 19)) {
            return false;
        }

        byte[] lengths = new byte[1 + 464];
        int total = literalCount + distanceCount;
        int position = 0;
        do {
            int code = decode(c, codeTable);
            if (code < 0 || code > 18) {
                return fail("bad codelengths");
            }
            if (code < 16) {
                lengths[1 + position++] = (byte) code;
            } else {
                byte value = 0;
                int repeat;
                if (code == 16) {
                    repeat = receive(c, 2) + 3;
                    value = lengths[1 + position - 1];
                } else if (code == 17) {
                    repeat = receive(c, 3) + 3;
                } else {
                    repeat = receive(c, 7) + 11;
                }
                Arrays.fill(lengths, 1 + position, 1 + position + repeat, value);
                position += repeat;
            }
        } while (position < total);
        if (position != total) {
            return fail("bad codelengths");
        }
        return buildHuffman(c.lengths, lengths, 1, literalCount)
                && buildHuffman(c.distances, lengths, 1 + literalCount, distanceCount);
    }

    private static boolean compressed(Inflate c) {
        int cursor = c.output;
        for (; ; ) {
            int symbol = decode(c, c.lengths);
            if (symbol < 0) {
                return fail("bad huffman code");
            }
            if (symbol < 256) {
                if (c.outputEnd <= cursor && !expand(c, cursor, 1)) {
                    return false;
                }
                c.out[cursor++] = (byte) symbol;
                continue;
            }
            if (symbol == 256) {
                c.output = cursor;
                return true;
            }
            int index = symbol - 257;
            int count = LENGTH_BASE[index];

            if (index >= 8 && index < 28) {
                count += receive(c, LENGTH_EXTRA[index]);
            }
            int distanceSymbol = decode(c, c.distances);
            if (distanceSymbol < 0) {
                return fail("bad huffman code");
            }
            int distance = DISTANCE_BASE[distanceSymbol];
            if (distanceSymbol >= 4 && distanceSymbol < 30) {
                distance += receive(c, DISTANCE_EXTRA[distanceSymbol]);
            }
            if (cursor < distance) {
                return fail("bad dist");
            }
            if (c.outputEnd - cursor < count && !expand(c, cursor, count)) {
                return false;
            }
            int source = cursor - distance;
            if (distance == 1) {
                if (count != 0) {
                    Arrays.fill(c.out, cursor, cursor + count, c.out[source]);
                }
                cursor += count;
            } else {
                for (int i = 0; i < count; ++i) {
                    c.out[cursor++] = c.out[source++];
                }
            }
        }
    }

    static boolean buildHuffman(Huffman table, byte[] sizes, int offset, int count) {
        int[] counts = new int[17];
        int[] nextCode = new int[16];
        Arrays.fill(table.fast, (char) 0);
        for (int i = 0; i < count; ++i) {
            ++counts[sizes[offset + i] & 0xff];
        }
        for (int i = 1; i < 16; ++i) {
            if (counts[i] > (1 << i)) {
                return fail("bad sizes");
            }
        }
        int code = 0;
        int total = 0;
        for (int bits = 1; bits < 16; ++bits) {
            nextCode[bits] = code;
            table.firstCode[bits] = (char) code;
            table.firstSymbol[bits] = (char) total;
            code += counts[bits];
            if (counts[bits] != 0 && below(1 << bits, code)) {
                return fail("bad codelengths");
            }
            table.maxCode[bits] = code << (16 - bits);
            code *= 2;
            total += counts[bits];
        }
        table.maxCode[16] = 65536;
        for (int symbol = 0; symbol < count; ++symbol) {
            int bits = sizes[offset + symbol] & 0xff;
            if (bits == 0) {
                continue;
            }
            int current = nextCode[bits];
            int index = current - table.firstCode[bits] + table.firstSymbol[bits];
            table.size[index] = (byte) bits;
            table.value[index] = (char) symbol;
            if (bits < 10) {
                for (int reversed = reverse16(current) >>> (16 - bits); reversed < 512; reversed += 1 << bits) {
                    table.fast[reversed] = (char) (symbol | (bits << 9));
                }
            }
            nextCode[bits] = current + 1;
        }
        return true;
    }

    private static boolean inflate(Inflate c, byte[] output, int capacity, int expandable, int parseHeader) {
        c.out = output;
        c.output = 0;
        c.outputEnd = capacity;
        c.expandable = expandable;
        if (parseHeader != 0) {
            int method = nextByte(c);
            int flags = nextByte(c);
            if ((((method << 8) | flags) * 0x7bdf & 0xffff) >= 0x843) {
                return fail("bad zlib header");
            }
            if ((flags & 32) != 0) {
                return fail("no preset dict");
            }
            if ((method & 15) != 8) {
                return fail("bad compression");
            }
        }
        c.numBits = 0;
        c.codeBuffer = 0;
        for (; ; ) {
            int last = receive(c, 1);
            int type = receive(c, 2);
            if (type == 0) {
                if (!uncompressed(c)) {
                    return false;
                }
            } else {
                if (type == 3) {
                    return false;
                }
                if (type == 1) {
                    if (FIXED_DISTANCES[31] == 0) {
                        for (int i = 0; i < 288; ++i) {
                            FIXED_LENGTHS[i] = (byte) (i < 144 ? 8 : i < 256 ? 9 : i < 280 ? 7 : 8);
                        }
                        Arrays.fill(FIXED_DISTANCES, (byte) 5);
                    }
                    if (!buildHuffman(c.lengths, FIXED_LENGTHS, 0, 288)
                            || !buildHuffman(c.distances, FIXED_DISTANCES, 0, 32)) {
                        return false;
                    }
                } else if (!dynamicTables(c)) {
                    return false;
                }
                if (!compressed(c)) {
                    return false;
                }
            }
            if (last != 0) {
                return true;
            }
        }
    }

    static byte[] decodeZlibHeader(byte[] input, int offset, int size, int capacity, int[] outputSize,
                                   int parseHeader) {
        if (capacity < 0) {
            return null;
        }
        Inflate c = new Inflate();
        c.in = input;
        c.input = offset;
        c.inputEnd = offset + size;
        if (!inflate(c, new byte[capacity], capacity, 1, parseHeader)) {
            return null;
        }
        if (outputSize != null) {
            outputSize[0] = c.output;
        }
        return c.out;
    }

    private static int paeth(int left, int above, int upperLeft) {
        int leftDistance = Math.abs(above - upperLeft);
        int aboveDistance = Math.abs(left - upperLeft);
        int diagonalDistance = Math.abs(left + above - 2 * upperLeft);
        if (leftDistance <= aboveDistance && leftDistance <= diagonalDistance) {
            return left;
        }
        return aboveDistance <= diagonalDistance ? above : upperLeft;
    }

    private static byte filtered(int filter, int source, byte[] p, int output, int previous, int distance) {
        int predictor = 0;
        switch (filter) {
            case 1:
            case 6:
                predictor = p[output - distance] & 0xff;
                break;
            case 2:
                predictor = p[previous] & 0xff;
                break;
            case 3:
                predictor = ((p[output - distance] & 0xff) + (p[previous] & 0xff)) >>> 1;
                break;
            case 4:
                predictor = paeth(p[output - distance] & 0xff, p[previous] & 0xff, p[previous - distance] & 0xff);
                break;
            case 5:
                predictor = (p[output - distance] & 0xff) >>> 1;
                break;
            default:
                break;
        }
        return (byte) (source + predictor);
    }

    static boolean reconstructRows(Png png, byte[] rawData, int raw, int rawLength, int outputComponents, int width,
                                   int height, int depth, int colorType) {
        int components = png.input.components;
        int stride = width * outputComponents;
        long allocation = Integer.toUnsignedLong(stride * height);
        if (allocation > Integer.MAX_VALUE - 8) {
            failure = "outofmem";
            return false;
        }
        png.pixels = new byte[(int) allocation];
        byte[] p = png.pixels;
        int samples = components * width;
        int packedBytes = (samples * depth + 7) >>> 3;
        int needed = (packedBytes + 1) * height;
        if (png.input.width == width && png.input.height == height ? rawLength != needed : below(rawLength, needed)) {
            failure = "not enough pixels";
            return false;
        }
        if (height == 0) {
            return true;
        }
        int packedOffset = depth < 8 ? stride - packedBytes : 0;
        int firstCount = depth < 8 ? 1 : components;
        int effectiveWidth = depth < 8 ? packedBytes : width;
        int remaining = (effectiveWidth - 1) * components;
        for (int y = 0; Integer.compareUnsigned(y, height) < 0; ++y) {
            int filter = rawData[raw++] & 0xff;
            if (filter > 4) {
                failure = "invalid filter";
                return false;
            }
            if (y == 0) {
                filter = FIRST_ROW_FILTER[filter];
            }
            int row = y * stride;
            int destination = row + packedOffset;
            int previous = row - stride;
            for (int k = 0; k < firstCount; ++k) {
                int predictor = 0;
                if (filter == 2 || filter == 4) {
                    predictor = p[previous + k] & 0xff;
                } else if (filter == 3) {
                    predictor = (p[previous + k] & 0xff) >>> 1;
                }
                p[destination + k] = (byte) ((rawData[raw + k] & 0xff) + predictor);
            }
            raw += firstCount;
            if (depth == 8) {
                if (components != outputComponents) {
                    p[row + components] = (byte) 255;
                }
                destination = row + outputComponents;
                previous += outputComponents;
            } else {
                destination = row + packedOffset + 1;
                previous += 1;
            }
            if (components != outputComponents && depth >= 8) {
                for (int x = 1; Integer.compareUnsigned(x, width) < 0; ++x) {
                    for (int k = 0; k < components; ++k) {
                        p[destination + k] = filtered(filter, rawData[raw + k] & 0xff, p, destination + k,
                                previous + k, outputComponents);
                    }
                    p[destination + components] = (byte) 255;
                    raw += components;
                    destination += outputComponents;
                    previous += outputComponents;
                }
            } else {
                if (filter == 0) {
                    System.arraycopy(rawData, raw, p, destination, remaining);
                } else {
                    for (int k = 0; k < remaining; ++k) {
                        p[destination + k] = filtered(filter, rawData[raw + k] & 0xff, p, destination + k,
                                previous + k, firstCount);
                    }
                }
                raw += remaining;
            }
        }
        if (depth < 8) {
            for (int y = 0; Integer.compareUnsigned(y, height) < 0; ++y) {
                int row = stride * y;
                int packed = row + stride - packedBytes;
                int scale = colorType == 0 ? DEPTH_SCALE[depth] : 1;
                if (depth == 1 || depth == 2 || depth == 4) {
                    int mask = (1 << depth) - 1;
                    for (int i = 0; Integer.compareUnsigned(i, samples) < 0; ++i) {
                        int bit = i * depth;
                        p[row + i] = (byte) ((((p[packed + (bit >>> 3)] & 0xff) >>> (8 - depth - (bit & 7))) & mask)
                                * scale);
                    }
                }
                if (components != outputComponents) {
                    if (components == 1) {
                        for (int x = width - 1; x >= 0; --x) {
                            p[row + x * 2 + 1] = (byte) 255;
                            p[row + x * 2] = p[row + x];
                        }
                    } else {
                        for (int x = width - 1; x >= 0; --x) {
                            p[row + x * 4 + 3] = (byte) 255;
                            p[row + x * 4 + 2] = p[row + x * 3 + 2];
                            p[row + x * 4 + 1] = p[row + x * 3 + 1];
                            p[row + x * 4] = p[row + x * 3];
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean assembleRows(Png png, int rawLength, int depth, int colorType, int interlace) {
        Input input = png.input;
        byte[] rawData = png.expanded;
        int raw = 0;
        int channels = input.outputComponents;
        if (interlace == 0) {
            return reconstructRows(png, rawData, raw, rawLength, channels, input.width, input.height, depth, colorType);
        }
        byte[] result = new byte[input.width * channels * input.height];
        for (int pass = 0; pass < 7; ++pass) {
            int xOrigin = ADAM7_X_ORIGIN[pass];
            int yOrigin = ADAM7_Y_ORIGIN[pass];
            int xStep = ADAM7_X_STEP[pass];
            int yStep = ADAM7_Y_STEP[pass];
            int xNumerator = input.width - xOrigin + xStep - 1;
            int yNumerator = input.height - yOrigin + yStep - 1;
            int width = Integer.divideUnsigned(xNumerator, xStep);
            int height = Integer.divideUnsigned(yNumerator, yStep);
            if (below(xNumerator, xStep) || below(yNumerator, yStep)) {
                continue;
            }
            int packedBits = width * depth * input.components;
            if (!reconstructRows(png, rawData, raw, rawLength, channels, width, height, depth, colorType)) {
                return false;
            }
            for (int y = 0; below(y, height); ++y) {
                for (int x = 0; below(x, width); ++x) {
                    int destinationRow = (y * yStep + yOrigin) * channels * input.width;
                    int destinationX = (x * xStep + xOrigin) * channels;
                    int source = (y * width + x) * channels;
                    System.arraycopy(png.pixels, source, result, destinationRow + destinationX, channels);
                }
            }
            int consumed = (((packedBits + 7) >>> 3) + 1) * height;
            raw += consumed;
            rawLength -= consumed;
        }
        png.pixels = result;
        return true;
    }

    private static boolean finishPng(Png png, int compressedLength, int depth, int colorType, int interlace,
                                     boolean iphone, boolean hasTransparency, byte[] transparent, byte[] palette,
                                     int paletteComponents, int requested) {
        Input input = png.input;
        if (png.compressed == null) {
            return fail("no IDAT");
        }
        int guess = input.components * input.height * ((input.width * depth + 7) >>> 3) + input.height;
        int[] rawLength = new int[1];
        png.expanded = decodeZlibHeader(png.compressed, 0, compressedLength, guess, rawLength, iphone ? 0 : 1);
        if (png.expanded == null) {
            return false;
        }
        png.compressed = null;
        int extra = input.components + 1;
        int output = hasTransparency ? extra : input.components;
        if (requested != 3 && paletteComponents == 0 && requested == extra) {
            output = extra;
        }
        input.outputComponents = output;
        if (!assembleRows(png, rawLength[0], depth, colorType, interlace)) {
            return false;
        }
        int count = input.width * input.height;
        if (hasTransparency) {
            byte[] p = png.pixels;
            if (input.outputComponents == 2) {
                for (int i = 0, pixel = 0; below(i, count); ++i, pixel += 2) {
                    p[pixel + 1] = (byte) (p[pixel] != transparent[0] ? 255 : 0);
                }
            } else {
                for (int i = 0, pixel = 0; below(i, count); ++i, pixel += 4) {
                    if (p[pixel] == transparent[0] && p[pixel + 1] == transparent[1] && p[pixel + 2] == transparent[2]) {
                        p[pixel + 3] = 0;
                    }
                }
            }
        }
        if (iphone && convertIphone != 0 && input.outputComponents > 2) {
            convertIphonePixels(png);
        }
        if (paletteComponents != 0) {
            input.components = paletteComponents;
            int target = Integer.compareUnsigned(requested, 2) > 0 ? requested : paletteComponents;
            input.outputComponents = target;
            byte[] expanded = new byte[count * target];
            int channels = target == 3 ? 3 : 4;
            for (int i = 0; below(i, count); ++i) {
                System.arraycopy(palette, (png.pixels[i] & 0xff) * 4, expanded, i * channels, channels);
            }
            png.pixels = expanded;
        }
        png.expanded = null;
        return true;
    }

    static void convertIphonePixels(Png png) {
        int count = png.input.width * png.input.height;
        byte[] p = png.pixels;
        if (png.input.outputComponents == 3) {
            for (int i = 0, pixel = 0; below(i, count); ++i, pixel += 3) {
                byte blue = p[pixel];
                p[pixel] = p[pixel + 2];
                p[pixel + 2] = blue;
            }
        } else if (unpremultiply == 0) {
            for (int i = 0, pixel = 0; below(i, count); ++i, pixel += 4) {
                byte blue = p[pixel];
                p[pixel] = p[pixel + 2];
                p[pixel + 2] = blue;
            }
        } else {
            for (int i = 0, pixel = 0; below(i, count); ++i, pixel += 4) {
                int blue = p[pixel] & 0xff;
                int red = p[pixel + 2] & 0xff;
                int alpha = p[pixel + 3] & 0xff;
                if (alpha == 0) {
                    p[pixel] = (byte) red;
                    p[pixel + 2] = (byte) blue;
                } else {
                    p[pixel] = (byte) (red * 255 / alpha);
                    p[pixel + 1] = (byte) ((p[pixel + 1] & 0xff) * 255 / alpha);
                    p[pixel + 2] = (byte) (blue * 255 / alpha);
                }
            }
        }
    }

    static boolean parsePng(Png png, int scan, int requested) {
        Input input = png.input;
        png.compressed = null;
        png.expanded = null;
        png.pixels = null;
        for (int expected : PNG_SIGNATURE) {
            if (input.readByte() != expected) {
                return fail("bad png sig");
            }
        }
        if (scan == 1) {
            return true;
        }
        boolean first = true;
        boolean iphone = false;
        boolean hasTransparency = false;
        int depth = 0;
        int interlace = 0;
        int paletteSize = 0;
        int used = 0;
        int capacity = 0;
        int colorType = 0;
        int paletteComponents = 0;
        byte[] transparent = new byte[3];
        byte[] palette = new byte[1024];
        for (; ; ) {
            int length = input.readBig32();
            int kind = input.readBig32();
            switch (kind) {
                case 0x43674249:
                    input.skip(length);
                    iphone = true;
                    break;
                case 0x49484452: {
                    if (!first) {
                        return fail("multiple IHDR");
                    }
                    if (length != 13) {
                        return fail("bad IHDR len");
                    }
                    input.width = input.readBig32();
                    if (Integer.compareUnsigned(input.width, 0x1000000) > 0) {
                        return fail("too large");
                    }
                    input.height = input.readBig32();
                    if (Integer.compareUnsigned(input.height, 0x1000000) > 0) {
                        return fail("too large");
                    }
                    depth = input.readByte();
                    if (depth > 8 || ((0x116 >>> depth) & 1) == 0) {
                        return fail("1/2/4/8-bit only");
                    }
                    colorType = input.readByte();
                    if (colorType > 6) {
                        return fail("bad ctype");
                    }
                    if (colorType == 3) {
                        paletteComponents = 3;
                    } else if ((colorType & 1) != 0) {
                        return fail("bad ctype");
                    }
                    if (input.readByte() != 0) {
                        return fail("bad comp method");
                    }
                    if (input.readByte() != 0) {
                        return fail("bad filter method");
                    }
                    interlace = input.readByte();
                    if (interlace > 1) {
                        return fail("bad interlace method");
                    }
                    if (input.width == 0 || input.height == 0) {
                        return fail("0-pixel image");
                    }
                    if (paletteComponents == 0) {
                        input.components = (colorType > 3 ? 1 : 0) + (colorType & 2) + 1;
                        if (Long.compareUnsigned(0x40000000L / Integer.toUnsignedLong(input.width) / input.components,
                                Integer.toUnsignedLong(input.height)) < 0) {
                            return fail("too large");
                        }
                        first = false;
                        if (scan == 2) {
                            return true;
                        }
                    } else {
                        input.components = 1;
                        first = false;
                        if (0x40000000L / Integer.toUnsignedLong(input.width) / 4L < Integer.toUnsignedLong(input.height)) {
                            return fail("too large");
                        }
                    }
                    break;
                }
                case 0x504c5445:
                    if (first) {
                        return fail("first not IHDR");
                    }
                    if (Integer.compareUnsigned(length, 768) > 0) {
                        return fail("invalid PLTE");
                    }
                    paletteSize = (length * 0xaaab) >>> 17;
                    if (paletteSize * 3 != length) {
                        return fail("invalid PLTE");
                    }
                    for (int i = 0; i < paletteSize; ++i) {
                        palette[i * 4] = (byte) input.readByte();
                        palette[i * 4 + 1] = (byte) input.readByte();
                        palette[i * 4 + 2] = (byte) input.readByte();
                        palette[i * 4 + 3] = (byte) 255;
                    }
                    first = false;
                    break;
                case 0x74524e53:
                    if (first) {
                        return fail("first not IHDR");
                    }
                    if (png.compressed != null) {
                        return fail("tRNS after IDAT");
                    }
                    if (paletteComponents == 0) {
                        if ((input.components & 1) == 0) {
                            return fail("tRNS with alpha");
                        }
                        if (input.components * 2 != length) {
                            return fail("bad tRNS len");
                        }
                        hasTransparency = true;
                        for (int i = 0; i < input.components; ++i) {
                            input.readByte();
                            transparent[i] = (byte) (input.readByte() * DEPTH_SCALE[depth]);
                        }
                    } else {
                        if (scan == 2) {
                            input.components = 4;
                            return true;
                        }
                        if (paletteSize == 0) {
                            return fail("tRNS before PLTE");
                        }
                        if (Integer.compareUnsigned(length, paletteSize) > 0) {
                            return fail("bad tRNS len");
                        }
                        paletteComponents = 4;
                        for (int i = 0; i < length; ++i) {
                            palette[i * 4 + 3] = (byte) input.readByte();
                        }
                    }
                    first = false;
                    break;
                case 0x49444154: {
                    if (first) {
                        return fail("first not IHDR");
                    }
                    if (paletteComponents != 0 && paletteSize == 0) {
                        return fail("no PLTE");
                    }
                    if (scan == 2) {
                        input.components = paletteComponents;
                        return true;
                    }
                    int next = used + length;
                    if (next < used) {
                        return false;
                    }
                    if (below(capacity, next)) {
                        if (capacity == 0) {
                            capacity = Integer.compareUnsigned(length, 4096) > 0 ? length : 4096;
                        }
                        while (below(capacity, next)) {
                            capacity *= 2;
                        }
                        if (capacity < 0) {
                            return fail("outofmem");
                        }
                        png.compressed = png.compressed == null ? new byte[capacity]
                                : Arrays.copyOf(png.compressed, capacity);
                    }
                    if (!input.read(png.compressed, used, length)) {
                        return fail("outofdata");
                    }
                    used = next;
                    first = false;
                    break;
                }
                case 0x49454e44:
                    if (first) {
                        return fail("first not IHDR");
                    }
                    if (scan != 0) {
                        return true;
                    }
                    return finishPng(png, used, depth, colorType, interlace, iphone, hasTransparency, transparent,
                            palette, paletteComponents, requested);
                default:
                    if (first) {
                        return fail("first not IHDR");
                    }
                    if ((kind & 0x20000000) == 0) {
                        for (int i = 0; i < 4; ++i) {
                            UNKNOWN_PNG_CHUNK[i] = (char) ((kind >>> (24 - 8 * i)) & 0xff);
                        }
                        failure = new String(UNKNOWN_PNG_CHUNK, 0, 24);
                        return false;
                    }
                    input.skip(length);
                    first = false;
                    break;
            }
            input.readBig32();
        }
    }

    private static int luminance(byte[] p, int pixel) {
        return (((p[pixel] & 0xff) * 77 + (p[pixel + 1] & 0xff) * 150 + (p[pixel + 2] & 0xff) * 29) >>> 8);
    }

    static byte[] convertComponents(byte[] pixels, int inputComponents, int outputComponents, int width, int height) {
        if (inputComponents == outputComponents) {
            return pixels;
        }
        int outputStride = width * outputComponents;
        int inputStride = width * inputComponents;
        byte[] allocation = new byte[outputStride * height];
        int conversion = outputComponents + inputComponents * 8;
        for (int y = 0; y < height; ++y) {
            int destination = y * outputStride;
            int source = y * inputStride;
            if (width - 1 < 0) {
                continue;
            }
            for (int x = 0; below(x, width); ++x) {
                byte[] d = allocation;
                byte[] s = pixels;
                switch (conversion) {
                    case 10:
                        d[destination] = s[source];
                        d[destination + 1] = (byte) 255;
                        break;
                    case 11:
                    case 19:
                        d[destination] = d[destination + 1] = d[destination + 2] = s[source];
                        break;
                    case 12:
                        d[destination] = d[destination + 1] = d[destination + 2] = s[source];
                        d[destination + 3] = (byte) 255;
                        break;
                    case 17:
                        d[destination] = s[source];
                        break;
                    case 20:
                        d[destination] = d[destination + 1] = d[destination + 2] = s[source];
                        d[destination + 3] = s[source + 1];
                        break;
                    case 25:
                    case 33:
                        d[destination] = (byte) luminance(s, source);
                        break;
                    case 26:
                        d[destination] = (byte) luminance(s, source);
                        d[destination + 1] = (byte) 255;
                        break;
                    case 28:
                        d[destination] = s[source];
                        d[destination + 1] = s[source + 1];
                        d[destination + 2] = s[source + 2];
                        d[destination + 3] = (byte) 255;
                        break;
                    case 34:
                        d[destination] = (byte) luminance(s, source);
                        d[destination + 1] = s[source + 3];
                        break;
                    case 35:
                        d[destination] = s[source];
                        d[destination + 1] = s[source + 1];
                        d[destination + 2] = s[source + 2];
                        break;
                    default:
                        break;
                }
                source += inputComponents;
                destination += outputComponents;
            }
        }
        return allocation;
    }

    static class Image {
        final byte[] pixels;
        final int width;
        final int height;
        final int components;

        Image(byte[] pixels, int width, int height, int components) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.components = components;
        }
    }

    static Image decodePng(Input input, int requested) {
        input.current = 0;
        input.end = input.originalEnd;
        if (Integer.compareUnsigned(requested, 5) >= 0) {
            failure = "bad req_comp";
            return null;
        }
        Png png = new Png(input);
        if (!parsePng(png, 0, requested)) {
            return null;
        }
        byte[] result = png.pixels;
        if (requested != 0 && requested != input.outputComponents) {
            result = convertComponents(png.pixels, input.outputComponents, requested, input.width, input.height);
            input.outputComponents = requested;
        }
        return new Image(result, input.width, input.height, input.outputComponents);
    }
}

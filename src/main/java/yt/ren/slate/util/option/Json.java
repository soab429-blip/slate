package yt.ren.slate.util.option;

import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.util.math.Sse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Json {
    public static final int FALSE = 1;
    public static final int TRUE = 2;
    public static final int NULL = 4;
    public static final int NUMBER = 8;
    public static final int STRING = 16;
    public static final int ARRAY = 32;
    public static final int OBJECT = 64;

    public Json next;
    public Json previous;
    public Json child;
    public int type;
    public byte[] text;
    public int integer;
    public double number;
    public byte[] key;

    Json() {
    }

    private Json(int type) {
        this.type = type;
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

    public static Json member(Json object, byte[] key) {
        if (object != null && key != null) {
            for (Json item = object.child; item != null && item.key != null; item = item.next) {
                if (strcmp(key, item.key) == 0) {
                    return item;
                }
            }
        }
        return null;
    }

    public static Json member(Json object, String key) {
        return member(object, key.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isNumber(Json v) {
        return v != null && (v.type & 255) == NUMBER;
    }

    public static boolean isString(Json v) {
        return v != null && (v.type & 255) == STRING;
    }

    public static boolean isBoolean(Json v) {
        return v != null && (v.type & 3) != 0;
    }

    public static boolean isTrue(Json v) {
        return v != null && (v.type & 255) == TRUE;
    }

    public static boolean isObject(Json v) {
        return v != null && (v.type & 255) == OBJECT;
    }

    public static boolean isArray(Json v) {
        return v != null && (v.type & 255) == ARRAY;
    }

    public static double number(Json v) {
        return isNumber(v) ? v.number : Double.longBitsToDouble(0x7ff8000000000000L);
    }

    public static boolean append(Json parent, Json item) {
        if (parent == null || item == null || parent == item) {
            return false;
        }
        Json head = parent.child;
        if (head == null) {
            parent.child = item;
            item.previous = item;
            item.next = null;
        } else if (head.previous != null) {
            Json last = head.previous;
            last.next = item;
            item.previous = last;
            head.previous = item;
        }
        return true;
    }

    private static Json attach(Json parent, byte[] key, Json value) {
        if (parent != null && key != null && value != null && parent != value) {
            value.key = key.clone();
            append(parent, value);
            return value;
        }
        return null;
    }

    private static byte[] b(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static Json object() {
        return new Json(OBJECT);
    }

    public static Json string(byte[] value) {
        Json result = new Json(STRING);
        result.text = value.clone();
        return result;
    }

    public static Json addBoolean(Json parent, String key, int value) {
        return attach(parent, b(key), new Json(value != 0 ? TRUE : FALSE));
    }

    static int integerValue(double value) {
        return value >= 2147483647.0 ? 2147483647 : value <= -2147483648.0 ? Integer.MIN_VALUE : Sse.cvttsd2si(value);
    }

    public static Json addNumber(Json parent, String key, double value) {
        Json result = new Json(NUMBER);
        result.number = value;
        result.integer = integerValue(value);
        return attach(parent, b(key), result);
    }

    public static Json addString(Json parent, String key, byte[] value) {
        return attach(parent, b(key), string(value));
    }

    public static Json addObject(Json parent, byte[] key) {
        return attach(parent, key, new Json(OBJECT));
    }

    public static Json addObject(Json parent, String key) {
        return addObject(parent, b(key));
    }

    public static Json addArray(Json parent, String key) {
        return attach(parent, b(key), new Json(ARRAY));
    }

    private static class Parse {
        final byte[] content;
        final int length;
        int offset;
        int depth;

        Parse(byte[] content, int length) {
            this.content = content;
            this.length = length;
        }

        int at(int i) {
            return content[i] & 0xff;
        }

        boolean matches(String text) {
            int size = text.length();
            if (size > length - offset) {
                return false;
            }
            for (int i = 0; i < size; ++i) {
                if (content[offset + i] != text.charAt(i)) {
                    return false;
                }
                if (content[offset + i] == 0) {
                    return true;
                }
            }
            return true;
        }
    }

    private static void skipWhitespace(Parse b) {
        if (b.offset >= b.length) {
            return;
        }
        while (b.offset < b.length && b.at(b.offset) <= 0x20) {
            ++b.offset;
        }
        if (b.offset == b.length) {
            --b.offset;
        }
    }

    private static int hexFour(byte[] text, int offset) {
        int value = 0;
        for (int i = 0; i < 4; ++i) {
            int c = text[offset + i] & 0xff;
            int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                digit = c - 'A' + 10;
            } else if (c >= 'a' && c <= 'f') {
                digit = c - 'a' + 10;
            } else {
                return 0;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    private static boolean parseNumber(Json value, Parse b) {
        byte[] token = new byte[64];
        int count = 0;
        while (count < 63 && b.offset + count < b.length) {
            int c = b.content[b.offset + count];
            if (c == '.' || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == 'e' || c == 'E') {
                token[count] = (byte) c;
            } else {
                break;
            }
            ++count;
        }
        int[] end = new int[1];
        double number = ConfigNumbers.strtod(token, count, end);
        if (end[0] == 0) {
            return false;
        }
        value.number = number;
        value.integer = integerValue(number);
        value.type = NUMBER;
        b.offset += end[0];
        return true;
    }

    private static boolean parseString(Json value, Parse b) {
        int opening = b.offset;
        int cursor = opening + 1;
        if (opening >= b.length || b.content[opening] != '"') {
            b.offset = cursor;
            return false;
        }
        int closing = cursor;
        for (; closing < b.length; ++closing) {
            if (b.content[closing] == '\\') {
                if (closing + 1 >= b.length) {
                    b.offset = cursor;
                    return false;
                }
                ++closing;
            } else if (b.content[closing] == '"') {
                break;
            }
        }
        if (closing >= b.length) {
            b.offset = cursor;
            return false;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (cursor < closing) {
            int c = b.content[cursor];
            if (c != '\\') {
                output.write(c);
                ++cursor;
                continue;
            }
            int consumed = 2;
            switch (b.content[cursor + 1]) {
                case '"':
                case '\\':
                case '/':
                    output.write(b.content[cursor + 1]);
                    break;
                case 'b':
                    output.write('\b');
                    break;
                case 'f':
                    output.write('\f');
                    break;
                case 'n':
                    output.write('\n');
                    break;
                case 'r':
                    output.write('\r');
                    break;
                case 't':
                    output.write('\t');
                    break;
                case 'u': {
                    if (closing - cursor < 6) {
                        b.offset = cursor;
                        return false;
                    }
                    int cp = hexFour(b.content, cursor + 2);
                    consumed = 6;
                    if (cp >= 0xdc00 && cp <= 0xdfff) {
                        b.offset = cursor;
                        return false;
                    }
                    if (cp >= 0xd800 && cp <= 0xdbff) {
                        if (closing - cursor < 12 || b.content[cursor + 6] != '\\' || b.content[cursor + 7] != 'u') {
                            b.offset = cursor;
                            return false;
                        }
                        int low = hexFour(b.content, cursor + 8);
                        if (low < 0xdc00 || low > 0xdfff) {
                            b.offset = cursor;
                            return false;
                        }
                        cp = ((cp & 0x3ff) << 10) + (low & 0x3ff) + 0x10000;
                        consumed = 12;
                    }
                    if (cp < 0x80) {
                        output.write(cp);
                    } else {
                        int length = cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
                        byte[] encoded = new byte[length];
                        for (int i = length - 1; i > 0; --i) {
                            encoded[i] = (byte) ((cp & 0x3f) | 0x80);
                            cp >>>= 6;
                        }
                        encoded[0] = (byte) (cp | (length == 2 ? 0xc0 : length == 3 ? 0xe0 : 0xf0));
                        output.write(encoded, 0, length);
                    }
                    break;
                }
                default:
                    b.offset = cursor;
                    return false;
            }
            cursor += consumed;
        }
        value.type = STRING;
        value.text = output.toByteArray();
        b.offset = closing + 1;
        return true;
    }

    private static boolean parseArray(Json value, Parse b) {
        if (b.depth >= 1000) {
            return false;
        }
        ++b.depth;
        if (b.offset >= b.length || b.content[b.offset] != '[') {
            return false;
        }
        ++b.offset;
        skipWhitespace(b);
        if (b.offset >= b.length) {
            --b.offset;
            return false;
        }
        if (b.content[b.offset] == ']') {
            --b.depth;
            value.type = ARRAY;
            value.child = null;
            ++b.offset;
            return true;
        }
        --b.offset;
        Json head = null;
        Json tail = null;
        do {
            Json item = new Json();
            if (head != null) {
                tail.next = item;
                item.previous = tail;
            } else {
                head = item;
            }
            tail = item;
            ++b.offset;
            skipWhitespace(b);
            if (!parseValue(item, b)) {
                return false;
            }
            skipWhitespace(b);
            if (b.offset >= b.length) {
                return false;
            }
        } while (b.content[b.offset] == ',');
        if (b.content[b.offset] != ']') {
            return false;
        }
        --b.depth;
        head.previous = tail;
        value.type = ARRAY;
        value.child = head;
        ++b.offset;
        return true;
    }

    private static boolean parseObject(Json value, Parse b) {
        if (b.depth >= 1000) {
            return false;
        }
        ++b.depth;
        if (b.offset >= b.length || b.content[b.offset] != '{') {
            return false;
        }
        ++b.offset;
        skipWhitespace(b);
        if (b.offset >= b.length) {
            --b.offset;
            return false;
        }
        if (b.content[b.offset] == '}') {
            --b.depth;
            value.type = OBJECT;
            value.child = null;
            ++b.offset;
            return true;
        }
        --b.offset;
        Json head = null;
        Json tail = null;
        do {
            Json item = new Json();
            if (head != null) {
                tail.next = item;
                item.previous = tail;
            } else {
                head = item;
            }
            tail = item;
            ++b.offset;
            skipWhitespace(b);
            if (!parseString(item, b)) {
                return false;
            }
            skipWhitespace(b);
            item.key = item.text;
            item.text = null;
            if (b.offset >= b.length || b.content[b.offset] != ':') {
                return false;
            }
            ++b.offset;
            skipWhitespace(b);
            if (!parseValue(item, b)) {
                return false;
            }
            skipWhitespace(b);
            if (b.offset >= b.length) {
                return false;
            }
        } while (b.content[b.offset] == ',');
        if (b.content[b.offset] != '}') {
            return false;
        }
        --b.depth;
        head.previous = tail;
        value.type = OBJECT;
        value.child = head;
        ++b.offset;
        return true;
    }

    private static boolean parseValue(Json value, Parse b) {
        if (b.offset > b.length) {
            return false;
        }
        if (b.matches("null")) {
            value.type = NULL;
            b.offset += 4;
            return true;
        }
        if (b.matches("false")) {
            value.type = FALSE;
            b.offset += 5;
            return true;
        }
        if (b.matches("true")) {
            value.type = TRUE;
            value.integer = 1;
            b.offset += 4;
            return true;
        }
        if (b.offset >= b.length) {
            return false;
        }
        int c = b.content[b.offset];
        if (c == '"') {
            return parseString(value, b);
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return parseNumber(value, b);
        }
        if (c == '{') {
            return parseObject(value, b);
        }
        if (c == '[') {
            return parseArray(value, b);
        }
        return false;
    }

    public static Json parse(byte[] text) {
        if (text == null) {
            return null;
        }
        int strlen = 0;
        while (strlen < text.length && text[strlen] != 0) {
            ++strlen;
        }
        byte[] content = Arrays.copyOf(text, strlen + 1);
        int length = strlen + 1;
        Parse buffer = new Parse(content, length);
        Json value = new Json();
        if (length >= 5 && buffer.at(0) == 0xef && buffer.at(1) == 0xbb && buffer.at(2) == 0xbf) {
            buffer.offset = 3;
        }
        skipWhitespace(buffer);
        if (parseValue(value, buffer)) {
            return value;
        }
        return null;
    }

    private static void printString(byte[] value, ByteArrayOutputStream out) {
        if (value == null) {
            out.write('"');
            out.write('"');
            return;
        }
        out.write('"');
        byte[] digits = "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);
        for (byte raw : value) {
            int c = raw & 0xff;
            if (c == 0) {
                break;
            }
            switch (c) {
                case '"':
                case '\\':
                    out.write('\\');
                    out.write(c);
                    break;
                case '\b':
                    out.write('\\');
                    out.write('b');
                    break;
                case '\f':
                    out.write('\\');
                    out.write('f');
                    break;
                case '\n':
                    out.write('\\');
                    out.write('n');
                    break;
                case '\r':
                    out.write('\\');
                    out.write('r');
                    break;
                case '\t':
                    out.write('\\');
                    out.write('t');
                    break;
                default:
                    if (c < 0x20) {
                        out.write('\\');
                        out.write('u');
                        out.write('0');
                        out.write('0');
                        out.write(digits[c >> 4]);
                        out.write(digits[c & 15]);
                    } else {
                        out.write(c);
                    }
            }
        }
        out.write('"');
    }

    private static boolean printNumber(Json value, ByteArrayOutputStream out) {
        String token;
        if ((Double.doubleToRawLongBits(value.number) & 0x7ff0000000000000L) == 0x7ff0000000000000L) {
            token = "null";
        } else if (value.number == (double) value.integer) {
            token = ValueFormatter.format("%d", value.integer);
        } else {
            token = ValueFormatter.format("%1.15g", value.number);
            double[] roundTrip = new double[1];
            int scanned = ConfigNumbers.scanReal(token, roundTrip);
            if (scanned != 1) {
                token = ValueFormatter.format("%1.17g", value.number);
            } else {
                double first = Math.abs(roundTrip[0]);
                double second = Math.abs(value.number);
                double magnitude = Math.max(first, second);
                if (!(Math.abs(roundTrip[0] - value.number) <= magnitude * 2.220446049250313e-16)) {
                    token = ValueFormatter.format("%1.17g", value.number);
                }
            }
        }
        if (token.length() > 25) {
            return false;
        }
        out.write(token.getBytes(StandardCharsets.ISO_8859_1), 0, token.length());
        return true;
    }

    private static boolean printValue(Json value, ByteArrayOutputStream out) {
        if (value == null) {
            return true;
        }
        switch (value.type & 255) {
            case FALSE:
                out.write("false".getBytes(StandardCharsets.ISO_8859_1), 0, 5);
                return false;
            case TRUE:
                out.write("true".getBytes(StandardCharsets.ISO_8859_1), 0, 4);
                return false;
            case NULL:
                out.write("null".getBytes(StandardCharsets.ISO_8859_1), 0, 4);
                return false;
            case NUMBER:
                return !printNumber(value, out);
            case STRING:
                printString(value.text, out);
                return false;
            case ARRAY:
                out.write('[');
                for (Json item = value.child; item != null; item = item.next) {
                    if (printValue(item, out)) {
                        return true;
                    }
                    if (item.next == null) {
                        break;
                    }
                    out.write(',');
                }
                out.write(']');
                return false;
            case OBJECT:
                out.write('{');
                for (Json item = value.child; item != null; item = item.next) {
                    printString(item.key, out);
                    out.write(':');
                    if (printValue(item, out)) {
                        return true;
                    }
                    if (item.next != null) {
                        out.write(',');
                    }
                }
                out.write('}');
                return false;
            default:
                return true;
        }
    }

    public static byte[] print(Json value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        if (printValue(value, out)) {
            return null;
        }
        return out.toByteArray();
    }
}

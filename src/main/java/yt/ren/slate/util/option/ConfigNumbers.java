package yt.ren.slate.util.option;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class ConfigNumbers {
    private static boolean digit(int c) {
        return c >= '0' && c <= '9';
    }

    private static int prefix(byte[] text, int length) {
        int i = 0;
        if (i < length && (text[i] == '+' || text[i] == '-')) {
            ++i;
        }
        int mantissaStart = i;
        int digits = 0;
        while (i < length && digit(text[i])) {
            ++i;
            ++digits;
        }
        if (i < length && text[i] == '.') {
            ++i;
            while (i < length && digit(text[i])) {
                ++i;
                ++digits;
            }
        }
        if (digits == 0) {
            return 0;
        }
        int end = i;
        if (i < length && (text[i] == 'e' || text[i] == 'E')) {
            int j = i + 1;
            if (j < length && (text[j] == '+' || text[j] == '-')) {
                ++j;
            }
            if (j < length && digit(text[j])) {
                while (j < length && digit(text[j])) {
                    ++j;
                }
                end = j;
            }
        }
        return mantissaStart > end ? 0 : end;
    }

    static double strtod(byte[] text, int length, int[] end) {
        int consumed = prefix(text, length);
        end[0] = consumed;
        if (consumed == 0) {
            return 0.0;
        }
        String token = new String(text, 0, consumed, StandardCharsets.ISO_8859_1);
        return Double.parseDouble(token);
    }

    static int scanReal(String text, double[] value) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        int start = 0;
        while (start < bytes.length && (bytes[start] == ' ' || (bytes[start] >= 9 && bytes[start] <= 13))) {
            ++start;
        }
        byte[] rest = Arrays.copyOfRange(bytes, start, bytes.length);
        int[] end = new int[1];
        double result = strtod(rest, rest.length, end);
        if (end[0] == 0) {
            return 0;
        }
        value[0] = result;
        return 1;
    }
}

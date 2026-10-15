package yt.ren.slate.text;

import java.nio.charset.StandardCharsets;

public class TextBuffer {
    public static String format(int capacity, String format, Object... args) {
        String text = ValueFormatter.format(format, args);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < capacity) {
            return text;
        }
        return new String(bytes, 0, capacity - 1, StandardCharsets.UTF_8);
    }
}

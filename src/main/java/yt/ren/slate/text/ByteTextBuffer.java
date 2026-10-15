package yt.ren.slate.text;

import java.nio.charset.StandardCharsets;

public class ByteTextBuffer {
    public final byte[] data;
    public final int capacity;
    public int size;

    public ByteTextBuffer(byte[] data, int capacity) {
        this.data = data;
        this.capacity = capacity;
        this.size = 0;
    }

    public boolean appendText(byte[] text, int length) {
        if (size >= capacity) {
            return false;
        }
        if (length >= capacity - size) {
            return false;
        }
        System.arraycopy(text, 0, data, size, length);
        size += length;
        return true;
    }

    public boolean appendText(byte[] text) {
        return appendText(text, text.length);
    }

    public boolean appendFormat(String format, Object... args) {
        if (size >= capacity) {
            return false;
        }
        int available = capacity - size;
        byte[] out = ValueFormatter.format(format, args).getBytes(StandardCharsets.UTF_8);
        int count = out.length;
        if (count >= available) {
            System.arraycopy(out, 0, data, size, available - 1);
            data[size + available - 1] = 0;
            size = capacity;
            return false;
        }
        System.arraycopy(out, 0, data, size, count);
        data[size + count] = 0;
        size += count;
        return true;
    }

    public boolean appendCharacter(byte character) {
        if (size >= capacity) {
            return false;
        }
        data[size++] = character;
        return true;
    }

    public boolean finish() {
        return appendCharacter((byte) 0);
    }
}

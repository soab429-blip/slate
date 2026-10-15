package yt.ren.slate.ui;

import yt.ren.slate.input.WindowInput;
import yt.ren.slate.input.WindowInputHost;
import yt.ren.slate.ui.vector.UIColor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class UIState {
    public static final int SIZE = 0x4d8;

    public static final int CAPTURE_MODE = 0x370;
    public static final int CAPTURE = 0x378;
    public static final int CAPTURE_SIZE = 104;
    public static final int APPEND_CHARACTER = 0x3e0;
    public static final int CHARACTER_CONTEXT = 0x3e8;
    public static final int SUBMITTED = 0x3f0;
    public static final int POPUP_TYPE = 0x3f8;
    public static final int POPUP_OWNER = 0x400;
    public static final int POPUP_TITLE = 0x408;
    public static final int POPUP_LAST_UPDATE = 0x410;
    public static final int POPUP_STORAGE = 0x418;
    public static final int HIT_X = 0x4a8;
    public static final int HIT_Y = 0x4ac;
    public static final int MOUSE_X = 0x4b0;
    public static final int MOUSE_Y = 0x4b4;
    public static final int SCROLL = 0x4bc;
    public static final int LEFT_BUTTON = 0x4c8;
    public static final int LEFT_CONSUMED = 0x4c9;
    public static final int SECONDARY_BUTTON = 0x4ca;
    public static final int SHUTTING_DOWN = 0x4d0;

    public final Object popupKey = new Object();

    public final Object entryKey = new Object();

    public final ByteBuffer m = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private final Object[] refs = new Object[SIZE / 8];
    private float scrollTarget;

    public float f(int offset) {
        return m.getFloat(offset);
    }

    public void f(int offset, float value) {
        m.putFloat(offset, value);
    }

    public int i(int offset) {
        return m.getInt(offset);
    }

    public void i(int offset, int value) {
        m.putInt(offset, value);
    }

    public long l(int offset) {
        return m.getLong(offset);
    }

    public void l(int offset, long value) {
        m.putLong(offset, value);
    }

    public double d(int offset) {
        return m.getDouble(offset);
    }

    public void d(int offset, double value) {
        m.putDouble(offset, value);
    }

    public int b(int offset) {
        return m.get(offset) & 0xff;
    }

    public void b(int offset, int value) {
        m.put(offset, (byte) value);
    }

    public Object ref(int offset) {
        return refs[offset >> 3];
    }

    public void ref(int offset, Object value) {
        refs[offset >> 3] = value;
    }

    public UIColor color(int offset) {
        return new UIColor(f(offset), f(offset + 4), f(offset + 8), f(offset + 12));
    }

    public void color(int offset, UIColor color) {
        f(offset, color.red);
        f(offset + 4, color.green);
        f(offset + 8, color.blue);
        f(offset + 12, color.alpha);
    }

    public void clear(int offset, int length) {
        for (int k = 0; k < length; ++k) {
            m.put(offset + k, (byte) 0);
        }
        for (int slot = (offset + 7) >> 3; slot < (offset + length) >> 3; ++slot) {
            refs[slot] = null;
        }
    }

    public byte[] string(int offset, int limit) {
        int length = 0;
        while (length < limit && m.get(offset + length) != 0) {
            ++length;
        }
        byte[] result = new byte[length];
        for (int k = 0; k < length; ++k) {
            result[k] = m.get(offset + k);
        }
        return result;
    }

    public void strncpy(int offset, byte[] text, int count) {
        int k = 0;
        for (; k < count && k < text.length && text[k] != 0; ++k) {
            m.put(offset + k, text[k]);
        }
        for (; k < count; ++k) {
            m.put(offset + k, (byte) 0);
        }
    }

    public int strlen(int offset) {
        int length = 0;
        while (m.get(offset + length) != 0) {
            ++length;
        }
        return length;
    }

    public int captureMode() {
        return i(CAPTURE_MODE);
    }

    public void captureMode(int value) {
        i(CAPTURE_MODE, value);
        WindowInput input = WindowInputHost.input();
        if (input != null) {
            input.textCapture(value == 2 || value == 3);
        }
    }

    public float hitX() {
        return f(HIT_X);
    }

    public float hitY() {
        return f(HIT_Y);
    }

    public float scroll() {
        return f(SCROLL);
    }

    public void scroll(float value) {
        f(SCROLL, value);
    }

    public float scrollTarget() {
        return scrollTarget;
    }

    public void scrollTarget(float value) {
        scrollTarget = value;
    }

    public int leftButton() {
        return b(LEFT_BUTTON);
    }

    public int popupType() {
        return i(POPUP_TYPE);
    }

    public Object popupRef(int offset) {
        return ref(offset);
    }

    @Override
    public String toString() {
        return "UIState" + Arrays.toString(new int[]{captureMode(), popupType()});
    }
}

package yt.ren.slate.input;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

public class WindowInput {
    @FunctionalInterface
    public interface Handler {
        int handle(ClientInputEvent event);
    }

    public static final class PostedClick {
        public final boolean left;
        public final boolean down;

        PostedClick(boolean left, boolean down) {
            this.left = left;
            this.down = down;
        }
    }

    private final ArrayDeque<PostedClick> posted = new ArrayDeque<>();
    private Handler handler;
    private volatile boolean textCapture;

    public void connect(Handler handler) {
        this.handler = handler;
    }

    public static int windowVirtualKey(long key) {
        if (Long.compareUnsigned(key - 0x41L, 26L) < 0) {
            return (int) key - 0x27;
        }
        if (Long.compareUnsigned(key - 0x30L, 10L) < 0) {
            return (int) key - 0x2a;
        }
        if (Long.compareUnsigned(key - 0x60L, 10L) < 0) {
            return (int) key - 0x50;
        }
        if ((key & 0xfffffffffffffff0L) == 0x70L) {
            return (int) key - 0x2d;
        }
        switch ((int) key) {
            case 0x08:
                return 0x53;
            case 0x09:
                return 0x54;
            case 0x0d:
                return 0x55;
            case 0x14:
                return 0x56;
            case 0x1b:
                return 0x57;
            case 0x20:
                return 0x58;
            case 0x21:
                return 0x59;
            case 0x22:
                return 0x5a;
            case 0x23:
                return 0x5b;
            case 0x24:
                return 0x5c;
            case 0x25:
                return 0x3f;
            case 0x26:
                return 0x40;
            case 0x27:
                return 0x41;
            case 0x28:
                return 0x42;
            case 0x2a:
                return 0x5d;
            case 0x2d:
                return 0x5e;
            case 0x2e:
                return 0x5f;
            case 0x90:
                return 0x60;
            case 0x91:
                return 0x61;
            case 0xa0:
                return 0x62;
            case 0xa1:
                return 0x63;
            case 0xa2:
                return 0x64;
            case 0xa3:
                return 0x65;
            case 0xa4:
                return 0x66;
            case 0xa5:
                return 0x67;
            case 0xba:
                return 0x34;
            case 0xbb:
                return 0x35;
            case 0xbc:
                return 0x36;
            case 0xbd:
                return 0x37;
            case 0xbe:
                return 0x38;
            case 0xbf:
                return 0x39;
            case 0xc0:
            case 0xdf:
                return 0x3a;
            case 0xdb:
                return 0x3b;
            case 0xdc:
                return 0x3c;
            case 0xdd:
                return 0x3d;
            case 0xde:
                return 0x3e;
            default:
                return 0;
        }
    }

    public boolean textCapture() {
        return textCapture;
    }

    public void textCapture(boolean value) {
        textCapture = value;
    }

    public void postMouseButton(int left, int pressed) {
        synchronized (posted) {
            posted.add(new PostedClick(left != 0, pressed != 0));
        }
    }

    private int emit(ClientInputEvent event) {
        return handler != null ? handler.handle(event) : 0;
    }

    public boolean keyEvent(int key, boolean down, int synthetic) {
        ClientInputEvent event = new ClientInputEvent(2, key, down ? 1 : 0, synthetic);
        return emit(event) != 0;
    }

    public boolean characterEvent(char character) {
        ClientInputEvent event = new ClientInputEvent(2, 0, 1, 0);
        byte[] bytes = utf8(character);
        System.arraycopy(bytes, 0, event.characters, 0, bytes.length);
        return bytes.length != 0 && emit(event) != 0;
    }

    public boolean rawInputEvent() {
        return emit(new ClientInputEvent(4, 0, 0, 0)) != 0;
    }

    public boolean mouseMoveEvent(int x, int y) {
        ClientInputEvent event = new ClientInputEvent(1, x, 0, 0);
        event.setMouseY(y);
        return emit(event) != 0;
    }

    public boolean wheelEvent(float amount) {
        return emit(new ClientInputEvent(3, Float.floatToRawIntBits(amount), 0, 0)) != 0;
    }

    public PostedClick pollPosted() {
        synchronized (posted) {
            return posted.poll();
        }
    }

    private static byte[] utf8(char character) {
        if (Character.isSurrogate(character)) {
            return new byte[]{(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};
        }
        return String.valueOf(character).getBytes(StandardCharsets.UTF_8);
    }
}

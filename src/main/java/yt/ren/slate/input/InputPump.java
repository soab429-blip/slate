package yt.ren.slate.input;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.ArrayDeque;

public class InputPump {
    public static final class MouseEvent {
        public final int button;
        public final boolean state;
        public final int x;
        public final int y;
        public final int dx;
        public final int dy;
        public final int wheel;
        public final long nanos;

        MouseEvent(int button, boolean state, int x, int y, int dx, int dy, int wheel, long nanos) {
            this.button = button;
            this.state = state;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.wheel = wheel;
            this.nanos = nanos;
        }
    }

    public static final class KeyEvent {
        public final int key;
        public final char character;
        public final boolean state;
        public final boolean repeat;
        public final long nanos;

        KeyEvent(int key, char character, boolean state, boolean repeat, long nanos) {
            this.key = key;
            this.character = character;
            this.state = state;
            this.repeat = repeat;
            this.nanos = nanos;
        }
    }

    private static final int[] VIRTUAL_KEYS = new int[256];

    private static final ArrayDeque<MouseEvent> mouseQueue = new ArrayDeque<>();
    private static final ArrayDeque<KeyEvent> keyQueue = new ArrayDeque<>();
    private static final boolean[] held = new boolean[2];

    private static boolean pumping;
    private static MouseEvent mouseEvent;
    private static KeyEvent keyEvent;

    static {
        letters(16, "QWERTYUIOP");
        letters(30, "ASDFGHJKL");
        letters(44, "ZXCVBNM");
        for (int i = 0; i < 9; ++i) {
            VIRTUAL_KEYS[2 + i] = '1' + i;
        }
        for (int i = 0; i < 10; ++i) {
            VIRTUAL_KEYS[59 + i] = 0x70 + i;
        }
        int[][] pairs = {
                {1, 0x1b}, {11, 0x30}, {12, 0xbd}, {13, 0xbb}, {14, 0x08}, {15, 0x09}, {26, 0xdb}, {27, 0xdd},
                {28, 0x0d}, {29, 0xa2}, {39, 0xba}, {40, 0xde}, {41, 0xc0}, {42, 0xa0}, {43, 0xdc}, {51, 0xbc},
                {52, 0xbe}, {53, 0xbf}, {54, 0xa1}, {55, 0x6a}, {56, 0xa4}, {57, 0x20}, {58, 0x14}, {69, 0x90},
                {70, 0x91}, {71, 0x67}, {72, 0x68}, {73, 0x69}, {74, 0x6d}, {75, 0x64}, {76, 0x65}, {77, 0x66},
                {78, 0x6b}, {79, 0x61}, {80, 0x62}, {81, 0x63}, {82, 0x60}, {83, 0x6e}, {87, 0x7a}, {88, 0x7b},
                {156, 0x0d}, {157, 0xa3}, {181, 0x6f}, {183, 0x2c}, {184, 0xa5}, {197, 0x13}, {199, 0x24},
                {200, 0x26}, {201, 0x21}, {203, 0x25}, {205, 0x27}, {207, 0x23}, {208, 0x28}, {209, 0x22},
                {210, 0x2d}, {211, 0x2e}, {219, 0x5b}, {220, 0x5c}, {221, 0x5d}
        };
        for (int[] pair : pairs) {
            VIRTUAL_KEYS[pair[0]] = pair[1];
        }
    }

    private static void letters(int start, String keys) {
        for (int i = 0; i < keys.length(); ++i) {
            VIRTUAL_KEYS[start + i] = keys.charAt(i);
        }
    }

    public static boolean pumping() {
        return pumping;
    }

    public static void pump() {
        if (pumping) {
            return;
        }
        WindowInput input = WindowInputHost.input();
        pumping = true;
        try {
            if (Mouse.isCreated()) {
                pumpMouse(input);
            }
            if (Keyboard.isCreated()) {
                pumpKeyboard(input);
            }
        } finally {
            pumping = false;
        }
    }

    private static void pumpMouse(WindowInput input) {
        if (input != null) {
            for (WindowInput.PostedClick click = input.pollPosted(); click != null; click = input.pollPosted()) {
                if (input.keyEvent(click.left ? 1 : 2, click.down, 1)) {
                    continue;
                }
                int button = click.left ? 0 : 1;
                held[button] = click.down;
                mouseQueue.add(new MouseEvent(button, click.down, Mouse.getX(), Mouse.getY(), 0, 0, 0,
                        System.nanoTime()));
            }
        }
        while (Mouse.next()) {
            MouseEvent event = new MouseEvent(Mouse.getEventButton(), Mouse.getEventButtonState(),
                    Mouse.getEventX(), Mouse.getEventY(), Mouse.getEventDX(), Mouse.getEventDY(),
                    Mouse.getEventDWheel(), Mouse.getEventNanoseconds());
            if (event.button >= 0 && event.button < held.length) {
                held[event.button] = false;
            }
            if (input == null || !mouse(input, event)) {
                mouseQueue.add(event);
            }
        }
    }

    private static boolean mouse(WindowInput input, MouseEvent event) {
        if (event.button >= 0) {
            int key = buttonKey(event.button);
            return key != 0 && input.keyEvent(key, event.state, 0);
        }
        if (event.wheel != 0) {
            return input.wheelEvent(Math.abs(event.wheel) >= 120 ? event.wheel / 120.0f : event.wheel);
        }
        if (input.mouseMoveEvent(event.x, Display.getHeight() - 1 - event.y)) {
            return true;
        }
        return (event.dx != 0 || event.dy != 0) && input.rawInputEvent();
    }

    private static void pumpKeyboard(WindowInput input) {
        while (Keyboard.next()) {
            KeyEvent event = new KeyEvent(Keyboard.getEventKey(), Keyboard.getEventCharacter(),
                    Keyboard.getEventKeyState(), Keyboard.isRepeatEvent(), Keyboard.getEventNanoseconds());
            if (input == null || !keyboard(input, event)) {
                keyQueue.add(event);
            }
        }
    }

    private static boolean keyboard(WindowInput input, KeyEvent event) {
        int key = event.key;
        int code = key > 0 && key < VIRTUAL_KEYS.length ? WindowInput.windowVirtualKey(VIRTUAL_KEYS[key]) : 0;
        boolean consumed = code != 0 && input.keyEvent(code, event.state, 0);
        char character = event.character;
        if (event.state && character != 0 && !Character.isISOControl(character)) {
            consumed |= input.characterEvent(character);
        }
        return consumed;
    }

    public static boolean nextMouse() {
        mouseEvent = mouseQueue.poll();
        return mouseEvent != null;
    }

    public static MouseEvent mouseEvent() {
        return mouseEvent;
    }

    public static boolean nextKeyboard() {
        keyEvent = keyQueue.poll();
        return keyEvent != null;
    }

    public static KeyEvent keyEvent() {
        return keyEvent;
    }

    public static boolean syntheticHeld(int button) {
        return button >= 0 && button < held.length && held[button];
    }

    public static boolean blockLook() {
        WindowInput input = WindowInputHost.input();
        return input != null && input.rawInputEvent();
    }

    private static int buttonKey(int button) {
        switch (button) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            case 4:
                return 5;
            default:
                return 0;
        }
    }
}

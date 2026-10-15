package yt.ren.slate.ui;

import java.nio.charset.StandardCharsets;

public class UIText {
    public static final byte[] UNKNOWN_KEY = b("NONE");
    public static final String GUI_HINT = "Press %s to open GUI";
    public static final String SINGLE_TOGGLE = "%s %s";
    public static final String ENABLED = "Enabled";
    public static final String DISABLED = "Disabled";
    public static final String MULTIPLE_TOGGLES = "%s %u mods";
    public static final String BRAND = "SLATE";
    public static final String DEFAULT_PROFILE = "Default";
    public static final String ERROR_TEXT = "%s";
    public static final String ERROR_CODE = "Error code %d";

    public static final String[] PROFILE_ACTIONS = {"Loaded profile", "Saved profile", "MZx", "Deleted profile"};
    public static final int[] TEXT_ALIGNMENTS = {17, 18, 20};

    private static final String[] KEY_NAME_TEXT = {"MOUSE1", "MOUSE2", "MOUSE3", "MOUSE4", "MOUSE5", "0", "1", "2", "3",
            "4", "5", "6", "7", "8", "9", "NUMPAD0", "NUMPAD1", "NUMPAD2", "NUMPAD3", "NUMPAD4", "NUMPAD5", "NUMPAD6",
            "NUMPAD7", "NUMPAD8", "NUMPAD9", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
            "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "COLON", "PLUS", "COMMA", "MINUS", "PERIOD", "SLASH",
            "GRAVE", "LBRACKET", "BACKSLASH", "RBRACKET", "QUOTE", "LEFT", "UP", "RIGHT", "DOWN", "F1", "F2", "F3", "F4",
            "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "BACKSPACE", "TAB", "ENTER",
            "CAPS", "ESC", "SPACE", "PREV", "NEXT", "END", "HOME", "PRNTSCR", "INS", "DEL", "NUMLOCK", "SCROLL", "LSHIFT",
            "RSHIFT", "LCONTROL", "RCONTROL", "LMENU", "RMENU"};
    private static final byte[][] KEY_NAMES = new byte[KEY_NAME_TEXT.length][];

    static {
        for (int i = 0; i < KEY_NAME_TEXT.length; ++i) {
            KEY_NAMES[i] = b(KEY_NAME_TEXT[i]);
        }
    }

    public static byte[] b(String text) {
        return text == null ? null : text.getBytes(StandardCharsets.UTF_8);
    }

    public static String s(byte[] text) {
        return text == null ? null : new String(text, StandardCharsets.UTF_8);
    }

    public static byte[] keyName(int key) {
        int index = key - 1;
        return Integer.compareUnsigned(index, 103) < 0 ? KEY_NAMES[index] : UNKNOWN_KEY;
    }
}

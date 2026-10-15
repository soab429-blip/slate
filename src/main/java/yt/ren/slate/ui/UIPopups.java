package yt.ren.slate.ui;

import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.util.Clock;
import yt.ren.slate.util.math.Floats;

import java.util.ArrayList;
import java.util.List;

public class UIPopups implements UICallbacks.PopupActions {
    public static class ColorHistory {
        public long head;
        public final float[][] colors = new float[8][3];
    }

    static class Suggestions {
        final List<long[]> index = new ArrayList<>();
        final List<byte[]> text = new ArrayList<>();

        int count() {
            return index.size();
        }
    }

    private final ColorHistory history;
    private final Clock clock;

    public UIPopups(ColorHistory history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    private static boolean equalColor(float[] a, float[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    static float[] color(UIState s, int offset) {
        return new float[]{s.f(offset), s.f(offset + 4), s.f(offset + 8)};
    }

    static void color(UIState s, int offset, float[] value) {
        s.f(offset, value[0]);
        s.f(offset + 4, value[1]);
        s.f(offset + 8, value[2]);
    }

    private static float[] popupHsl(float[] c) {
        float red = c[0];
        float green = c[1];
        float blue = c[2];
        float maxGb = Float.isNaN(green) ? blue : Floats.max(blue, green);
        float maxAll = Float.isNaN(red) ? maxGb : Floats.max(maxGb, red);
        float minAll = blue;
        if (green == green) {
            minAll = Floats.min(minAll, green);
        }
        if (red == red) {
            minAll = Floats.min(minAll, red);
        }
        float maximum = maxAll;
        float minimum = minAll;
        float sum = maximum + minimum;
        float light = 0.5f * sum;
        float saturation = 0.0f;
        float hue = 0.0f;
        if (maximum != minimum) {
            float delta = maximum - minimum;
            float alternate = (2.0f - maximum) - minimum;
            float denominator = light > 0.5f ? alternate : sum;
            if (maximum == c[0]) {
                hue = (c[1] < c[2] ? 6.0f : 0.0f) + (c[1] - c[2]) / delta;
            } else if (maximum == c[1]) {
                hue = (c[2] - c[0]) / delta + 2.0f;
            } else if (maximum == c[2]) {
                hue = (c[0] - c[1]) / delta + 4.0f;
            } else {
                hue = light;
            }
            saturation = delta / denominator;
            hue = hue / 6.0f;
        }
        return new float[]{hue, saturation, light};
    }

    private static void begin(UIState s, int type, Object owner, byte[] title, double now) {
        s.clear(UIState.POPUP_TYPE, 0xa0);
        s.i(UIState.POPUP_TYPE, type);
        s.ref(UIState.POPUP_OWNER, owner);
        s.ref(UIState.POPUP_TITLE, title);
        s.d(UIState.POPUP_LAST_UPDATE, now);
    }

    public void finish(UIState s, int notify) {
        s.captureMode(0);
        switch (s.popupType()) {
            case 0:
                return;
            case 1:
                if (notify != 0) {
                    ((UICallbacks.PopupText) s.ref(0x428)).accept(s.ref(UIState.POPUP_OWNER), null);
                }
                break;
            case 2: {
                if (notify != 0) {
                    float[] color = color(s, 0x430);
                    ((UICallbacks.PopupColor) s.ref(0x428)).accept(s.ref(UIState.POPUP_OWNER), color);
                }
                float[] color = color(s, 0x430);
                long head = history.head;
                boolean found = false;
                for (long i = 0; i < 8; ++i) {
                    if (equalColor(color, history.colors[(int) ((head + i) & 7)])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    long index = head != 0 ? head - 1 : 7;
                    history.head = index;
                    history.colors[(int) index] = color;
                }
                break;
            }
            case 4: {
                if (notify != 0) {
                    ((UICallbacks.PopupSuggestion) s.ref(0x430)).accept(s.ref(UIState.POPUP_OWNER), 0, null);
                }
                if (s.ref(0x450) != null) {
                    s.ref(0x450, null);
                }
                break;
            }
            default:
                break;
        }
        s.i(UIState.POPUP_TYPE, 0);
    }

    @Override
    public void closePopup(UIState s) {
        finish(s, 1);
    }

    public void openText(UIState s, byte[] title, Object owner, UICallbacks.CaptureCharacter character,
                         UICallbacks.PopupText callback) {
        finish(s, 1);
        double now = clock.now();
        begin(s, 1, owner, title, now);
        s.ref(0x428, callback);
        s.ref(0x430, character);
        UIController.captureText(s, character, s.popupKey);
    }

    public void openColor(UIState s, byte[] title, Object owner, float[] color, UICallbacks.PopupColor callback) {
        finish(s, 1);
        double now = clock.now();
        float[] saved = color.clone();
        float[] hsl = popupHsl(color);
        begin(s, 2, owner, title, now);
        s.ref(0x428, callback);
        color(s, 0x430, saved);
        color(s, 0x43c, hsl);
    }

    public void openConfirmation(UIState s, Object owner, UICallbacks.PopupConfirm callback, String format,
                                 Object... arguments) {
        finish(s, 1);
        double now = clock.now();
        begin(s, 3, owner, UIText.b("Are you sure?"), now);
        s.ref(0x428, callback);
        byte[] message = new byte[100];
        new ByteTextBuffer(message, 100).appendFormat(format, arguments);
        for (int k = 0; k < 100; ++k) {
            s.b(0x430 + k, message[k]);
        }
    }

    public void openSuggestions(UIState s, byte[] title, Object owner, UICallbacks.CaptureCharacter character,
                                UICallbacks.PopupSuggestion callback, byte[][] suggestions, long count) {
        finish(s, 1);
        double now = clock.now();
        begin(s, 4, owner, title, now);
        s.ref(0x428, character);
        s.ref(0x430, callback);
        s.ref(0x438, suggestions);
        s.l(0x440, count);
        UIController.captureText(s, character, s.popupKey);
        UIController.allowCaptureMouse(s);
    }

    private static int lower(int c) {
        return Integer.compareUnsigned((c - 'A') & 0xff, 26) < 0 ? c | 32 : c;
    }

    void refreshSuggestions(UIState s, byte[] filter, double now) {
        if (0.25 > now - s.d(0x448)) {
            return;
        }
        Suggestions matches = (Suggestions) s.ref(0x450);
        if (matches == null) {
            matches = new Suggestions();
            s.ref(0x450, matches);
        }
        matches.index.clear();
        matches.text.clear();
        s.l(0x458, 0);
        byte[][] source = (byte[][]) s.ref(0x438);
        for (long i = 0; i < s.l(0x440); ++i) {
            byte[] text = source[(int) i];
            int length = text.length;
            int queryLength = filter.length;
            if (queryLength > length) {
                continue;
            }
            boolean found = queryLength == 0;
            for (int offset = 0; !found && offset <= length - queryLength; ++offset) {
                int j = 0;
                while (j < queryLength && lower(text[offset + j] & 0xff) == lower(filter[j] & 0xff)) {
                    ++j;
                }
                found = j == queryLength;
            }
            if (!found) {
                continue;
            }
            if (matches.count() >= 30) {
                s.l(0x458, s.l(0x458) + 1);
            } else {
                matches.index.add(new long[]{i});
                matches.text.add(text);
            }
        }
        s.d(0x448, now);
    }

    public static void appendNumeric(UIState s, int offset, int character, Object context) {
        int length = s.strlen(offset);
        if (length != 100 && (Integer.compareUnsigned((character - '0') & 0xff, 10) < 0
                || Integer.compareUnsigned((character - ',') & 0xff, 3) < 0)) {
            s.b(offset + length, character);
        }
    }

    public static int clicked(UIState s) {
        return s.b(0x420);
    }

    public ColorHistory history() {
        return history;
    }
}

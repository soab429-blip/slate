package yt.ren.slate.ui;

import yt.ren.slate.input.ClientInputEvent;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Sse;

import java.nio.ByteBuffer;

public class UIController {
    public static boolean popupOpen(UIState s) {
        return s.popupType() != 0;
    }

    public static void captureText(UIState s, UICallbacks.CaptureCharacter character, Object context) {
        if ((s.captureMode() & ~1) == 2 && s.ref(UIState.CHARACTER_CONTEXT) == context) {
            return;
        }
        s.captureMode(2);
        s.clear(UIState.CAPTURE, UIState.CAPTURE_SIZE);
        s.ref(UIState.APPEND_CHARACTER, character);
        s.ref(UIState.CHARACTER_CONTEXT, context);
        s.clear(UIState.SUBMITTED, 8);
    }

    public static boolean textCaptured(UIState s, Object context) {
        return (s.captureMode() & ~1) == 2 && s.ref(UIState.CHARACTER_CONTEXT) == context;
    }

    public static void allowCaptureMouse(UIState s) {
        if (s.captureMode() == 2) {
            s.captureMode(3);
        }
    }

    public static void captureBinding(UIState s, UICallbacks.CaptureBinding callback, Object argument) {
        s.captureMode(1);
        s.ref(UIState.CAPTURE, callback);
        s.ref(UIState.CAPTURE + 8, argument);
    }

    public static boolean bindingCaptured(UIState s, Object argument) {
        if (s.captureMode() != 1) {
            return false;
        }
        return s.ref(UIState.CAPTURE + 8) == argument;
    }

    public static byte[] submittedText(UIState s) {
        if ((s.captureMode() & ~1) != 2 || s.b(UIState.SUBMITTED) != 1) {
            return null;
        }
        s.captureMode(0);
        s.b(UIState.SUBMITTED, 0);
        return s.string(UIState.CAPTURE, UIState.CAPTURE_SIZE);
    }

    public static byte[] capturedText(UIState s) {
        return (s.captureMode() & ~1) == 2 ? s.string(UIState.CAPTURE, UIState.CAPTURE_SIZE) : null;
    }

    public static boolean rectContains(UIRect r, float x, float y) {
        return r.x <= x && r.y <= y && x < r.x + r.width && y < r.y + r.height;
    }

    public static boolean hovered(UIState s, UIRect rect) {
        if (popupOpen(s) && s.b(0x420) == 0) {
            return false;
        }
        return rectContains(rect, s.hitX(), s.hitY()) && (s.captureMode() == 3 || s.captureMode() == 0);
    }

    public static boolean clicked(UIState s, UIRect rect) {
        if (!hovered(s, rect) || s.leftButton() != 1 || s.b(UIState.LEFT_CONSUMED) != 0) {
            return false;
        }
        s.b(UIState.LEFT_CONSUMED, 1);
        return true;
    }

    public static void consumeClick(UIState s) {
        if (s.leftButton() == 1 && s.b(UIState.LEFT_CONSUMED) == 0) {
            s.b(UIState.LEFT_CONSUMED, 1);
        }
    }

    public static int consumeInput(UIState s, ClientInputEvent event, UICallbacks.PopupActions popup) {
        if (event.type == 3) {
            if (!popupOpen(s)) {
                s.scrollTarget(event.wheel() * -40.0f + s.scrollTarget());
            }
            return 0;
        }
        if (event.type == 1) {
            s.f(UIState.MOUSE_X, (float) event.key);
            s.f(UIState.MOUSE_Y, (float) event.mouseY());
            return 0;
        }
        if (event.type != 2) {
            return 0;
        }
        if (event.state == 1) {
            int mode = s.captureMode();
            if (Integer.compareUnsigned(mode - 2, 2) < 0) {
                if (event.key == 1) {
                    if (mode != 3) {
                        s.captureMode(0);
                        return 1;
                    }
                } else {
                    if (event.key == 0x53) {
                        int length = s.strlen(UIState.CAPTURE);
                        if (length != 0) {
                            s.b(UIState.CAPTURE + length - 1, 0);
                        }
                        return 1;
                    }
                    if (event.key == 0x55) {
                        if (s.b(UIState.CAPTURE) != 0) {
                            s.b(UIState.SUBMITTED, 1);
                        }
                        return 1;
                    }
                    if (event.key == 0x57) {
                        s.captureMode(0);
                        return 1;
                    }
                    UICallbacks.CaptureCharacter append = (UICallbacks.CaptureCharacter) s.ref(UIState.APPEND_CHARACTER);
                    for (int i = 0; i < event.characters.length; ++i) {
                        int character = event.characters[i] & 0xff;
                        if (character == 0) {
                            break;
                        }
                        append.append(s, UIState.CAPTURE, character, s.ref(UIState.CHARACTER_CONTEXT));
                    }
                    return 1;
                }
            } else if (mode == 1) {
                UICallbacks.CaptureBinding callback = (UICallbacks.CaptureBinding) s.ref(UIState.CAPTURE);
                Object argument = s.ref(UIState.CAPTURE + 8);
                callback.bind(argument, event.key == 1 || event.key == 0x57 ? 0 : event.key);
                s.captureMode(0);
                return 1;
            } else if (mode == 0 && popupOpen(s) && event.key == 0x57) {
                popup.closePopup(s);
                return 1;
            }
        }
        if (Integer.compareUnsigned(event.key - 100, 2) < 0) {
            s.b(UIState.SECONDARY_BUTTON, event.state);
        } else if (event.key == 1) {
            s.b(UIState.LEFT_BUTTON, event.state);
        }
        return 0;
    }

    static float unsignedFloat(long value) {
        if (value < 0) {
            long half = (value >>> 1) | (value & 1);
            float out = (float) half;
            return out + out;
        }
        return (float) value;
    }

    public static float roundCoordinate(float value) {
        int encoded = Float.floatToRawIntBits(value);
        int exponent = (encoded >>> 23) & 255;
        if (exponent >= 150) {
            return value;
        }
        float magnitude = Float.intBitsToFloat(encoded & 0x7fffffff);
        float displaced = 8388608.0f + magnitude;
        if (exponent <= 125) {
            return value * 0.0f;
        }
        displaced = (displaced + -8388608.0f) - magnitude;
        if (displaced > 0.5f) {
            magnitude = (magnitude + displaced) + -1.0f;
        } else {
            magnitude = magnitude + displaced;
            if (displaced <= -0.5f) {
                magnitude = magnitude + 1.0f;
            }
        }
        return encoded < 0 ? Floats.negate(magnitude) : magnitude;
    }

    public static UIRect inset(UIRect r, float padding) {
        return new UIRect(padding + r.x, padding + r.y, r.width - (padding + padding), r.height - (padding + padding));
    }

    public static UIRect round(UIRect r) {
        float y = r.y;
        float x = roundCoordinate(r.x);
        float roundedY = roundCoordinate(y);
        float width = roundCoordinate(r.width);
        float height = roundCoordinate(r.height);
        return new UIRect(x, roundedY, width, height);
    }

    public static float approach(float from, float to, float fraction) {
        return from + Floats.min(1.0f, fraction) * (to - from);
    }

    private static final int F_BOUNDS = 0x00;
    private static final int F_SPACING = 0x10;
    private static final int F_PADDING = 0x14;
    private static final int F_SCISSORED = 0x18;
    private static final int F_CURSOR_X = 0x1c;
    private static final int F_CURSOR_Y = 0x20;
    private static final int F_COLUMNS = 0x28;
    private static final int F_CELLS = 0x30;
    private static final int F_COUNT = 0x70;

    private static int frame(UIState s) {
        return (int) ((s.l(0x258) - 1) * 0x78);
    }

    private static UIRect cell(UIState s, int frame, long index) {
        int c = frame + F_CELLS + (int) (index & 3) * 16;
        return new UIRect(s.f(c), s.f(c + 4), s.f(c + 8), s.f(c + 12));
    }

    private static void cell(UIState s, int frame, long index, UIRect r) {
        int c = frame + F_CELLS + (int) (index & 3) * 16;
        s.f(c, r.x);
        s.f(c + 4, r.y);
        s.f(c + 8, r.width);
        s.f(c + 12, r.height);
    }

    private static void finishRow(UIState s, int f) {
        long count = s.l(f + F_COUNT);
        if (count == 0) {
            return;
        }
        long last = count - 1;
        long columns = s.l(f + F_COLUMNS);
        long index = last - Long.remainderUnsigned(last, columns);
        UIRect c = cell(s, f, last);
        float bottom = c.y + c.height;
        float result = -1.0f;
        do {
            result = Floats.max(bottom, result);
        } while (Long.compareUnsigned(index++, last) < 0);
        if (result != -1.0f) {
            s.f(f + F_CURSOR_Y, result + s.f(f + F_SPACING));
        }
    }

    public static void resetLayout(UIState s) {
        s.l(0x258, 0);
        s.i(0x260, 0);
    }

    public static void pushLayout(UIState s, UIRect rect, float spacing, float padding) {
        long depth = s.l(0x258);
        if (depth != 0) {
            int f = (int) ((depth - 1) * 0x78);
            UIRect parent = new UIRect(s.f(f), s.f(f + 4), s.f(f + 8), s.f(f + 12));
            if (rect.x < parent.x) {
                rect.x = parent.x;
            }
            if (rect.y < parent.y) {
                rect.y = parent.y;
            }
            float right = parent.x + parent.width;
            float bottom = parent.y + parent.height;
            if (right < rect.width + rect.x) {
                rect.width = right - rect.x;
                if (rect.width < 0.0f) {
                    rect.width = 0.0f;
                }
            }
            if (bottom < rect.height + rect.y) {
                rect.height = bottom - rect.y;
                if (rect.height < 0.0f) {
                    rect.height = 0.0f;
                }
            }
        }
        s.l(0x258, depth + 1);
        rect.set(inset(rect, padding));
        int f = (int) (depth * 0x78);
        s.clear(f, 0x78);
        s.f(f + F_BOUNDS, rect.x);
        s.f(f + F_BOUNDS + 4, rect.y);
        s.f(f + F_BOUNDS + 8, rect.width);
        s.f(f + F_BOUNDS + 12, rect.height);
        s.f(f + F_SPACING, spacing);
        s.f(f + F_PADDING, padding);
        s.f(f + F_CURSOR_X, rect.x);
        s.f(f + F_CURSOR_Y, rect.y);
    }

    public static void pushScissoredLayout(UIDrawing drawing, UIState s, UIRect rect, float spacing, float padding) {
        VectorContext vector = drawing.vector;
        UIRect local = rect.copy();
        pushLayout(s, local, spacing, padding);
        long depth = s.l(0x258);
        s.b((int) ((depth - 1) * 0x78) + F_SCISSORED, 1);
        rect.set(round(rect));
        vector.save();
        float shrink = Floats.negate(spacing) - spacing;
        vector.scissor(rect.x + spacing, rect.y + spacing, rect.width + shrink, shrink + rect.height);
    }

    public static float popLayout(UIDrawing drawing, UIState s) {
        int f = frame(s);
        finishRow(s, f);
        if (s.b(f + F_SCISSORED) == 1) {
            drawing.vector.restore();
        }
        float extent = (float) s.i(0x260);
        float cursorY = s.f(f + F_CURSOR_Y);
        if (extent < cursorY) {
            s.i(0x260, Sse.cvttss2si(cursorY));
        }
        s.l(0x258, s.l(0x258) - 1);
        float spacing = s.f(f + F_SPACING);
        return (spacing + spacing) + (cursorY - s.f(f + F_BOUNDS + 4));
    }

    public static void setColumns(UIState s, int columns) {
        int f = frame(s);
        finishRow(s, f);
        s.l(f + F_COLUMNS, columns == 0 ? 1 : (columns < 4 ? columns : 4));
        s.l(f + F_COUNT, 0);
    }

    public static void fitColumns(UIState s, float desiredWidth) {
        int f = frame(s);
        float width = s.f(f + F_BOUNDS + 8);
        float spacing = s.f(f + F_SPACING);
        finishRow(s, f);
        int count = Sse.cvttss2si((width + spacing) / (desiredWidth + spacing));
        s.l(f + F_COLUMNS, count == 0 ? 1 : (count < 4 ? count : 4));
        s.l(f + F_COUNT, 0);
    }

    public static UIRect nextCell(UIState s, float height) {
        int f = frame(s);
        long count = s.l(f + F_COUNT);
        long columns = s.l(f + F_COLUMNS);
        s.l(f + F_COUNT, count + 1);
        long remainder = Long.remainderUnsigned(count, columns);
        UIRect cell = cell(s, f, count);
        UIRect above = cell(s, f, count - columns);
        float spacing = s.f(f + F_SPACING);
        if (remainder == 0) {
            cell.x = s.f(f + F_CURSOR_X);
            if (Long.compareUnsigned(count, columns) < 0) {
                cell.y = s.f(f + F_CURSOR_Y);
                cell.width = (s.f(f + F_BOUNDS + 8) - unsignedFloat(columns - 1) * spacing) / unsignedFloat(columns);
            } else {
                cell.y = (above.y + above.height) + spacing;
                cell.width = above.width;
            }
        } else {
            UIRect previous = cell(s, f, count - 1);
            cell.x = (previous.x + previous.width) + spacing;
            cell.width = previous.width;
            cell.y = Long.compareUnsigned(count, columns) < 0 ? s.f(f + F_CURSOR_Y) : spacing + (above.y + above.height);
        }
        cell.height = height;
        cell(s, f, count, cell);
        return round(cell);
    }

    public static void backdrop(UIDrawing drawing, float fraction) {
        float opacity = 1.0f - CrtMath.powf(1.0f - fraction, 5.0f);
        VectorContext vector = drawing.vector;
        vector.setGlobalAlpha(opacity);
        UIColor color = UIDrawing.gray(Float.intBitsToFloat(0x3e40c0c1));
        vector.setFillColor(UIDrawing.replaceAlpha(color, 0.75f));
        drawing.rectangle(new UIRect(0.0f, 0.0f, drawing.width, drawing.height));
        vector.translate(0.0f, -50.0f);
        vector.translate(0.0f, opacity * 50.0f);
    }

    public static void requestUnload(Object state) {
        ((UIState) state).b(0x4cb, 1);
    }

    public static int unloadRequested(UIState s) {
        return s.b(0x4cb);
    }

    public static void open(UIState s, double now) {
        s.f(0x4b8, 0.0f);
        s.d(0x498, now);
    }

    public static void resetInput(UIState s) {
        s.captureMode(0);
        s.b(UIState.LEFT_BUTTON, 0);
    }

    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    public static void navigation(UIState s, UIDrawing drawing, float delta, UIPopups popups) {
        VectorContext initial = drawing.vector;
        UIRect row = nextCell(s, 42.0f);
        initial.setFillColor(UIDrawing.gray(bits(0x3da0a0a1)));
        drawing.roundedRectangle(row, 20.0f);
        float width = row.width / 7.0f;
        float deltaFraction = (float) ((double) delta / 0.05);
        float transition = approach(s.f(0x358), unsignedFloat(s.l(0x350)), deltaFraction);
        s.f(0x358, transition);
        UIRect selected = inset(new UIRect(row.x + transition * width, row.y, width, row.height), 6.0f);
        drawing.vector.setFillColor(UIDrawing.gray(bits(0x3e808081)));
        drawing.roundedRectangle(selected, 14.0f);
        initial.setFontSize(16.0f);
        for (long index = 0; index != 7; ++index) {
            int tab = 0x2e0 + (int) index * 16;
            UIRect cell = new UIRect(unsignedFloat(index) * (row.width / 7.0f) + row.x, row.y, row.width / 7.0f,
                    row.height);
            if (clicked(s, cell)) {
                if (s.i(tab) == 6) {
                    popups.openConfirmation(s, s, UIController::requestUnload, "Unload Slate?");
                } else {
                    s.l(0x350, index);
                    s.captureMode(0);
                }
            }
            cell = inset(cell, 6.0f);
            boolean isHovered = hovered(s, cell);
            UIColor color;
            if (s.i(tab) == 6) {
                color = isHovered ? UIDrawing.rgba(bits(0x3f6eeeef), bits(0x3eb0b0b1), bits(0x3eb0b0b1), 1.0f)
                        : UIDrawing.rgba(bits(0x3f5dddde), bits(0x3e8e8e8f), bits(0x3e8e8e8f), 1.0f);
            } else {
                float channel = isHovered ? 1.0f : bits(0x3f5dddde);
                color = UIDrawing.gray(channel);
            }
            drawing.vector.setFillColor(color);
            drawing.alignedText(cell, 1, 0, (byte[]) s.ref(tab + 8));
        }
    }

    public static boolean toggle(UIState s, UIDrawing drawing, UIRect rect, ByteBuffer storage, int base,
                                 int[] value) {
        float animation = storage.getFloat(base);
        double updated = storage.getDouble(base + 8);
        double now = drawing.now;
        float elapsed = (float) (now - updated);
        double duration = elapsed;
        if (duration > 0.1) {
            animation = (float) value[0];
        } else if (value[0] == 0) {
            if (0.0f < animation) {
                animation = Floats.max((float) (duration / -0.1 + (double) animation), 0.0f);
            }
        } else if (animation < 1.0f) {
            animation = Floats.min((float) (duration / 0.1 + (double) animation), 1.0f);
        }
        storage.putFloat(base, animation);
        storage.putDouble(base + 8, now);
        VectorContext vector = drawing.vector;
        float transition = UIDrawing.transitionCurve(animation);
        float channel = hovered(s, rect) ? bits(0x3ea0a0a1) : bits(0x3e808081);
        UIColor base0 = UIDrawing.gray(channel);
        UIColor accent = s.color(0x360);
        UIColor mixed = UIColors.interpolate(base0, accent, transition);
        vector.setFillColor(mixed);
        drawing.roundedRectangle(rect, 10.0f);
        boolean isClicked = clicked(s, rect);
        if (isClicked) {
            value[0] ^= 1;
        }
        rect.set(inset(rect, 6.0f));
        float height = rect.height;
        vector.setFillColor(UIDrawing.gray(bits(0x3da0a0a1)));
        vector.beginPath();
        float integral = (float) Sse.cvttss2si(height);
        float half = integral * 0.5f;
        float left = half + rect.x;
        float right = (rect.x + rect.width) - half;
        vector.circle(left + (right - left) * transition, half + rect.y, integral);
        vector.fill();
        return isClicked;
    }

    public static void heading(UIState s, UIDrawing drawing, byte[] label) {
        VectorContext vector = drawing.vector;
        UIRect row = nextCell(s, 19.0f);
        row.y = row.y + 6.0f;
        vector.setFillColor(UIDrawing.gray(bits(0x3f19999a)));
        vector.setFontSize(15.0f);
        drawing.alignedText(row, 0, 0, label);
    }

    public static void tooltip(UIState s, byte[] text, int x, int y) {
        s.b(0x268, 1);
        s.strncpy(0x26a, text, 100);
        s.f(0x2d0, (float) x);
        s.f(0x2d4, (float) y);
    }
}

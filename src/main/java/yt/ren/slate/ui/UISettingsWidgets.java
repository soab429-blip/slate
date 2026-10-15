package yt.ren.slate.ui;

import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.ListStorage;
import yt.ren.slate.setting.RangeSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.ui.vector.VectorPaint;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Sse;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class UISettingsWidgets extends SettingsWidgets {
    public static class Scratch {
        final byte[] scalar = new byte[64];
        final byte[] range = new byte[64];
    }

    private final UIState state;
    private final UIDrawing drawing;
    private final UIPopups popups;
    private final Scratch scratch;

    public UISettingsWidgets(UIState state, UIDrawing drawing, UIPopups popups, Scratch scratch) {
        this.state = state;
        this.drawing = drawing;
        this.popups = popups;
        this.scratch = scratch;
    }

    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    private static UIColor gray(float value) {
        return UIDrawing.gray(value);
    }

    private static float clampSlider(float value) {
        return value < 0.0f ? 0.0f : Floats.min(1.0f, value);
    }

    private static float lerp(float a, float b, float fraction) {
        return a + (b - a) * fraction;
    }

    private static float displayValue(float value, float scale) {
        float multiplied = scale * value;
        return scale != 0.0f ? multiplied : value;
    }

    private static void tooltip(UIState s, byte[] text, float x, float y) {
        UIController.tooltip(s, text, Sse.cvttss2si(x), Sse.cvttss2si(y));
    }

    private static byte[] terminated(byte[] buffer) {
        int length = 0;
        while (length < buffer.length && buffer[length] != 0) {
            ++length;
        }
        return Arrays.copyOf(buffer, length);
    }

    private static int at(byte[] text, int index) {
        return index < text.length ? text[index] : 0;
    }

    private static float parseSliderText(byte[] text) {
        int cursor = 0;
        boolean negative = at(text, 0) == '-';
        if (negative) {
            ++cursor;
        }
        int integral = 0;
        int digit = at(text, cursor) - 48;
        while (Integer.compareUnsigned(digit, 10) < 0) {
            integral = integral * 10 + digit;
            ++cursor;
            digit = at(text, cursor) - 48;
        }
        int sign = negative ? -1 : 1;
        if (((at(text, cursor) & 0xff) | 2) != 0x2e) {
            return (float) (integral * sign);
        }
        ++cursor;
        float fraction = 0.0f;
        float power = bits(0x3dcccccd);
        digit = at(text, cursor) - 48;
        while (Integer.compareUnsigned(digit, 10) < 0) {
            fraction = fraction + (float) digit * power;
            power = (float) ((double) power * 0.1);
            ++cursor;
            digit = at(text, cursor) - 48;
        }
        return ((float) integral + fraction) * (float) sign;
    }

    private static void drawCross(VectorContext vector, UIRect rect) {
        float inset = (float) ((double) rect.height * 0.3);
        float left = rect.x + inset;
        float top = rect.y + inset;
        float right = (rect.height + rect.x) - inset;
        float bottom = (rect.y + rect.height) - inset;
        vector.beginPath();
        vector.moveTo(left, top);
        vector.lineTo(right, bottom);
        vector.moveTo(right, top);
        vector.lineTo(left, bottom);
        vector.stroke();
    }

    static int slider(UIState s, UIRect bar, UIRect hit, float lower, float upper, float step, float displayScale,
                      ByteBuffer ui, int activeOffset, float[] value, int index, Object key) {
        if (UIController.clicked(s, hit)) {
            ui.put(activeOffset, (byte) 1);
            if (s.b(UIState.SECONDARY_BUTTON) == 1) {
                UIController.captureText(s, UIPopups::appendNumeric, key);
            }
        } else if (s.leftButton() == 0 && !UIController.textCaptured(s, key)) {
            ui.put(activeOffset, (byte) 0);
        }
        int result = ui.get(activeOffset) & 0xff;
        if (result != 1) {
            return result;
        }
        float next;
        if (!UIController.textCaptured(s, key)) {
            next = (upper - lower) * ((s.hitX() - bar.x) / bar.width) + lower;
        } else {
            byte[] text = UIController.submittedText(s);
            if (text == null) {
                return result;
            }
            next = parseSliderText(text);
            if (displayScale != 0.0f) {
                next = next / displayScale;
            }
        }
        if (step != 0.0f) {
            next = UIController.roundCoordinate(next / step) * step;
        }
        value[index] = next;
        if (next <= lower) {
            value[index] = lower;
        } else if (upper <= next) {
            value[index] = upper;
        }
        return result;
    }

    @Override
    public void heading(String label) {
        UIController.heading(state, drawing, UIText.b(label));
    }

    @Override
    protected boolean booleanWidget(BooleanSetting setting) {
        byte[] label = UIText.b(setting.label());
        VectorContext vector = drawing.vector;
        UIRect row = UIController.nextCell(state, 20.0f);
        vector.setFillColor(gray(bits(0x3f5dddde)));
        vector.setFontSize(15.0f);
        drawing.alignedText(row, 0, 0, label);
        UIRect toggle = new UIRect((row.x + row.width) + -40.0f, row.y, 40.0f, row.height);
        int[] value = {(setting.get() ? 1 : 0)};
        boolean changed = UIController.toggle(state, drawing, toggle, setting.uiState(), 0, value);
        setting.set((value[0]) != 0);
        return changed;
    }

    private byte[] formatScratch(byte[] target, String format, Object... arguments) {
        Arrays.fill(target, (byte) 0);
        new ByteTextBuffer(target, 64).appendFormat(format, arguments);
        return terminated(target);
    }

    @Override
    protected boolean scalarWidget(ScalarSetting setting) {
        byte[] label = UIText.b(setting.label());
        String format = setting.format();
        float minimumValue = setting.minimum();
        float maximumValue = setting.maximum();
        float step = setting.step();
        float scale = setting.displayScale();
        VectorContext vector = drawing.vector;
        UIRect row = UIController.nextCell(state, 20.0f);
        vector.setFillColor(gray(bits(0x3f5dddde)));
        vector.setFontSize(15.0f);
        drawing.alignedText(row, 0, 0, label);
        float x = (float) ((double) row.x + (double) row.width * 0.5);
        float y = (row.y + 10.0f) + -2.0f;
        float width = (float) ((double) row.width * 0.5 - 2.0);
        UIRect bar = new UIRect(x, y, width, 4.0f);
        vector.setFillColor(gray(UIController.hovered(state, row) ? bits(0x3ea0a0a1) : bits(0x3e808081)));
        drawing.roundedRectangle(bar, 2.0f);
        float target = clampSlider((setting.get() - minimumValue) / (maximumValue - minimumValue));
        ByteBuffer ui = setting.uiState();
        float elapsed = (float) (drawing.now - ui.getDouble(8));
        float fraction = (float) ((double) elapsed / 0.1);
        float animation = UIController.approach(ui.getFloat(4), target, fraction);
        ui.putFloat(4, animation);
        ui.putDouble(8, drawing.now);
        float handle = lerp(x, width + x, animation);
        vector.setFillColor(state.color(0x360));
        drawing.roundedRectangle(new UIRect(x, y, handle - x, 5.0f), 2.0f);
        vector.beginPath();
        vector.circle(handle, 2.0f + y, 6.0f);
        vector.fill();
        float[] value = {setting.get()};
        int changed = slider(state, bar, row, minimumValue, maximumValue, step, scale, ui, 0, value, 0, setting.valueKey());
        setting.set(value[0]);
        if (ui.get(0) == 0 && (!UIController.hovered(state, row) || state.leftButton() != 0)) {
            return changed != 0;
        }
        byte[] text = state.string(UIState.CAPTURE, UIState.CAPTURE_SIZE);
        if (!UIController.textCaptured(state, setting.valueKey())) {
            text = formatScratch(scratch.scalar, format != null ? format : "%0.2f",
                    (double) displayValue(setting.get(), scale));
        }
        tooltip(state, text, handle, y);
        return changed != 0;
    }

    @Override
    protected boolean rangeWidget(RangeSetting setting) {
        byte[] label = UIText.b(setting.label());
        String singleFormat = setting.singleFormat();
        String rangeFormat = setting.rangeFormat();
        float minimumValue = setting.minimum();
        float maximumValue = setting.maximum();
        float step = setting.step();
        float scale = setting.displayScale();
        VectorContext vector = drawing.vector;
        UIRect row = UIController.nextCell(state, 20.0f);
        vector.setFillColor(gray(bits(0x3f5dddde)));
        vector.setFontSize(15.0f);
        drawing.alignedText(row, 0, 0, label);
        float start = (float) ((double) row.x + (double) row.width * 0.5);
        float y = (row.y + 10.0f) + -2.0f;
        float width = (float) ((double) row.width * 0.5 - 2.0);
        UIRect bar = new UIRect(start, y, width, 4.0f);
        vector.setFillColor(gray(UIController.hovered(state, row) ? bits(0x3ea0a0a1) : bits(0x3e808081)));
        drawing.roundedRectangle(bar, 2.0f);
        float span = maximumValue - minimumValue;
        float lower = clampSlider((setting.low() - minimumValue) / span);
        float upper = clampSlider((setting.high() - minimumValue) / span);
        ByteBuffer ui = setting.uiState();
        float elapsed = (float) (drawing.now - ui.getDouble(16));
        float fraction = (float) ((double) elapsed / 0.1);
        ui.putFloat(4, UIController.approach(ui.getFloat(4), lower, fraction));
        ui.putFloat(8, UIController.approach(ui.getFloat(8), upper, fraction));
        ui.putDouble(16, drawing.now);
        float end = start + width;
        float left = lerp(start, end, ui.getFloat(4));
        float right = lerp(start, end, ui.getFloat(8));
        vector.setFillColor(state.color(0x360));
        drawing.roundedRectangle(new UIRect(left, y, right - left, 5.0f), 2.0f);
        vector.beginPath();
        vector.circle(left, 2.0f + y, 6.0f);
        vector.fill();
        vector.beginPath();
        vector.circle(right, 2.0f + y, 6.0f);
        vector.fill();
        float middle = (float) Sse.cvttsd2si((double) (left + right) * 0.5);
        float leftSpan = middle - start;
        UIRect lowerHit = new UIRect(-6.0f + start, row.y, leftSpan + 12.0f, row.height);
        UIRect upperHit = new UIRect(middle, row.y, (width - leftSpan) + 12.0f, row.height);
        byte[] text;
        float hintX = left;
        float[] bounds = {setting.low(), setting.high()};
        if (slider(state, bar, lowerHit, minimumValue, maximumValue, step, scale, ui, 0, bounds, 0,
                setting.valueKey()) != 0) {
            setting.set(bounds[0], bounds[1]);
            if (setting.low() > setting.high()) {
                setting.setHigh(setting.low());
            }
            if (UIController.textCaptured(state, setting.valueKey())) {
                text = state.string(UIState.CAPTURE, UIState.CAPTURE_SIZE);
            } else {
                text = formatScratch(scratch.scalar, singleFormat != null ? singleFormat : "%0.2f",
                        (double) displayValue(setting.low(), scale));
            }
        } else if (slider(state, bar, upperHit, minimumValue, maximumValue, step, scale, ui, 1, bounds, 1,
                setting.upperKey()) != 0) {
            setting.set(bounds[0], bounds[1]);
            if (setting.high() < setting.low()) {
                setting.setLow(setting.high());
            }
            hintX = right;
            if (UIController.textCaptured(state, setting.upperKey())) {
                text = state.string(UIState.CAPTURE, UIState.CAPTURE_SIZE);
            } else {
                text = formatScratch(scratch.scalar, singleFormat != null ? singleFormat : "%0.2f",
                        (double) displayValue(setting.high(), scale));
            }
        } else {
            if (!UIController.hovered(state, row)
                    || (state.leftButton() == 1 && ui.get(0) == 0 && ui.get(1) != 1)) {
                return false;
            }
            byte[] rangeText = formatScratch(scratch.range, rangeFormat != null ? rangeFormat : "%0.2f - %0.2f",
                    (double) displayValue(setting.low(), scale), (double) displayValue(setting.high(), scale));
            int hint = Sse.cvttsd2si((double) ((lowerHit.x + lowerHit.width) + upperHit.x) * 0.5);
            UIController.tooltip(state, rangeText, hint, Sse.cvttss2si(y));
            return false;
        }
        tooltip(state, text, hintX, y);
        return true;
    }

    static void appendChoiceCharacter(UIState s, int offset, int c, Object unused) {
        int length = s.strlen(offset);
        if (length < 100 && (Integer.compareUnsigned(((c & 0xdf) + 0xbf) & 0xff, 26) < 0 || c == 32
                || Integer.compareUnsigned((c - 48) & 0xff, 10) < 0)) {
            s.b(offset + length, c);
        }
    }

    static void selectChoice(Object selected, long index, byte[] text) {
        if (text != null) {
            ((ChoiceSetting) selected).select((int) index + 1);
        }
    }

    private static final byte[] SELECT_ITEM = UIText.b("Select item");

    @Override
    protected boolean choiceWidget(ChoiceSetting setting) {
        byte[] label = UIText.b(setting.label());
        String[] choices = setting.choices();
        int count = setting.count();
        int uiFlag = setting.uiFlag();
        VectorContext vector = drawing.vector;
        ByteBuffer ui = setting.uiState();
        int expanded = ui.get(0x14) & 0xff;
        float target = expanded != 0 ? (float) (count * 28) : 20.0f;
        float elapsed = (float) (drawing.now - ui.getDouble(8));
        float height = UIController.approach(ui.getFloat(0x10), target, (float) ((double) elapsed / 0.1));
        ui.putFloat(0x10, height);
        ui.putDouble(8, drawing.now);
        UIRect row = UIController.nextCell(state, height);
        vector.setFillColor(gray(bits(0x3f5dddde)));
        vector.setFontSize(15.0f);
        drawing.alignedText(new UIRect(row.x, row.y, row.width, 20.0f), 0, 0, label);
        UIRect box = new UIRect((float) ((double) row.width * 0.5 + (double) row.x), row.y, row.width * 0.5f,
                row.height);
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        drawing.strokeRoundedRectangle(box, 10.0f);
        if (ui.get(0x14) == 0) {
            boolean isHovered = UIController.hovered(state, box);
            if (uiFlag == 1 && count > 5 && setting.index() != 0) {
                UIRect clear = new UIRect((box.width - box.height) + box.x, box.y, box.height, box.height);
                boolean clearHover = UIController.hovered(state, clear);
                vector.setStrokeColor(clearHover
                        ? UIDrawing.rgba(bits(0x3f6eeeef), bits(0x3eb0b0b1), bits(0x3eb0b0b1), 1.0f)
                        : gray(bits(0x3e808081)));
                drawCross(vector, clear);
                if (UIController.clicked(state, clear)) {
                    setting.select(0);
                }
                isHovered = isHovered && !clearHover;
            }
            float channel;
            if (uiFlag == 0 || setting.index() != 0) {
                channel = isHovered ? 1.0f : bits(0x3f5dddde);
            } else {
                channel = isHovered ? bits(0x3f3bbbbc) : bits(0x3f19999a);
            }
            vector.setFillColor(gray(channel));
            drawing.alignedText(box, 1, 0, UIText.b(choices[setting.index()]));
            if (!UIController.clicked(state, box)) {
                return false;
            }
            if (count < 6) {
                ui.put(0x14, (byte) 1);
            } else {
                byte[][] items = new byte[count - 1][];
                for (int i = 1; i < count; ++i) {
                    items[i - 1] = UIText.b(choices[i]);
                }
                popups.openSuggestions(state, SELECT_ITEM, setting, UISettingsWidgets::appendChoiceCharacter,
                        UISettingsWidgets::selectChoice, items, count - 1);
            }
            return false;
        }
        UIColor outline = gray(bits(0x3e808081));
        vector.setStrokeColor(UIDrawing.replaceAlpha(outline, 0.5f));
        if (count == 0) {
            return false;
        }
        float part = box.height / (float) Integer.toUnsignedLong(count);
        boolean changed = false;
        int flag = (uiFlag ^ 1) & 0xff;
        for (int index = 0; index < count; ++index) {
            UIRect item = new UIRect(box.x, (float) Integer.toUnsignedLong(index) * part + box.y, box.width, part);
            byte[] itemLabel = UIText.b(choices[index]);
            boolean isHovered = UIController.hovered(state, item);
            UIColor color;
            if (index == setting.index()) {
                color = state.color(0x360);
            } else if (!isHovered) {
                color = gray(index == 0 && flag == 0 ? bits(0x3f19999a) : bits(0x3f5dddde));
            } else {
                color = gray(index == 0 || flag != 0 ? 1.0f : bits(0x3f3bbbbc));
            }
            vector.setFillColor(color);
            drawing.alignedText(item, 1, 0, itemLabel);
            if (!changed && UIController.clicked(state, item)) {
                setting.select(index);
                ui.put(0x14, (byte) 0);
                changed = true;
            }
            if (index != 0) {
                vector.beginPath();
                vector.moveTo(item.x, item.y);
                vector.lineTo(item.x + item.width, item.y);
                vector.stroke();
            }
        }
        return changed;
    }

    static void setSelectedColor(Object output, float[] color) {
        float[] result = (float[]) output;
        result[2] = color[2];
        result[0] = color[0];
        result[1] = color[1];
    }

    @Override
    protected boolean colorWidget(ColorSetting setting) {
        byte[] label = UIText.b(setting.label());
        VectorContext vector = drawing.vector;
        UIRect row = UIController.nextCell(state, 20.0f);
        vector.setFillColor(gray(bits(0x3f5dddde)));
        vector.setFontSize(15.0f);
        drawing.alignedText(new UIRect(row.x, row.y, row.width, 20.0f), 0, 0, label);
        UIRect swatch = new UIRect((row.x + row.width) + -40.0f, row.y, 40.0f, 20.0f);
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        drawing.strokeRoundedRectangle(swatch, 10.0f);
        UIColor first = UIDrawing.rgb(setting.channel(0), setting.channel(1), setting.channel(2));
        UIColor second = UIDrawing.rgba(setting.channel(0), setting.channel(1), setting.channel(2), 0.25f);
        VectorPaint gradient = VectorContext.linearGradient(swatch.x, swatch.y, swatch.width + swatch.x, swatch.y, first,
                second);
        vector.setFillPaint(gradient);
        drawing.roundedRectangle(swatch, 10.0f);
        UIRect target = new UIRect((float) ((double) row.width * 0.5 + (double) row.x), row.y, row.width * 0.5f,
                row.height);
        boolean isClicked = UIController.clicked(state, target);
        if (isClicked) {
            float[] saved = setting.liveChannels().clone();
            popups.openColor(state, label, setting.liveChannels(), saved, UISettingsWidgets::setSelectedColor);
        }
        return isClicked;
    }

    private static final float[] RED = {1.0f, 0.0f, 0.0f};
    private static final byte[] CLICK_TO_ADD = UIText.b("Click to add");

    static void insertListText(Object pointer, byte[] text) {
        if (text != null) {
            ListSetting setting = (ListSetting) pointer;
            setting.storage().insert(UIText.s(text), RED.clone());
        }
    }

    static void listCharacter(ListStorage storage, UIState s, int offset, int character) {
        int length = s.strlen(offset);
        StringBuilder text = new StringBuilder(length);
        for (int k = 0; k < length; ++k) {
            text.append((char) s.b(offset + k));
        }
        storage.inputCharacter(text, character);
        if (text.length() > length) {
            s.b(offset + length, text.charAt(length));
        }
    }

    @Override
    protected boolean listWidget(ListSetting setting, String[] suggestions) {
        byte[] label = UIText.b(setting.label());
        byte[] inputLabel = UIText.b(setting.inputLabel());
        ListStorage storage = setting.storage();
        boolean character = storage.acceptsInput();
        int elementKind = setting.elementKind();
        VectorContext vector = drawing.vector;
        long count = storage.count();
        long rows = count + 1;
        UIRect row = UIController.nextCell(state, UIController.unsignedFloat(count + rows * 27 + 1));
        vector.setFillColor(gray(bits(0x3f5dddde)));
        vector.setFontSize(15.0f);
        drawing.alignedText(new UIRect(row.x, row.y, row.width, 20.0f), 0, 0, label);
        UIRect box = new UIRect((float) ((double) row.width * 0.5 + (double) row.x), row.y, row.width * 0.5f,
                row.height);
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        drawing.strokeRoundedRectangle(box, 10.0f);
        float height = box.height / UIController.unsignedFloat(rows);
        boolean changed = false;
        long remove = -1;
        for (long index = 0; index < count; ++index) {
            UIRect item = new UIRect(box.x, UIController.unsignedFloat(index) * height + box.y, box.width, height);
            byte[] name = UIText.b(storage.name(index));
            int length = name.length;
            int clipped = length > 19 ? 20 : length;
            vector.setFillColor(gray(bits(0x3f5dddde)));
            UIRect textRect = item.copy();
            drawing.textRange(textRect, 1, 0, name, clipped);
            if (elementKind == 1) {
                UIRect swatch = UIController.inset(new UIRect(item.x, item.y, item.height, item.height), 4.0f);
                float[] color = storage.color(index);
                vector.setFillColor(UIDrawing.rgb(color[0], color[1], color[2]));
                drawing.roundedRectangle(swatch, 5.0f);
                if (UIController.clicked(state, swatch)) {
                    float[] saved = color.clone();
                    popups.openColor(state, name, color, saved, UISettingsWidgets::setSelectedColor);
                    changed = true;
                }
            }
            UIRect clear = new UIRect((item.width - item.height) + item.x, item.y, item.height, item.height);
            boolean isHovered = UIController.hovered(state, clear);
            vector.setStrokeColor(isHovered
                    ? UIDrawing.rgba(bits(0x3f6eeeef), bits(0x3eb0b0b1), bits(0x3eb0b0b1), 1.0f)
                    : gray(bits(0x3e808081)));
            drawCross(vector, clear);
            if (UIController.clicked(state, clear)) {
                remove = index;
            }
            UIColor line = gray(bits(0x3e808081));
            vector.setStrokeColor(UIDrawing.replaceAlpha(line, 0.5f));
            vector.beginPath();
            vector.moveTo(item.x, item.y + item.height);
            vector.lineTo(item.x + item.width, item.y + item.height);
            vector.stroke();
        }
        if (remove != -1) {
            storage.remove(remove);
            changed = true;
        }
        UIRect append = new UIRect(box.x, UIController.unsignedFloat(count) * height + box.y, box.width, height);
        vector.setFillColor(gray(UIController.hovered(state, append) ? bits(0x3f3bbbbc) : bits(0x3f19999a)));
        drawing.alignedText(append, 1, 0, CLICK_TO_ADD);
        if (UIController.clicked(state, append)) {
            UICallbacks.CaptureCharacter callback = character
                    ? (st, offset, c, context) -> listCharacter(storage, st, offset, c) : null;
            if (suggestions == null) {
                popups.openText(state, inputLabel, setting, callback, UISettingsWidgets::insertListText);
            } else {
                byte[][] items = new byte[suggestions.length][];
                for (int i = 0; i < suggestions.length; ++i) {
                    items[i] = UIText.b(suggestions[i]);
                }
                popups.openSuggestions(state, inputLabel, setting, callback, UISettingsWidgets::insertListSuggestion,
                        items, items.length);
            }
            changed = true;
        }
        return changed;
    }

    static void insertListSuggestion(Object pointer, long index, byte[] text) {
        insertListText(pointer, text);
    }

    public static boolean button(UIState s, UIDrawing drawing, byte[] label, int enabled, UIRect rect) {
        VectorContext vector = drawing.vector;
        boolean isClicked = enabled != 0 && UIController.clicked(s, rect);
        vector.setFillColor(gray(bits(0x3e808081)));
        drawing.roundedRectangle(rect, 8.0f);
        vector.setFillColor(gray(enabled != 0 ? bits(0x3f5dddde) : bits(0x3f19999a)));
        vector.setFontSize(15.0f);
        drawing.alignedText(rect, 1, 0, label);
        return isClicked;
    }
}

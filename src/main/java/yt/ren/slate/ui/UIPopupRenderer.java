package yt.ren.slate.ui;

import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.ui.vector.VectorPaint;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.Sse;

import java.util.Arrays;

public class UIPopupRenderer {
    private final UIPopups popups;
    private final byte[] hexText = new byte[7];

    public UIPopupRenderer(UIPopups popups) {
        this.popups = popups;
    }

    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    private static UIColor gray(float value) {
        return UIDrawing.gray(value);
    }

    private static void updateHsl(UIState s, boolean fromHistory) {
        float[] color = UIPopups.color(s, 0x430);
        float red = color[0];
        float green = color[1];
        float blue = color[2];
        float maxAll;
        float minAll;
        if (fromHistory) {
            maxAll = blue;
            if (color[1] == color[1]) {
                maxAll = Floats.max(maxAll, green);
            }
            if (color[0] == color[0]) {
                maxAll = Floats.max(maxAll, red);
            }
            minAll = Float.isNaN(green) ? blue : Floats.min(blue, green);
            minAll = Float.isNaN(red) ? minAll : Floats.min(minAll, red);
        } else {
            maxAll = Float.isNaN(green) ? blue : Floats.max(blue, green);
            maxAll = Float.isNaN(red) ? maxAll : Floats.max(maxAll, red);
            minAll = blue;
            if (color[1] == color[1]) {
                minAll = Floats.min(minAll, green);
            }
            if (color[0] == color[0]) {
                minAll = Floats.min(minAll, red);
            }
        }
        float sum = maxAll + minAll;
        float light = 0.5f * sum;
        float saturation = 0.0f;
        float hue = 0.0f;
        if (maxAll != minAll) {
            float delta = maxAll - minAll;
            float alternate = (2.0f - maxAll) - minAll;
            float denominator = light > 0.5f ? alternate : sum;
            if (maxAll == color[0]) {
                hue = (color[1] < color[2] ? 6.0f : 0.0f) + (color[1] - color[2]) / delta;
            } else if (maxAll == color[1]) {
                hue = (color[2] - color[0]) / delta + 2.0f;
            } else if (maxAll == color[2]) {
                hue = (color[0] - color[1]) / delta + 4.0f;
            } else {
                hue = light;
            }
            saturation = delta / denominator;
            hue = hue / 6.0f;
        }
        s.f(0x43c, hue);
        s.f(0x440, saturation);
        s.f(0x444, light);
    }

    private static int nibble(byte c) {
        return ((c & 0xff) + (c >= 'a' ? -87 : -48)) & 0xff;
    }

    private static float decodedHexPair(byte[] text, int offset) {
        int integer = (nibble(text[offset]) << 4) | nibble(text[offset + 1]);
        return (float) ((double) integer / 255.0);
    }

    private static int hexComponent(float value) {
        return (int) Sse.cvttss2si64(value) & 255;
    }

    static void popupTextEntry(UIState s, UIDrawing drawing, UIRect rect, int alignment) {
        UIRect saved = rect.copy();
        float end = drawing.alignedText(saved, alignment, 0, s.string(UIState.CAPTURE, UIState.CAPTURE_SIZE));
        UIRect cursor = new UIRect(end + 1.0f, rect.y, rect.width, rect.height);
        UIWidgets.blinkingLine(drawing, cursor, gray(bits(0x3f5dddde)), 8.0f, 1.0f);
    }

    static void appendHexCharacter(UIState s, int offset, int character, Object unused) {
        int length = s.strlen(offset);
        if (length >= 6) {
            return;
        }
        if (Integer.compareUnsigned((character - 'A') & 0xff, 6) < 0) {
            character |= 32;
        } else if (Integer.compareUnsigned((character - 'a') & 0xff, 6) >= 0
                && Integer.compareUnsigned((character - '0') & 0xff, 10) >= 0) {
            return;
        }
        s.b(offset + length, character);
    }

    static boolean colorSlider(UIState s, UIDrawing drawing, UIRect rect, int component, UIColor handle) {
        VectorContext vector = drawing.vector;
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        drawing.strokeRoundedRectangle(rect, 5.0f);
        UIRect saved = rect.copy();
        float value = s.f(0x43c + component * 4);
        UIColor handleColor = handle.copy();
        float centerX = (saved.width - 20.0f) * value + (saved.x + 10.0f);
        vector.beginPath();
        float centerY = (float) ((double) saved.height * 0.5 + (double) saved.y);
        vector.circle(centerX, centerY, 10.0f);
        vector.setFillColor(handleColor);
        vector.fill();
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        vector.stroke();
        int flag = 0x448 + component;
        if (UIController.clicked(s, rect)) {
            s.b(flag, 1);
        } else if (s.leftButton() == 0) {
            s.b(flag, 0);
            return false;
        } else if (s.b(flag) != 1) {
            return false;
        }
        float ratio = (s.hitX() - rect.x) / rect.width;
        float upper = Floats.min(1.0f, ratio);
        s.f(0x43c + component * 4, ratio < 0.0f ? 0.0f : upper);
        return true;
    }

    private static final byte[] CONTINUE = UIText.b("Continue");

    public void draw(UIState s, UIDrawing drawing) {
        s.b(0x420, 1);
        double elapsed = (drawing.now - s.d(UIState.POPUP_LAST_UPDATE)) / 0.3;
        float progress = (float) ((double) s.f(0x418) + elapsed);
        s.f(0x418, Floats.min(1.0f, progress));
        UIController.setColumns(s, 1);
        UIRect outer = UIController.nextCell(s, s.f(0x41c));
        VectorContext vector = drawing.vector;
        vector.setFillColor(gray(bits(0x3da0a0a1)));
        drawing.roundedRectangle(outer, 20.0f);
        UIController.pushScissoredLayout(drawing, s, outer, 8.0f, 12.0f);
        UIController.setColumns(s, 1);
        UIRect title = UIController.nextCell(s, 20.0f);
        vector.setFontSize(16.0f);
        vector.setFillColor(gray(bits(0x3f5dddde)));
        drawing.alignedText(title, 0, 0, (byte[]) s.ref(UIState.POPUP_TITLE));
        UIRect close = new UIRect((title.width - title.height) + title.x, title.y, title.height, title.height);
        vector.setStrokeColor(UIController.hovered(s, close)
                ? UIDrawing.rgba(bits(0x3f6eeeef), bits(0x3eb0b0b1), bits(0x3eb0b0b1), 1.0f) : gray(bits(0x3f19999a)));
        vector.setStrokeWidth(2.0f);
        float inset = (float) ((double) close.height * 0.2);
        vector.beginPath();
        vector.moveTo(close.x + inset, close.y + inset);
        float right = (close.height + close.x) - inset;
        float bottom = (close.y + close.height) - inset;
        vector.lineTo(right, bottom);
        vector.moveTo(right, close.y + inset);
        vector.lineTo(close.x + inset, bottom);
        vector.stroke();
        boolean closeClicked = UIController.clicked(s, close);
        int status = 1;
        switch (s.popupType()) {
            case 1: {
                UIRect row = UIController.nextCell(s, 30.0f);
                vector.setFontSize(15.0f);
                vector.setStrokeColor(gray(bits(0x3e808081)));
                vector.setStrokeWidth(2.0f);
                drawing.strokeRoundedRectangle(row, 8.0f);
                if (!UIController.textCaptured(s, s.popupKey)) {
                    break;
                }
                row = UIController.inset(row, 4.0f);
                UIWidgets.textEntry(s, drawing, row);
                byte[] text = UIController.submittedText(s);
                if (text != null) {
                    ((UICallbacks.PopupText) s.ref(0x428)).accept(s.ref(UIState.POPUP_OWNER), text);
                    status = 2;
                } else {
                    status = 0;
                }
                break;
            }
            case 2:
                drawColor(s, drawing);
                status = 0;
                break;
            case 3: {
                UIRect message = UIController.nextCell(s, 30.0f);
                vector.setFontSize(15.0f);
                vector.setFillColor(gray(bits(0x3f5dddde)));
                drawing.alignedText(message, 0, 0, s.string(0x430, 100));
                UIRect button = UIController.nextCell(s, 30.0f);
                boolean isHovered = UIController.hovered(s, button);
                vector.setFillColor(isHovered
                        ? UIDrawing.rgba(bits(0x3f6eeeef), bits(0x3eb0b0b1), bits(0x3eb0b0b1), 1.0f)
                        : UIDrawing.rgba(bits(0x3f5dddde), bits(0x3e8e8e8f), bits(0x3e8e8e8f), 1.0f));
                drawing.roundedRectangle(button, 5.0f);
                vector.setFillColor(gray(1.0f));
                drawing.alignedText(button, 1, 0, CONTINUE);
                status = 0;
                if (UIController.clicked(s, button)) {
                    ((UICallbacks.PopupConfirm) s.ref(0x428)).confirm(s.ref(UIState.POPUP_OWNER));
                    status = 2;
                }
                break;
            }
            case 4:
                status = drawSuggestions(s, drawing);
                break;
            default:
                break;
        }
        s.f(0x41c, UIController.popLayout(drawing, s));
        s.b(0x420, 0);
        s.d(UIState.POPUP_LAST_UPDATE, drawing.now);
        if (status == 0 && !closeClicked) {
            return;
        }
        popups.finish(s, status == 1 || (status == 0 && closeClicked) ? 1 : 0);
    }

    private int drawSuggestions(UIState s, UIDrawing drawing) {
        UIRect row = UIController.nextCell(s, 30.0f);
        VectorContext vector = drawing.vector;
        vector.setFontSize(15.0f);
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        drawing.strokeRoundedRectangle(row, 8.0f);
        if (!UIController.textCaptured(s, s.popupKey)) {
            return 1;
        }
        row = UIController.inset(row, 4.0f);
        UIWidgets.textEntry(s, drawing, row);
        byte[] filter = UIController.capturedText(s);
        popups.refreshSuggestions(s, filter, drawing.now);
        UIPopups.Suggestions matches = (UIPopups.Suggestions) s.ref(0x450);
        int count = matches.count();
        if (count == 0) {
            return 0;
        }
        vector.setFontSize(16.0f);
        byte[] selectedText = null;
        long selectedIndex = 0;
        UIRect pair = new UIRect(0.0f, 0.0f, 0.0f, 0.0f);
        for (int i = 0; i < count; ++i) {
            if ((i & 1) == 0) {
                pair = UIController.nextCell(s, 25.0f);
            }
            float width = pair.width * 0.5f;
            UIRect cell = new UIRect((i & 1) != 0 ? pair.x + width : pair.x, pair.y, width, pair.height);
            vector.setFillColor(gray(UIController.hovered(s, cell) ? 1.0f : bits(0x3f5dddde)));
            byte[] text = matches.text.get(i);
            drawing.alignedText(cell, 0, 0, text);
            if (UIController.clicked(s, cell)) {
                selectedIndex = matches.index.get(i)[0];
                selectedText = text;
            }
        }
        long overflow = s.l(0x458);
        if (overflow != 0) {
            byte[] text = new byte[14];
            new ByteTextBuffer(text, 14).appendFormat("+%d more...",
                    (int) (Long.compareUnsigned(overflow, 9999) < 0 ? overflow : 9999));
            UIRect more = UIController.nextCell(s, 25.0f);
            vector.setFillColor(gray(bits(0x3f19999a)));
            vector.setFontSize(15.0f);
            drawing.alignedText(more, 0, 0, terminated(text));
        }
        if (selectedText == null) {
            return 0;
        }
        ((UICallbacks.PopupSuggestion) s.ref(0x430)).accept(s.ref(UIState.POPUP_OWNER), selectedIndex, selectedText);
        return 2;
    }

    private static byte[] terminated(byte[] buffer) {
        int length = 0;
        while (length < buffer.length && buffer[length] != 0) {
            ++length;
        }
        return Arrays.copyOf(buffer, length);
    }

    private static final byte[] HASH = UIText.b("#");

    private void drawColor(UIState s, UIDrawing drawing) {
        VectorContext vector = drawing.vector;
        UIRect row = UIController.nextCell(s, 24.0f);
        boolean isHovered = UIController.hovered(s, row);
        float[] rgb = UIPopups.color(s, 0x430);
        vector.setFillColor(UIDrawing.rgb(rgb[0], rgb[1], rgb[2]));
        drawing.roundedRectangle(new UIRect(row.x, row.y, row.height, row.height), 5.0f);
        vector.setStrokeColor(gray(bits(0x3e808081)));
        vector.setStrokeWidth(2.0f);
        vector.stroke();
        vector.setFontSize(20.0f);
        vector.setFillColor(gray(bits(0x3f19999a)));
        float hashX = (row.x + row.height) + 8.0f;
        float hashWidth = row.width - (row.x - hashX);
        float textX = drawing.alignedText(new UIRect(hashX, row.y, hashWidth, row.height), 0, 0, HASH);
        UIRect entry = new UIRect(textX, row.y, hashWidth - (hashX - textX), row.height);
        if (UIWidgets.clickTextEntry(s, row, UIPopupRenderer::appendHexCharacter, s.entryKey)) {
            vector.setFillColor(gray(1.0f));
            popupTextEntry(s, drawing, entry, 0);
            byte[] text = UIController.submittedText(s);
            if (text != null) {
                if (text.length >= 6) {
                    s.f(0x430, decodedHexPair(text, 0));
                    s.f(0x434, decodedHexPair(text, 2));
                    s.f(0x438, decodedHexPair(text, 4));
                }
                updateHsl(s, false);
            }
        } else {
            int red = hexComponent(s.f(0x430) * 255.0f);
            int green = hexComponent(s.f(0x434) * 255.0f);
            int blue = hexComponent(255.0f * s.f(0x438));
            Arrays.fill(hexText, (byte) 0);
            new ByteTextBuffer(hexText, 7).appendFormat("%02x%02x%02x", red, green, blue);
            vector.setFillColor(gray(isHovered ? bits(0x3f3bbbbc) : bits(0x3f19999a)));
            drawing.alignedText(entry, 0, 0, terminated(hexText));
        }
        UIController.heading(s, drawing, UIText.b("Hue"));
        UIRect hueBar = UIController.nextCell(s, 15.0f);
        float segment = hueBar.width / 6.0f;
        float width = ScalarRounding.ceilGameFloat(segment);
        for (long i = 0; i < 6; ++i) {
            float x = ScalarRounding.floorGameFloat((float) i * segment + hueBar.x);
            UIColor first = UIColors.hsl((float) i / 6.0f, s.f(0x440), s.f(0x444));
            UIColor second = UIColors.hsl((float) (i + 1) / 6.0f, s.f(0x440), s.f(0x444));
            VectorPaint paint = VectorContext.linearGradient(x, hueBar.y, segment + x, hueBar.y, first, second);
            vector.beginPath();
            if (i == 0) {
                vector.roundedRectVarying(x, hueBar.y, width, hueBar.height, 5.0f, 0.0f, 0.0f, 5.0f);
            } else if (i == 5) {
                vector.roundedRectVarying(x, hueBar.y, width, hueBar.height, 0.0f, 5.0f, 5.0f, 0.0f);
            } else {
                vector.rect(x, hueBar.y, width, hueBar.height);
            }
            vector.setFillPaint(paint);
            vector.fill();
        }
        UIColor handle = UIColors.hsl(s.f(0x43c), s.f(0x440), s.f(0x444));
        boolean changedHue = colorSlider(s, drawing, hueBar, 0, handle);
        UIController.heading(s, drawing, UIText.b("Saturation"));
        UIColor first = UIColors.hsl(s.f(0x43c), 0.0f, s.f(0x444));
        UIColor second = UIColors.hsl(s.f(0x43c), 1.0f, s.f(0x444));
        handle = UIColors.interpolate(first, second, s.f(0x440));
        UIRect bar = UIController.nextCell(s, 15.0f);
        VectorPaint paint = VectorContext.linearGradient(bar.x, bar.y, bar.width + bar.x, bar.height + bar.y, first, second);
        vector.setFillPaint(paint);
        drawing.roundedRectangle(bar, 5.0f);
        boolean changedSaturation = colorSlider(s, drawing, bar, 1, handle);
        UIController.heading(s, drawing, UIText.b("Lightness"));
        first = UIColors.hsl(s.f(0x43c), s.f(0x440), 0.0f);
        second = UIColors.hsl(s.f(0x43c), s.f(0x440), 1.0f);
        handle = UIColors.interpolate(first, second, s.f(0x444));
        bar = UIController.nextCell(s, 15.0f);
        paint = VectorContext.linearGradient(bar.x, bar.y, bar.width + bar.x, bar.height + bar.y, first, second);
        vector.setFillPaint(paint);
        drawing.roundedRectangle(bar, 5.0f);
        boolean changedLightness = colorSlider(s, drawing, bar, 2, handle);
        if (changedHue || changedSaturation || changedLightness) {
            UIColor color = UIColors.hsl(s.f(0x43c), s.f(0x440), s.f(0x444));
            s.f(0x430, color.red);
            s.f(0x434, color.green);
            s.f(0x438, color.blue);
        }
        UIController.heading(s, drawing, UIText.b("Recently used"));
        UIRect recent = UIController.nextCell(s, 25.0f);
        float x = recent.x;
        float swatchWidth = (recent.width - 56.0f) * 0.125f;
        vector.setStrokeWidth(2.0f);
        float step = 8.0f + swatchWidth;
        long chosen = 8;
        UIPopups.ColorHistory history = popups.history();
        for (long counter = -8; counter != 0; ++counter) {
            long candidate = history.head + counter + 8;
            long index = Long.compareUnsigned(candidate, 8) < 0 ? candidate : history.head + counter;
            UIRect swatch = new UIRect(x, recent.y, swatchWidth, recent.height);
            vector.setStrokeColor(gray(UIController.hovered(s, swatch) ? bits(0x3ea0a0a1) : bits(0x3e808081)));
            if (UIController.clicked(s, swatch)) {
                chosen = index;
            }
            vector.beginPath();
            vector.roundedRect(swatch.x, swatch.y, swatch.width, swatch.height, 5.0f);
            float[] color = history.colors[(int) index];
            vector.setFillColor(UIDrawing.rgb(color[0], color[1], color[2]));
            vector.fill();
            vector.stroke();
            x = x + step;
        }
        if (chosen != 8) {
            s.f(0x438, history.colors[(int) chosen][2]);
            s.f(0x430, history.colors[(int) chosen][0]);
            s.f(0x434, history.colors[(int) chosen][1]);
            updateHsl(s, true);
        }
    }
}

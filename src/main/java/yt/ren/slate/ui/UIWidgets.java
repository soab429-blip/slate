package yt.ren.slate.ui;

import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.module.Trait;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;

import java.nio.ByteBuffer;
import java.util.List;

public class UIWidgets {
    public static class BindingMetrics {
        public final float[] widths = new float[104];
    }

    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    private static UIColor gray(float value) {
        return UIDrawing.gray(value);
    }

    public static float moduleLabel(UIState s, UIDrawing drawing, Module module, UIRect rect, float fontSize,
                                    UIColor ordinary, UIColor hoveredColor, byte[] text) {
        VectorContext vector = drawing.vector;
        if (!module.hasLayout()) {
            vector.setFontSize(fontSize);
            vector.setFillColor(ordinary);
            return drawing.alignedText(rect, 0, 0, text);
        }
        UIRect button = new UIRect(rect.x, rect.y, rect.height, rect.height);
        float textX = (button.x + button.width) + 8.0f;
        float textWidth = vector.textBounds(textX, rect.y, text, 0, text.length, null);
        UIRect textRect = new UIRect(textX, rect.y, textWidth, rect.height);
        UIRect target = new UIRect(rect.x, rect.y, (button.width + textWidth) + 8.0f, rect.height);
        ByteBuffer g = module.uiState();
        if (UIController.clicked(s, target)) {
            g.put(0x18, (byte) ((g.get(0x18) & 0xff) ^ 1));
        }
        boolean isHovered = UIController.hovered(s, target);
        vector.setFontSize(fontSize);
        vector.setFillColor(isHovered ? hoveredColor : ordinary);
        drawing.alignedText(textRect, 0, 0, text);
        vector.setFillColor(gray(isHovered ? bits(0x3ea0a0a1) : bits(0x3e808081)));
        drawing.roundedRectangle(button, 5.0f);
        UIRect inner = UIController.inset(button, 6.0f);
        vector.setStrokeColor(gray(isHovered ? 1.0f : bits(0x3f5dddde)));
        vector.beginPath();
        vector.moveTo(inner.x, inner.height * 0.5f + inner.y);
        vector.lineTo(inner.x + inner.width, inner.height * 0.5f + inner.y);
        if (g.get(0x18) == 0) {
            vector.moveTo(inner.width * 0.5f + inner.x, inner.y);
            vector.lineTo(0.5f * inner.width + inner.x, inner.y + inner.height);
        }
        vector.setStrokeWidth(2.0f);
        vector.stroke();
        return target.x + target.width;
    }

    private static final byte[] PRESS_A_KEY = UIText.b("PRESS A KEY...");
    private static final byte[] HOLD = UIText.b("HOLD");

    public static void binding(UIState s, UIDrawing drawing, UICallbacks.CaptureBinding callback, Object owner, int key,
                               int[] hold, UIRect rect, BindingMetrics metrics) {
        VectorContext vector = drawing.vector;
        vector.setFontSize(12.0f);
        drawing.selectFont(1);
        boolean capturing = UIController.bindingCaptured(s, owner);
        float width;
        if (capturing) {
            width = ScalarRounding.ceilGameFloat(drawing.textBounds(0.0f, 0.0f, PRESS_A_KEY));
        } else {
            width = metrics.widths[key];
            if (width == 0.0f) {
                width = ScalarRounding.ceilGameFloat(drawing.textBounds(0.0f, 0.0f, UIText.keyName(key)));
                metrics.widths[key] = width;
            }
        }
        UIRect button = new UIRect(rect.x, rect.y, width + 12.0f, rect.height);
        UIColor background = key == 0 ? gray(bits(0x3f19999a)) : s.color(0x360);
        vector.setFillColor(UIDrawing.replaceAlpha(background, bits(0x3e19999a)));
        drawing.roundedRectangle(button, 5.0f);
        UIColor foreground = UIController.hovered(s, button) ? gray(1.0f)
                : (key == 0 ? gray(bits(0x3f19999a)) : s.color(0x360));
        vector.setFillColor(foreground);
        drawing.alignedText(button, 1, 1, capturing ? PRESS_A_KEY : UIText.keyName(key));
        if (UIController.clicked(s, button)) {
            UIController.captureBinding(s, callback, owner);
        }
        if (key == 0) {
            if (hold != null) {
                hold[0] = 0;
            }
            return;
        }
        if (hold == null) {
            return;
        }
        float holdWidth = ScalarRounding.ceilGameFloat(drawing.textBounds(0.0f, 0.0f, HOLD));
        UIRect holdButton = new UIRect((button.x + button.width) + 5.0f, button.y, holdWidth + 12.0f, button.height);
        boolean bright = true;
        if (hold[0] == 0) {
            if (!UIController.hovered(s, rect)) {
                return;
            }
            bright = hold[0] == 1;
        }
        background = gray(bright ? bits(0x3f5dddde) : bits(0x3f19999a));
        vector.setFillColor(UIDrawing.replaceAlpha(background, bits(0x3e19999a)));
        drawing.roundedRectangle(holdButton, 5.0f);
        foreground = gray(UIController.hovered(s, holdButton) ? bits(0x3f5dddde) : bits(0x3f19999a));
        vector.setFillColor(foreground);
        drawing.alignedText(holdButton, 1, 1, HOLD);
        if (UIController.clicked(s, holdButton)) {
            hold[0] ^= 1;
        }
    }

    public static boolean clickTextEntry(UIState s, UIRect rect, UICallbacks.CaptureCharacter character, Object owner) {
        if (UIController.clicked(s, rect)) {
            UIController.captureText(s, character, owner);
        }
        return UIController.textCaptured(s, owner);
    }

    public static void textEntry(UIState s, UIDrawing drawing, UIRect rect) {
        UIRect saved = rect.copy();
        float advance = drawing.alignedText(rect, 1, 0, s.string(UIState.CAPTURE, UIState.CAPTURE_SIZE));
        blinkingLine(drawing, new UIRect(advance + 1.0f, saved.y, saved.width, saved.height), gray(bits(0x3f5dddde)),
                8.0f, 1.0f);
    }

    public static void blinkingLine(UIDrawing drawing, UIRect rect, UIColor color, float frequency, float width) {
        VectorContext vector = drawing.vector;
        float value = ScalarTrig.sine((float) ((double) frequency * drawing.now));
        float alpha = 0.5f + value * 0.5f;
        vector.beginPath();
        UIColor copied = color.copy();
        vector.setStrokeColor(UIDrawing.replaceAlpha(copied, alpha));
        vector.setStrokeWidth(width);
        vector.moveTo(rect.x, rect.y);
        vector.lineTo(rect.x, rect.y + rect.height);
        vector.stroke();
    }

    public static void appendProfileCharacter(UIState s, int offset, int character, Object unused) {
        int length = s.strlen(offset);
        if (Integer.compareUnsigned((character - 0x20) & 0xff, 0x5f) < 0 && length < 15 && character != '/'
                && character != '\\') {
            s.b(offset + length, character);
        }
    }

    public static boolean overlap(UIRect a, UIRect b) {
        return a.x <= b.width + b.x && b.x <= a.x + a.width && a.y <= b.height + b.y && b.y <= a.y + a.height;
    }

    @FunctionalInterface
    public interface ModuleSettingsDraw {
        void draw(UIState s, UIDrawing drawing, Module module);
    }

    public static void moduleCards(UIState s, UIDrawing drawing, ModuleRegistry registry, int category, float delta,
                                   BindingMetrics metrics, ModuleSettingsDraw showSettings) {
        VectorContext vector = drawing.vector;
        UIController.fitColumns(s, 430.0f);
        List<Module> list = registry.categories.get(category);
        float transition = (float) ((double) delta / 0.05);
        for (int i = 0; i < list.size(); ++i) {
            Module module = list.get(i);
            ByteBuffer g = module.uiState();
            UIRect card = UIController.nextCell(s, g.getFloat(0x10));
            UIRect screen = new UIRect(0.0f, s.scroll(), drawing.width, drawing.height);
            if (!overlap(screen, card)) {
                continue;
            }
            vector.setFillColor(gray(bits(0x3da0a0a1)));
            drawing.roundedRectangle(card, 20.0f);
            UIRect clipped = card.copy();
            UIController.pushScissoredLayout(drawing, s, clipped, 8.0f, 12.0f);
            UIController.setColumns(s, 1);
            UIRect title = UIController.nextCell(s, 20.0f);
            float advance;
            byte[] name = UIText.b(module.name());
            if (s.b(0x4cc) == 1) {
                advance = moduleLabel(s, drawing, module, title, 16.0f, gray(bits(0x3f5dddde)), gray(1.0f), name);
            } else {
                vector.setFontSize(16.0f);
                vector.setFillColor(gray(bits(0x3f5dddde)));
                advance = drawing.alignedText(title, 0, 0, name);
            }
            if (!module.has(Trait.NO_KEYBIND)) {
                UIRect bindingRect = new UIRect(advance + 5.0f, title.y, (title.width - advance) + title.x, title.height);
                final Module target = module;
                int[] hold = module.has(Trait.NO_HOLD) ? null : new int[]{(module.holdToActivate() ? 1 : 0)};
                binding(s, drawing, (argument, key) -> registry.context.change((Module) argument, key), module,
                        module.keybind(), hold, bindingRect, metrics);
                if (hold != null) {
                    target.setHoldToActivate((hold[0]) != 0);
                }
            }
            if (!module.has(Trait.NO_TOGGLE)) {
                UIRect toggleRect = new UIRect((title.x + title.width) + -40.0f, title.y, 40.0f, title.height);
                int[] value = {(module.isEnabled() ? 1 : 0)};
                UIController.toggle(s, drawing, toggleRect, g, 0, value);
                if (value[0] != (module.isEnabled() ? 1 : 0)) {
                    registry.queueModuleChange(module, value[0]);
                }
            }
            if (s.b(0x4cc) == 0) {
                UIRect description = UIController.nextCell(s, 20.0f);
                moduleLabel(s, drawing, module, description, 14.0f, gray(bits(0x3f19999a)), gray(bits(0x3f3bbbbc)),
                        UIText.b(module.description()));
            }
            if (g.get(0x18) == 1 && module.hasLayout()) {
                showSettings.draw(s, drawing, module);
            }
            float height = UIController.popLayout(drawing, s);
            long signedHeight = Sse.cvttss2si64(height);
            long alternate = Sse.cvttss2si64(height - bits(0x5f000000));
            long encoded = signedHeight | (alternate & (signedHeight < 0 ? ~0L : 0L));
            float previous = g.getFloat(0x10);
            float desired = UIController.unsignedFloat(encoded);
            g.putFloat(0x10, previous == 0.0f ? desired : UIController.approach(previous, desired, transition));
        }
    }
}

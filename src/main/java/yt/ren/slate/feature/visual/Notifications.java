package yt.ren.slate.feature.visual;

import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.*;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.ui.UIDrawing;
import yt.ren.slate.ui.UIModule;
import yt.ren.slate.ui.UIRect;
import yt.ren.slate.ui.UIText;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.util.math.Floats;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class Notifications extends Module {
    private final BooleanSetting hideIngame = register("hide_ingame", new BooleanSetting("In-game", 0, 0));
    private final BooleanSetting hideHoldNotifs = register("hide_hold_notifs", new BooleanSetting("Hold bind is used", 1, 0));
    private final BooleanSetting hideBindOnly = register("hide_bind_only", new BooleanSetting("Module is bind-only", 1, 0));
    private final BooleanSetting showCombat = register("show_combat", new BooleanSetting("Combat", 1, 0));
    private final BooleanSetting showMove = register("show_move", new BooleanSetting("Move", 1, 0));
    private final BooleanSetting showBlock = register("show_block", new BooleanSetting("Block", 1, 0));
    private final BooleanSetting showVisual = register("show_visual", new BooleanSetting("Visual", 1, 0));
    private final BooleanSetting showUtility = register("show_utility", new BooleanSetting("Utility", 1, 0));

    private static class Batch {
        String name;
        int count;
    }

    private static class Entry {
        int kind;
        final byte[] text = new byte[101];
        float age;
    }

    private final ClientRuntime runtime;
    private UIModule gui;
    private final AtomicInteger hintShown = new AtomicInteger();
    private final Batch enabled = new Batch();
    private final Batch disabled = new Batch();
    private long count;
    private final Entry[] entries = new Entry[10];
    private long head;

    public Notifications(ClientRuntime runtime) {
        super(ModuleId.NOTIFICATIONS, "Notifications", "Informs you when certain actions are performed.", Category.VISUAL);
        traits(Trait.NO_KEYBIND, Trait.NO_TOGGLE, Trait.HIDDEN);
        this.runtime = runtime;
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new Entry();
        }
    }


    private Entry append(int kind) {
        long current = count;
        long first = head;
        Entry entry = entries[(int) Long.remainderUnsigned(current + first, 10)];
        entry.kind = kind;
        entry.age = 0.0f;
        if (current == 10) {
            head = first + 1;
        } else {
            count = current + 1;
        }
        return entry;
    }

    private static void format(Entry entry, String pattern, Object... arguments) {
        new ByteTextBuffer(entry.text, entry.text.length).appendFormat(pattern, arguments);
    }

    private void flushBatch(Batch batch, String action) {
        int total = batch.count;
        if (total == 0) {
            return;
        }
        Entry entry = append(1);
        if (total == 1) {
            format(entry, UIText.SINGLE_TOGGLE, action, batch.name);
        } else {
            format(entry, UIText.MULTIPLE_TOGGLES, action, total);
        }
        batch.count = 0;
    }

    private boolean visibleCategory(Module module) {
        switch (module.category()) {
            case COMBAT:
                return showCombat.get();
            case MOVEMENT:
                return showMove.get();
            case VISUAL:
                return showVisual.get();
            case BLOCK:
                return showBlock.get();
            case UTILITY:
                return showUtility.get();
            default:
                return true;
        }
    }

    private static float opacity(float age) {
        double widened = age;
        double numerator;
        if (widened < 0.2) {
            numerator = widened;
        } else if (widened > 4.8) {
            numerator = 5.0 - widened;
        } else {
            return 1.0f;
        }
        float fraction = (float) (numerator / 0.2);
        float value = UIDrawing.transitionCurve(fraction);
        return Floats.min(1.0f, value);
    }

    public void resetHint() {
        hintShown.set(0);
    }

    void toggle(ToggleEvent event, Module descriptor) {
        Module module = event.module;
        int state = event.state;
        if (module == descriptor) {
            if (state == 0) {
                count = 0;
                return;
            }
            if (!hintShown.compareAndSet(0, 1)) {
                return;
            }
            Entry entry = append(1);
            format(entry, UIText.GUI_HINT, UIText.s(UIText.keyName(gui.keybind())));
            return;
        }
        if (module.has(Trait.GLOBAL)) {
            return;
        }
        if (module.has(Trait.NO_TOGGLE) && hideBindOnly.get()) {
            return;
        }
        if (module.holdToActivate() && hideHoldNotifs.get()) {
            return;
        }
        if (!visibleCategory(module)) {
            return;
        }
        Batch batch = state != 0 ? enabled : disabled;
        batch.name = module.name();
        ++batch.count;
    }

    void prepare(UIPrepareEvent event) {
        if (hideIngame.get() && !gui.isEnabled()) {
            count = 0;
            enabled.count = 0;
            disabled.count = 0;
            return;
        }
        while (count != 0) {
            if (!(entries[(int) Long.remainderUnsigned(head, 10)].age > 5.0f)) {
                break;
            }
            ++head;
            --count;
        }
        flushBatch(enabled, UIText.ENABLED);
        flushBatch(disabled, UIText.DISABLED);
        long remaining = count;
        if (remaining == 0) {
            return;
        }
        int flags = event.result;
        event.result = flags | 1;
        float delta = event.elapsed;
        long index = head;
        boolean animation = false;
        do {
            Entry entry = entries[(int) Long.remainderUnsigned(index, 10)];
            entry.age = entry.age + delta;
            double age = entry.age;
            animation = animation | (age > 4.8 || age < 0.2);
            ++index;
            --remaining;
        } while (remaining != 0);
        if (animation) {
            event.result = flags | 3;
        }
    }

    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    private static byte[] terminated(byte[] text) {
        int length = 0;
        while (length < text.length && text[length] != 0) {
            ++length;
        }
        return Arrays.copyOf(text, length);
    }

    private static final byte[] BRAND = UIText.b(UIText.BRAND);

    void render(UIRenderEvent event) {
        if (count == 0 || (hideIngame.get() && !gui.isEnabled())) {
            return;
        }
        UIDrawing drawing = (UIDrawing) event.surface;
        VectorContext vector = drawing.vector;
        vector.setFontSize(14.0f);
        if (count != 0) {
            float y = 12.0f;
            long index = 0;
            do {
                Entry entry = entries[(int) Long.remainderUnsigned(head + index, 10)];
                float alpha = opacity(entry.age);
                vector.setGlobalAlpha(alpha);
                float x = (1.0f - alpha) * -100.0f + 12.0f;
                UIColor accent;
                if (entry.kind == 2) {
                    accent = UIDrawing.rgb(bits(0x3f5dddde), bits(0x3e8e8e8f), bits(0x3e8e8e8f));
                } else if (entry.kind == 1) {
                    float[] color = gui.accentColor();
                    accent = UIDrawing.rgb(color[0], color[1], color[2]);
                } else if (entry.kind == 0) {
                    accent = UIDrawing.rgb(bits(0x3f0c8c8d), bits(0x3f6ceced), bits(0x3e2cacad));
                } else {
                    accent = UIDrawing.rgb(bits(0x3f5dddde), bits(0x3f5dddde), bits(0x3f5dddde));
                }
                byte[] text = terminated(entry.text);
                float width = vector.textBounds(0.0f, 0.0f, text, 0, text.length, null);
                UIRect background = new UIRect(x, y, width + 102.0f, 42.0f);
                vector.setFillColor(UIDrawing.rgb(bits(0x3da0a0a1), bits(0x3da0a0a1), bits(0x3da0a0a1)));
                drawing.roundedRectangle(background, 20.0f);
                UIRect badge = new UIRect(background.x + 8.0f, background.y + 8.0f, 70.0f, background.height - 16.0f);
                UIColor faded = accent.copy();
                vector.setFillColor(UIDrawing.replaceAlpha(faded, bits(0x3e19999a)));
                drawing.roundedRectangle(badge, 10.0f);
                vector.setFillColor(accent);
                drawing.alignedText(badge, 1, 0, BRAND);
                UIRect textRect = new UIRect((badge.x + badge.width) + 8.0f, background.y,
                        (background.width - badge.width) - 16.0f, background.height);
                vector.setFillColor(UIDrawing.rgb(bits(0x3f5dddde), bits(0x3f5dddde), bits(0x3f5dddde)));
                drawing.alignedText(textRect, 0, 0, text);
                y = y + alpha * 50.0f;
                ++index;
            } while (index < count);
        }
        vector.setGlobalAlpha(1.0f);
    }

    void profile(ProfileEvent event) {
        int action = event.action;
        if (action == 2) {
            return;
        }
        String label = UIText.PROFILE_ACTIONS[action];
        String name = event.name != null ? new String(event.name, StandardCharsets.UTF_8) : UIText.DEFAULT_PROFILE;
        format(append(0), UIText.SINGLE_TOGGLE, label, name);
    }

    void error(ErrorEvent event) {
        Entry entry = append(2);
        if (event.message != null) {
            format(entry, UIText.ERROR_TEXT, event.message);
        } else {
            format(entry, UIText.ERROR_CODE, event.status);
        }
    }

    void friend(FriendChangeEvent event) {
        Entry entry = append(1);
        byte[] text = (event.added() ? "Friend added" : "Friend removed").getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(text, 0, entry.text, 0, text.length);
        entry.text[text.length] = 0;
    }

    @Override
    protected void setup() {
        gui = runtime.module(UIModule.class);
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.UI_PREPARE, e -> prepare(e));
        on(EventTypes.UI_RENDER, e -> render(e), -100);
        on(EventTypes.PROFILE, e -> profile(e));
        on(EventTypes.ERROR, e -> error(e));
        on(EventTypes.FRIEND_CHANGE, e -> friend(e));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Hide if");
        w.show(hideIngame);
        w.show(hideHoldNotifs);
        w.show(hideBindOnly);
        w.heading("Visible mod categories");
        w.show(showCombat);
        w.show(showMove);
        w.show(showBlock);
        w.show(showVisual);
        w.show(showUtility);
    }
}

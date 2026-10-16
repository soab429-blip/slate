package yt.ren.slate.feature.visual;

import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.BindingEvent;
import yt.ren.slate.event.Events.ProfileEvent;
import yt.ren.slate.event.Events.UIPrepareEvent;
import yt.ren.slate.event.Events.UIRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.ui.UIDrawing;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.QSort;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ModOverlay extends Module {
    private final BooleanSetting showCombat = register("show_combat", new BooleanSetting("Combat", 1, 0));
    private final BooleanSetting showMove = register("show_move", new BooleanSetting("Move", 1, 0));
    private final BooleanSetting showBlock = register("show_block", new BooleanSetting("Block", 1, 0));
    private final BooleanSetting showVisual = register("show_visual", new BooleanSetting("Visual", 1, 0));
    private final BooleanSetting showUtility = register("show_utility", new BooleanSetting("Utility", 1, 0));
    private final ScalarSetting textSize = register("text_size", new ScalarSetting("Text size", "%.0f px", 1.0f, 0.0f, 10.0f, 24.0f, 16.0f, 0.0f));
    private final ScalarSetting paddingX = register("padding_x", new ScalarSetting("Padding X", "%.0f px", 1.0f, 0.0f, 0.0f, 5.0f, 3.0f, 0.0f));
    private final ScalarSetting paddingY = register("padding_y", new ScalarSetting("Padding Y", "%.0f px", 1.0f, 0.0f, 0.0f, 5.0f, 1.0f, 0.0f));
    private final ScalarSetting bgRounding = register("bg_rounding", new ScalarSetting("Rounding", "%.0f px", 1.0f, 0.0f, 0.0f, 16.0f, 6.0f, 0.0f));
    private final ColorSetting nameColorSetting = register("name_color", new ColorSetting("Text color", 0.953f, 0.365f, 0.071f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting detailsColorSetting = register("details_color", new ColorSetting("Details color", 0.6f, 0.6f, 0.6f, 0.0f, 0.0f, 0.0f));
    private final BooleanSetting lowercase = register("lowercase", new BooleanSetting("Lowercase", 1, 0));
    private final BooleanSetting showDetails = register("show_details", new BooleanSetting("Show details", 1, 0));
    private final BooleanSetting requireBound = register("require_bound", new BooleanSetting("Only bound modules", 0, 0));

    private static class Entry {
        Module module;
        final byte[] name = new byte[20];
        final byte[] details = new byte[32];
        float width;
        float height;
        float animation;
        int visible;
    }

    private int filterMask;
    private int refresh;
    private int measure;
    private int count;
    private final Entry[] entries = new Entry[50];
    private float animationTime;

    public ModOverlay(ClientRuntime runtime) {
        super(ModuleId.MOD_OVERLAY, "Mod Overlay", "Displays a list of all active mods.", Category.VISUAL);
        traits(Trait.HIDDEN);
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new Entry();
        }
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

    private boolean eligible(Module module) {
        if (module.has(Trait.HIDDEN)) {
            return false;
        }
        if (requireBound.get() && module.keybind() == 0) {
            return false;
        }
        return visibleCategory(module);
    }

    private static int strlen(byte[] text) {
        int length = 0;
        while (length < text.length && text[length] != 0) {
            ++length;
        }
        return length;
    }

    private static byte lower(byte value) {
        return value >= 'A' && value <= 'Z' ? (byte) (value | 32) : value;
    }

    private static void lowercaseName(Entry entry, Module module) {
        byte[] name = module.name().getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < name.length; ++index) {
            entry.name[index] = lower(name[index]);
        }
        entry.name[name.length] = 0;
    }

    private static void formatName(Entry entry, String name) {
        Arrays.fill(entry.name, (byte) 0);
        new ByteTextBuffer(entry.name, entry.name.length).appendFormat("%s", name);
    }

    private static void lowercaseDetails(Entry entry) {
        int index = 0;
        do {
            entry.details[index] = lower(entry.details[index]);
            ++index;
        } while (index < strlen(entry.details));
    }

    private static void formatDetails(Entry entry, String suffix) {
        byte[] text = suffix.getBytes(StandardCharsets.UTF_8);
        Arrays.fill(entry.details, (byte) 0);
        System.arraycopy(text, 0, entry.details, 0, Math.min(text.length, 31));
    }

    private UIColor settingColor(ColorSetting setting) {
        float[] value = setting.liveChannels();
        return UIDrawing.rgb(value[0], value[1], value[2]);
    }

    private void append(Module module) {
        Entry entry = entries[count];
        entry.module = module;
        entry.animation = 0.0f;
        entry.visible = 1;
        Module current = module;
        if (lowercase.get()) {
            lowercaseName(entry, current);
        } else {
            formatName(entry, current.name());
            current = entry.module;
        }
        String suffix = current.suffix();
        if (suffix == null || !showDetails.get()) {
            entry.details[0] = 0;
        } else {
            formatDetails(entry, suffix);
            if (lowercase.get() && entry.details[0] != 0) {
                lowercaseDetails(entry);
            }
        }
        ++count;
    }

    private void refresh(ModuleRegistry registry) {
        if (count != 0) {
            int bound = (requireBound.get() ? 1 : 0);
            int[] categories = {(showCombat.get() ? 1 : 0), (showMove.get() ? 1 : 0),
                    (showVisual.get() ? 1 : 0), (showBlock.get() ? 1 : 0),
                    (showUtility.get() ? 1 : 0)};
            for (int i = 0; i < count; ++i) {
                Entry entry = entries[i];
                Module module = entry.module;
                if (module.has(Trait.HIDDEN) || (bound != 0 && module.keybind() == 0)
                        || categories[module.category().ordinal()] == 0) {
                    entry.visible = 0;
                }
            }
        }
        for (Module module : registry.all) {
            if (!module.isEnabled() || !eligible(module)) {
                continue;
            }
            boolean found = false;
            for (int j = 0; j < count; ++j) {
                if (entries[j].module == module) {
                    entries[j].visible = 1;
                    found = true;
                    break;
                }
            }
            if (!found) {
                append(module);
            }
        }
    }

    void update(ClientGameContext context) {
        ModuleRegistry registry = context.registry;
        int focused = context.focused;
        int categories = (showCombat.get() ? 1 : 0) + (showMove.get() ? 1 : 0) * 2
                + (showBlock.get() ? 1 : 0) * 4 + (showVisual.get() ? 1 : 0) * 8;
        int mask = categories | ((showUtility.get() ? 1 : 0) << 4)
                | ((requireBound.get() ? 1 : 0) << 5);
        int previousRefresh = refresh;
        if (filterMask != mask || (previousRefresh & 1) != 0) {
            refresh(registry);
            filterMask = mask;
            measure = 1;
            refresh = 0;
        }
        if ((previousRefresh & 1) == 0 && focused != 0) {
            return;
        }
        if (count != 0) {
            int lower = (lowercase.get() ? 1 : 0);
            int i = 0;
            do {
                Entry entry = entries[i];
                Module module = entry.module;
                if ((lower & 1) != 0) {
                    lowercaseName(entry, module);
                    lower = 1;
                } else {
                    formatName(entry, module.name());
                    lower = (lowercase.get() ? 1 : 0);
                    module = entry.module;
                }
                String suffix = module.suffix();
                if (suffix == null || !showDetails.get()) {
                    entry.details[0] = 0;
                } else {
                    formatDetails(entry, suffix);
                    if (lowercase.get()) {
                        lower = 1;
                        if (entry.details[0] != 0) {
                            lowercaseDetails(entry);
                        }
                    } else {
                        lower = 0;
                    }
                }
                ++i;
            } while (i < count);
        }
        measure = 1;
    }

    void toggle(ToggleEvent event, Module descriptor) {
        Module module = event.module;
        animationTime = Float.intBitsToFloat(0x3e4ccccd);
        int enabled = event.state;
        if (module == descriptor) {
            ModuleRegistry registry = ((ClientGameContext) event.context).registry;
            count = 0;
            if (enabled != 1) {
                return;
            }
            for (Module candidate : registry.all) {
                if (candidate.isEnabled() && eligible(candidate)) {
                    append(candidate);
                }
            }
            measure = 1;
            return;
        }
        for (int i = 0; i < count; ++i) {
            if (entries[i].module == module) {
                if (enabled == 0) {
                    entries[i].visible = 0;
                } else if (eligible(module)) {
                    entries[i].visible = 1;
                }
                return;
            }
        }
        if (enabled != 1 || !eligible(module)) {
            return;
        }
        append(module);
        measure = 1;
    }

    void prepare(UIPrepareEvent event) {
        int flags = event.result;
        event.result = flags | 1;
        if (measure != 0 || animationTime > 0.0f) {
            event.result = flags | 3;
        }
    }

    private static int compare(Entry a, Entry b) {
        if (b.width > a.width) {
            return 1;
        }
        if (a.width > b.width) {
            return -1;
        }
        int i = 0;
        while (true) {
            int x = a.name[i] & 0xff;
            int y = b.name[i] & 0xff;
            if (x != y) {
                return x - y;
            }
            if (x == 0) {
                return 0;
            }
            ++i;
        }
    }

    private static byte[] terminated(byte[] text) {
        return Arrays.copyOf(text, strlen(text));
    }

    void render(UIRenderEvent event) {
        UIDrawing drawing = (UIDrawing) event.surface;
        VectorContext vector = drawing.vector;
        vector.setFontSize(textSize.get());
        drawing.selectFont(0);
        if (animationTime > 0.0f) {
            animationTime = animationTime - event.elapsed;
        }
        int current = count;
        if (current != 0) {
            double delta = event.elapsed;
            double positive = delta * 5.0;
            double negative = Double.longBitsToDouble(Double.doubleToRawLongBits(delta) ^ 0x8000000000000000L) * 5.0;
            int i = 0;
            do {
                Entry entry = entries[i];
                float animation = entry.animation;
                if (entry.visible == 1) {
                    if (animation != 1.0f) {
                        entry.animation = Floats.min(1.0f, (float) ((double) animation + positive));
                    }
                } else {
                    entry.animation = (float) ((double) animation + negative);
                    if (!(entry.animation > 0.0f)) {
                        count = current - 1;
                        Entry removed = entries[i];
                        System.arraycopy(entries, i + 1, entries, i, current - i - 1);
                        entries[current - 1] = removed;
                        --current;
                    }
                }
                ++i;
            } while (i < current);
        }
        if (measure == 1) {
            vector.setTextAlignment(9);
            float[] bounds = new float[4];
            if (count != 0) {
                int i = 0;
                do {
                    Entry entry = entries[i];
                    byte[] name = terminated(entry.name);
                    vector.textBounds(0.0f, 0.0f, name, 0, name.length, bounds);
                    entry.width = bounds[2];
                    entry.height = bounds[3];
                    if (entry.details[0] != 0) {
                        byte[] details = terminated(entry.details);
                        vector.textBounds(0.0f, 0.0f, details, 0, details.length, bounds);
                        entry.width = (bounds[2] + 4.0f) + entry.width;
                    }
                    ++i;
                } while (i < count);
            }
            QSort.sort(entries, count, ModOverlay::compare);
            measure = 0;
        }
        vector.setTextAlignment(9);
        float y = 0.0f;
        UIColor background = UIDrawing.rgba(0.0f, 0.0f, 0.0f, Float.intBitsToFloat(0x3f333333));
        UIColor nameColor = settingColor(nameColorSetting);
        UIColor detailsColor = settingColor(detailsColorSetting);
        if (count != 0) {
            float screenWidth = drawing.width;
            int i = 0;
            do {
                Entry entry = entries[i];
                float alpha = entry.animation == 1.0f ? 1.0f : UIDrawing.transitionCurve(entry.animation);
                vector.setGlobalAlpha(alpha);
                float rounding = bgRounding.get();
                ++i;
                if (i < count) {
                    rounding = Floats.min(entry.width - entries[i].width, rounding);
                }
                vector.setFillColor(background);
                vector.beginPath();
                float px = paddingX.get();
                float py = paddingY.get();
                float twiceX = px + px;
                vector.roundedRectVarying((screenWidth - twiceX) - entry.width * alpha, y, twiceX + entry.width,
                        (py + py) + entry.height, 0.0f, 0.0f, 0.0f, rounding);
                vector.fill();
                vector.setFillColor(nameColor);
                float textY = paddingY.get() + y;
                float textX = (screenWidth - paddingX.get()) - entry.width * alpha;
                byte[] name = terminated(entry.name);
                float endX = vector.text(textX, textY, name, 0, name.length);
                if (entry.details[0] != 0) {
                    vector.setFillColor(detailsColor);
                    byte[] details = terminated(entry.details);
                    vector.text(endX + 4.0f, textY, details, 0, details.length);
                }
                float padding = paddingY.get();
                y = y + alpha * ((padding + padding) + entry.height);
            } while (i < count);
        }
        vector.setGlobalAlpha(1.0f);
    }

    void profile(ProfileEvent event) {
        if (event.action == 0) {
            refresh = 1;
        }
    }

    void binding(BindingEvent event) {
        if (requireBound.get()) {
            refresh((ModuleRegistry) event.context);
            measure = 1;
        }
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update((ClientGameContext) e.context));
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.UI_PREPARE, e -> prepare(e));
        on(EventTypes.UI_RENDER, e -> render(e), 100);
        on(EventTypes.PROFILE, e -> profile(e));
        on(EventTypes.BINDING, e -> binding(e));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Visible categories");
        w.show(showCombat);
        w.show(showMove);
        w.show(showBlock);
        w.show(showVisual);
        w.show(showUtility);
        w.heading("Customization");
        w.show(textSize);
        w.show(paddingX);
        w.show(paddingY);
        w.show(bgRounding);
        w.show(nameColorSetting);
        w.show(detailsColorSetting);
        w.show(lowercase);
        w.show(showDetails);
        w.heading("Conditions");
        w.show(requireBound);
    }
}

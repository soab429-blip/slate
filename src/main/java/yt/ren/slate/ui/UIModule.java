package yt.ren.slate.ui;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import yt.ren.slate.config.profile.ProfileConfiguration;
import yt.ren.slate.config.profile.ProfileRegistry;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ErrorEvent;
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
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.ui.vector.VectorContext;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Sse;

import java.util.Arrays;

public class UIModule extends Module {
    private final ChoiceSetting scale = register("scale", new ChoiceSetting("Scale", 4, 0, new String[]{"Small (75%)", "Medium (100%)", "Large (150%)", "Extra large (200%)"}, 1, 0, 0));
    private final ColorSetting accentColor = register("accent_color", new ColorSetting("Accent color", 0.953f, 0.365f, 0.071f, 0.0f, 0.0f, 0.0f));
    private final BooleanSetting allowInput = register("allow_input", new BooleanSetting("Allow input while open", 1, 0));
    private final BooleanSetting compact = register("compact", new BooleanSetting("Compact", 0, 0));
    private final BooleanSetting wide = register("wide", new BooleanSetting("Wide", 0, 0));

    private static final String[] TAB_TITLES = {"Combat", "Move", "Visual", "Block", "Utility", "Profiles", "Unload"};
    private static final double SCROLL_RATE = 11.0;

    private final ClientRuntime runtime;
    public final UIState state = new UIState();
    final UIPopups popups;
    private final UIPopupRenderer popupRenderer;
    private final UIWidgets.BindingMetrics metrics = new UIWidgets.BindingMetrics();
    private final UISettingsWidgets.Scratch scratch = new UISettingsWidgets.Scratch();

    public UIModule(ClientRuntime runtime) {
        super(ModuleId.GUI, "GUI", "Graphical interface for the cheat.", Category.VISUAL);
        traits(Trait.NO_TOGGLE, Trait.HIDDEN, Trait.NO_HOLD, Trait.GLOBAL);
        defaultKeybind(99);
        this.runtime = runtime;
        this.popups = new UIPopups(new UIPopups.ColorHistory(), runtime.clock);
        this.popupRenderer = new UIPopupRenderer(popups);

        for (int i = 0; i < TAB_TITLES.length; ++i) {
            state.i(0x2e0 + i * 16, i);
            state.ref(0x2e0 + i * 16 + 8, UIText.b(TAB_TITLES[i]));
        }
    }

    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    private static UIColor gray(float value) {
        return UIDrawing.gray(value);
    }

    void tick(ClientGameContext context) {
        if (Mouse.isGrabbed()) {
            Mouse.setGrabbed(false);
        }
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.module != descriptor) {
            return;
        }
        ClientGameContext context = (ClientGameContext) event.context;
        if (event.state == 1) {
            if (Mouse.isGrabbed()) {
                int width = Display.getWidth();
                int height = Display.getHeight();
                Mouse.setCursorPosition(width / 2, height / 2);
                Mouse.setGrabbed(false);
            }
            UIController.open(state, runtime.clock.now());
            return;
        }
        if (context.client.focused) {
            Mouse.setGrabbed(true);
        }
        popups.closePopup(state);
        UIController.resetInput(state);
        if (state.b(UIState.SHUTTING_DOWN) == 0) {
            int error = runtime.configuration.saveGlobalFile();
            if (error != 0) {
                context.registry.post(EventTypes.ERROR, new ErrorEvent(error, null));
            }
        }
    }

    void prepare(UIPrepareEvent event) {
        event.result |= 3;
    }

    void render(UIRenderEvent event) {
        ClientGameContext context = (ClientGameContext) event.context;
        float[] accent = accentColor.liveChannels();
        state.color(0x360, UIDrawing.rgb(accent[0], accent[1], accent[2]));
        state.b(0x4cc, (compact.get() ? 1 : 0));
        state.b(0x4cd, (wide.get() ? 1 : 0));
        renderController((UIDrawing) event.surface, context.registry);
    }

    void shutdown() {
        state.b(UIState.SHUTTING_DOWN, 1);
    }

    private void showSettings(UIState s, UIDrawing drawing, Module module) {
        if (module.hasLayout()) {
            module.layout(new UISettingsWidgets(s, drawing, popups, scratch));
        }
    }

    private void errorEvent(ModuleRegistry modules, int error, String text) {
        modules.post(EventTypes.ERROR, new ErrorEvent(error, text));
    }

    private void profileEvent(ModuleRegistry modules, byte[] name, int action) {
        modules.post(EventTypes.PROFILE, new ProfileEvent(name, action));
    }

    private static final String[] CONFIRMATION_TEXT = {"Load profile '%s'?", "Save profile '%s'?",
            "Open folder for profile '%s'?", "Delete profile '%s'?"};
    private static final byte[][] ACTION_HINTS = {UIText.b("Click to load"), UIText.b("Click to save"),
            UIText.b("Click to open folder"), UIText.b("Click to delete")};
    private static final int[] ACTION_ICONS = {3, 0, 2, 1};

    private static class ProfileAction {
        UIState state;
        ModuleRegistry modules;
        byte[] name;
        int action;
    }

    private void directProfileResult(UIState s, ModuleRegistry modules, byte[] name, int action, int status) {
        if (status == 0) {
            profileEvent(modules, name, action);
            s.d(0x4a0, -1.0);
            return;
        }
        String text = null;
        if (status == 6003) {
            text = "Default profile cannot be overwritten";
        } else if (status == 6004) {
            text = "Default profile cannot be deleted";
        }
        errorEvent(modules, status, text);
    }

    private void profileActionClick(UIState s, ModuleRegistry modules, ProfileRegistry.Entry profile, int action) {
        byte[] name = profile != null ? profile.name : null;
        ProfileConfiguration configuration = runtime.configuration;
        switch (action) {
            case 0:
                if (SettingsWidgets.dirty != 0) {
                    confirmProfileAction(s, modules, name, 0);
                } else {
                    directProfileResult(s, modules, name, action, configuration.loadProfile(name));
                }
                break;
            case 1:
                if (profile == null) {
                    directProfileResult(s, modules, name, action, 6003);
                    break;
                }
                if (configuration.activeName == null || !Arrays.equals(name, configuration.activeName)) {
                    confirmProfileAction(s, modules, name, 1);
                } else {
                    directProfileResult(s, modules, name, action, configuration.saveProfile(name, 1));
                }
                break;
            case 2:
                confirmProfileAction(s, modules, name, 2);
                break;
            case 3:
                if (profile != null) {
                    confirmProfileAction(s, modules, name, 3);
                } else {
                    directProfileResult(s, modules, name, action, 6004);
                }
                break;
            default:
                break;
        }
    }

    private void confirmProfileAction(UIState s, ModuleRegistry modules, byte[] name, int action) {
        ProfileAction owner = new ProfileAction();
        owner.state = s;
        owner.modules = modules;
        owner.name = name != null ? name.clone() : null;
        owner.action = action;
        String shown = name != null ? UIText.s(name) : "Default";
        popups.openConfirmation(s, owner, this::performProfileAction, CONFIRMATION_TEXT[action], shown);
    }

    private void performProfileAction(Object context) {
        ProfileAction owner = (ProfileAction) context;
        ProfileConfiguration configuration = runtime.configuration;
        int status = 0;
        switch (owner.action) {
            case 0:
                status = configuration.loadProfile(owner.name);
                break;
            case 1:
                status = configuration.saveProfile(owner.name, 1);
                break;
            case 2:
                status = configuration.exploreProfiles();
                break;
            case 3:
                status = configuration.deleteProfile(owner.name);
                break;
            default:
                break;
        }
        if (status != 0) {
            errorEvent(owner.modules, status, null);
        } else {
            profileEvent(owner.modules, owner.name, owner.action);
        }
        owner.state.d(0x4a0, -1.0);
    }

    private static final byte[] DEFAULT = UIText.b("Default");
    private static final byte[] DEFAULT_DESCRIPTION = UIText.b("Contains the default configuration.");
    private static final byte[] CREATE_NEW_PROFILE = UIText.b("CREATE NEW PROFILE");

    private void drawProfiles(UIState s, UIDrawing drawing, ModuleRegistry modules) {
        ProfileRegistry profiles = runtime.profileRegistry;
        if (s.captureMode() == 0 && s.d(0x4a0) + 2.0 < drawing.now) {
            int status = profiles.refresh(runtime.configuration);
            if (status != 0) {
                errorEvent(modules, status, null);
            }
            s.d(0x4a0, drawing.now);
        }
        VectorContext cardVector = drawing.vector;
        UIController.fitColumns(s, 430.0f);
        long count = profiles.entries.size();
        long index = 0;
        do {
            UIRect card = UIController.nextCell(s, s.f(0x4c4));
            UIRect screen = new UIRect(0.0f, s.scroll(), drawing.width, drawing.height);
            if (UIWidgets.overlap(screen, card)) {
                ProfileRegistry.Entry profile = index != 0 ? profiles.entries.get((int) index - 1) : null;
                cardVector.setFillColor(gray(bits(0x3da0a0a1)));
                drawing.roundedRectangle(card, 20.0f);
                UIRect scissor = card.copy();
                UIController.pushScissoredLayout(drawing, s, scissor, 8.0f, 12.0f);
                UIController.setColumns(s, 1);
                VectorContext titleVector = drawing.vector;
                UIRect title = UIController.nextCell(s, 20.0f);
                titleVector.setFontSize(16.0f);
                titleVector.setFillColor(gray(bits(0x3f5dddde)));
                UIRect titleCopy = title.copy();
                if (profile == null) {
                    drawing.textRange(titleCopy, 0, 0, DEFAULT, DEFAULT.length);
                } else {
                    float advance = drawing.textRange(titleCopy, 0, 0, profile.name, profile.displayEnd);
                    UIRect binding = new UIRect(advance + 5.0f, title.y, (title.width - advance) + title.x, title.height);
                    final ProfileRegistry.Entry target = profile;
                    UIWidgets.binding(s, drawing, (argument, key) -> runtime.changeProfileBinding(
                            (ProfileRegistry.Entry) argument, key), target, profile.bindCode, null, binding, metrics);
                }
                int right = 80;
                int gap = 36;
                for (int action = 0; action != 4; ++action, right -= 20, gap -= 12) {
                    UIRect button = new UIRect(((title.x + title.width) - (float) right) - (float) gap, title.y, 20.0f,
                            20.0f);
                    float alpha = 0.5f;
                    if (!(profile == null && (action & 1) != 0)) {
                        alpha = 1.0f;
                        if (UIController.hovered(s, button)) {
                            double center = (double) button.width * 0.5 + (double) button.x;
                            UIController.tooltip(s, ACTION_HINTS[action], Sse.cvttsd2si(center),
                                    Sse.cvttss2si(button.y));
                            if (UIController.clicked(s, button)) {
                                profileActionClick(s, modules, profile, action);
                            }
                        }
                    }
                    drawing.icon(button, ACTION_ICONS[action], alpha);
                }
                if (s.b(0x4cc) == 0) {
                    UIRect description = UIController.nextCell(s, 20.0f);
                    titleVector.setFontSize(14.0f);
                    titleVector.setFillColor(gray(bits(0x3f19999a)));
                    drawing.alignedText(description, 0, 0, profile != null ? profile.modification() : DEFAULT_DESCRIPTION);
                }
                s.f(0x4c4, UIController.popLayout(drawing, s));
            }
            ++index;
        } while (index <= count);
        VectorContext vector = drawing.vector;
        UIRect create = UIController.nextCell(s, s.f(0x4c4));
        UIColor background = gray(bits(0x3da0a0a1));
        vector.setFillColor(UIDrawing.replaceAlpha(background, 0.5f));
        drawing.roundedRectangle(create, 20.0f);
        if (!UIWidgets.clickTextEntry(s, create, UIWidgets::appendProfileCharacter, null)) {
            vector.setFontSize(15.0f);
            vector.setFillColor(gray(UIController.hovered(s, create) ? 1.0f : bits(0x3f5dddde)));
            drawing.alignedText(create, 1, 1, CREATE_NEW_PROFILE);
        } else {
            vector.setFontSize(16.0f);
            vector.setFillColor(gray(1.0f));
            float y = (float) ((double) create.height * 0.5 + (double) create.y - 8.0);
            UIWidgets.textEntry(s, drawing, new UIRect(create.x, y, create.width, 16.0f));
            byte[] name = UIController.submittedText(s);
            if (name != null) {
                int status = runtime.configuration.saveProfile(name, 0);
                if (status != 0) {
                    errorEvent(modules, status, status == 1012 ? "Profile name is taken." : null);
                } else {
                    profileEvent(modules, name, 1);
                    s.d(0x4a0, -1.0);
                }
            }
        }
    }

    private static void smoothScroll(UIState s, float delta) {
        float limit = Math.max(0.0f, s.f(0x4c0));
        float target = Math.max(0.0f, Math.min(limit, s.scrollTarget()));
        s.scrollTarget(target);
        float scroll = Math.max(0.0f, Math.min(limit, s.scroll()));
        float blend = (float) Math.exp(-SCROLL_RATE * Math.max(0.0f, delta));
        scroll = target + (scroll - target) * blend;
        if (Math.abs(target - scroll) < 0.05f) {
            scroll = target;
        }
        s.scroll(scroll);
    }

    void renderController(UIDrawing drawing, ModuleRegistry modules) {
        UIState s = state;
        VectorContext initial = drawing.vector;
        initial.save();
        UIController.backdrop(drawing, s.f(0x4b8));
        float screenWidth = drawing.width;
        float width = Floats.min(-16.0f + screenWidth, s.b(0x4cd) != 0 ? 1500.0f : 900.0f);
        float left = (screenWidth - width) * 0.5f;
        smoothScroll(s, (float) (drawing.now - s.d(0x498)));
        float reciprocal = 1.0f / drawing.scale;
        s.f(UIState.HIT_X, s.f(UIState.MOUSE_X) * reciprocal);
        s.f(UIState.HIT_Y, s.f(UIState.MOUSE_Y) * reciprocal + s.scroll());
        drawing.vector.translate(0.0f, -Math.round(s.scroll() * drawing.scale) / drawing.scale);
        UIController.resetLayout(s);
        UIRect bounds = new UIRect(left, 0.0f, width, drawing.height);
        float delta = (float) (drawing.now - s.d(0x498));
        s.f(0x4b8, Floats.min(1.0f, (float) ((double) s.f(0x4b8) + (double) delta / 0.3)));
        UIRect layout = bounds.copy();
        UIController.pushLayout(s, layout, 8.0f, 12.0f);
        UIController.setColumns(s, 1);
        UIController.navigation(s, drawing, delta, popups);
        long selected = s.l(0x350);
        float distance = UIController.unsignedFloat(selected) - s.f(0x358);
        if (distance != 0.0f) {
            VectorContext vector = drawing.vector;
            float absolute = Floats.magnitude(distance);
            float alpha = 1.0f - (absolute + absolute);
            if (0.5f < absolute) {
                alpha = 0.0f;
            }
            vector.setGlobalAlpha(alpha);
            float shift = (float) ((double) distance * ((double) drawing.width * -0.1));
            vector.translate(shift, 0.0f);
            selected = s.l(0x350);
        }
        int kind = s.i(0x2e0 + (int) selected * 16);
        if (Integer.compareUnsigned(kind, 5) < 0) {
            UIWidgets.moduleCards(s, drawing, modules, kind, delta, metrics, this::showSettings);
        } else if (kind == 5) {
            drawProfiles(s, drawing, modules);
        }
        UIController.popLayout(drawing, s);
        if (s.b(0x268) == 0) {
            s.f(0x2d8, 0.0f);
        } else {
            float progress = (float) ((double) s.f(0x2d8) + (double) delta / 0.1);
            s.f(0x2d8, progress);
            VectorContext vector = drawing.vector;
            vector.save();
            vector.translate(s.f(0x2d0), s.f(0x2d4));
            if (progress > 1.0f) {
                s.f(0x2d8, 1.0f);
            } else {
                float curve = UIDrawing.transitionCurve(progress);
                vector.setGlobalAlpha(curve);
                vector.scale(curve, curve);
            }
            vector.setFontSize(14.0f);
            byte[] text = s.string(0x26a, 100);
            float measured = vector.textBounds(0.0f, 0.0f, text, 0, text.length, null);
            UIRect tooltip = new UIRect((float) ((double) measured * -0.5 - 5.0), -28.0f, measured + 10.0f, 20.0f);
            vector.setFillColor(gray(bits(0x3e808081)));
            drawing.roundedRectangle(tooltip, 5.0f);
            vector.setFillColor(gray(bits(0x3f5dddde)));
            drawing.alignedText(tooltip, 1, 0, text);
            vector.restore();
            s.b(0x268, 0);
        }
        initial.restore();
        float extent = (float) s.i(0x260);
        s.f(0x4c0, Floats.max(0.0f, (extent - drawing.height) + 12.0f));
        if (UIController.popupOpen(s)) {
            initial.save();
            s.f(UIState.HIT_Y, s.f(UIState.HIT_Y) - s.scroll());
            UIController.backdrop(drawing, s.f(0x418));
            UIController.resetLayout(s);
            if (bounds.width > 600.0f) {
                bounds.x = (float) ((double) (bounds.width - 600.0f) * 0.5 + (double) bounds.x);
                bounds.width = 600.0f;
            }
            UIController.pushLayout(s, bounds, 8.0f, 12.0f);
            popupRenderer.draw(s, drawing);
            UIController.popLayout(drawing, s);
            initial.restore();
        }
        UIController.consumeClick(s);
        s.b(UIState.LEFT_CONSUMED, s.leftButton());
        s.d(0x498, drawing.now);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> tick((ClientGameContext) e.context));
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.UI_PREPARE, e -> prepare(e));
        on(EventTypes.UI_RENDER, e -> render(e));
        on(EventTypes.UNLOAD, e -> shutdown());
        runtime.guiInput = event -> UIController.consumeInput(state, event, popups);
    }

    public boolean allowsInput() {
        return allowInput.get();
    }

    public float[] accentColor() {
        return accentColor.liveChannels().clone();
    }

    public int scaleIndex() {
        return scale.index();
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(scale);
        w.show(accentColor);
        w.show(allowInput);
        w.heading("Layout");
        w.show(compact);
        w.show(wide);
    }
}

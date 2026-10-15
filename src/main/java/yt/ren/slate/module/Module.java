package yt.ren.slate.module;

import yt.ren.slate.event.Event;
import yt.ren.slate.event.EventBus.Subscription;
import yt.ren.slate.event.EventListener;
import yt.ren.slate.event.EventType;
import yt.ren.slate.setting.Setting;
import yt.ren.slate.ui.SettingsWidgets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class Module {
    private final ModuleId id;
    private final String name;
    private final String description;
    private final Category category;
    private final ByteBuffer uiState = ByteBuffer.allocate(0x20).order(ByteOrder.LITTLE_ENDIAN);
    private boolean enabled;
    private int keybind;
    private boolean holdToActivate;
    private boolean held;

    private final List<Subscription<?>> subscriptions = new ArrayList<>();
    private final List<Setting> settings = new ArrayList<>();
    private ModuleRegistry registry;

    private final EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);

    protected Module(ModuleId id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected final void traits(Trait... values) {
        Collections.addAll(traits, values);
    }

    protected final void defaultKeybind(int key) {
        keybind = key;
    }

    public boolean has(Trait trait) {
        return traits.contains(trait);
    }

    public boolean persistsSettings() {
        return !settings.isEmpty();
    }

    protected final <S extends Setting> S register(String key, S setting) {
        for (Setting existing : settings) {
            if (existing.key().equals(key)) {
                throw new IllegalStateException("Setting declared twice: " + id + "." + key);
            }
        }
        setting.attach(id, key);
        settings.add(setting);
        return setting;
    }

    public String suffix() {
        return null;
    }

    public boolean hasLayout() {
        return !settings.isEmpty();
    }

    public void layout(SettingsWidgets widgets) {
    }

    void attach(ModuleRegistry registry) {
        this.registry = registry;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public ModuleId id() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    void markEnabled(boolean value) {
        enabled = value;
    }

    public ByteBuffer uiState() {
        return uiState;
    }

    public boolean isHeld() {
        return held;
    }

    public void setHeld(boolean value) {
        held = value;
    }

    public boolean setEnabled(boolean value) {
        return registry != null && registry.queueModuleChange(this, value ? 1 : 0);
    }

    public boolean toggle() {
        return setEnabled(!isEnabled());
    }

    public int keybind() {
        return keybind;
    }

    public void setKeybind(int key) {
        keybind = key;
    }

    public boolean holdToActivate() {
        return holdToActivate;
    }

    public void setHoldToActivate(boolean value) {
        holdToActivate = value;
    }

    public List<Setting> settings() {
        return Collections.unmodifiableList(settings);
    }

    public List<Subscription<?>> subscriptions() {
        return Collections.unmodifiableList(subscriptions);
    }

    protected void setup() {
    }

    public final void initialize() {
        setup();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    void enabled() {
        onEnable();
    }

    void disabled() {
        onDisable();
    }

    public <E extends Event> void on(EventType<E> type, EventListener<? super E> listener) {
        on(type, listener, 0);
    }

    public <E extends Event> void on(EventType<E> type, EventListener<? super E> listener, int priority) {
        subscriptions.add(new Subscription<E>(this, type, listener, priority));
    }

    @Override
    public String toString() {
        return name;
    }
}

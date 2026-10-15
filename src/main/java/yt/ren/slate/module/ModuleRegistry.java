package yt.ren.slate.module;

import yt.ren.slate.event.Event;
import yt.ren.slate.event.EventBus;
import yt.ren.slate.event.EventType;
import yt.ren.slate.event.EventTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ModuleRegistry {
    public final Module[] all;
    public final List<List<Module>> categories = new ArrayList<>();
    public final EventBus bus;
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private final List<Module> pendingEnable = new ArrayList<>();
    private final List<Module> pendingDisable = new ArrayList<>();
    public BindingCallback context;

    @FunctionalInterface
    public interface BindingCallback {
        void change(Module module, int code);
    }

    public ModuleRegistry(Module[] registered) {
        all = registered.clone();
        for (Category category : Category.values()) {
            List<Module> members = new ArrayList<>();
            byCategory.put(category, Collections.unmodifiableList(members));
            categories.add(members);
        }
        for (Module module : all) {
            categories.get(module.category().ordinal()).add(module);
            module.attach(this);
        }
        bus = new EventBus(Arrays.asList(all));
    }

    public List<Module> modules() {
        return Collections.unmodifiableList(Arrays.asList(all));
    }

    public List<Module> modules(Category category) {
        return byCategory.get(category);
    }

    public <E extends Event> void post(EventType<E> type, E event) {
        bus.post(type, event);
    }

    public boolean hasListeners(EventType<?> type) {
        return bus.hasListeners(type);
    }

    public boolean queueModuleChange(Module module, int desired) {
        if ((module.isEnabled() ? 1 : 0) == desired) {
            return true;
        }
        if (desired == 0) {
            VetoEvent event = new VetoEvent(module);
            post(EventTypes.MODULE_VETO, event);
            if ((event.veto & 1) != 0) {
                return false;
            }
            pendingDisable.add(module);
            return true;
        }
        pendingEnable.add(module);
        return true;
    }

    public boolean changesPending() {
        return !pendingDisable.isEmpty() || !pendingEnable.isEmpty();
    }

    public static boolean claimOwner(Module[] owner, Module module) {
        if (owner[0] == module) {
            return true;
        }
        if (owner[0] != null) {
            return false;
        }
        owner[0] = module;
        return true;
    }

    public static void releaseOwner(Module[] owner, Module module) {
        if (owner[0] == module) {
            owner[0] = null;
        }
    }

    public void applyModuleChanges(Object context, Module[] owner) {
        if (!pendingEnable.isEmpty()) {
            for (Module module : pendingEnable) {
                module.markEnabled(true);
                bus.refresh(module);
            }
            for (int i = 0; i < pendingEnable.size(); i++) {
                Module module = pendingEnable.get(i);
                if (!module.isEnabled()) {
                    module.markEnabled(true);
                    bus.refresh(module);
                }
                if (bus.hasListeners(EventTypes.MODULE_TOGGLE)) {
                    post(EventTypes.MODULE_TOGGLE, new ToggleEvent(context, module, 1));
                }
                module.enabled();
            }
            pendingEnable.clear();
        }
        if (!pendingDisable.isEmpty()) {
            for (Module module : pendingDisable) {
                module.markEnabled(false);
            }
            for (int i = 0; i < pendingDisable.size(); i++) {
                Module module = pendingDisable.get(i);
                module.markEnabled(false);
                post(EventTypes.MODULE_TOGGLE, new ToggleEvent(context, module, 0));
                module.disabled();
                releaseOwner(owner, module);
            }
            for (int i = 0; i < pendingDisable.size(); i++) {
                bus.refresh(pendingDisable.get(i));
            }
            pendingDisable.clear();
        }
    }

    public static class VetoEvent extends Event {
        public int veto;

        VetoEvent(Module module) {
            super(module);
        }

        public Module module() {
            return (Module) context;
        }
    }

    public static class ToggleEvent extends Event {
        public final Module module;
        public final int state;

        ToggleEvent(Object context, Module module, int state) {
            super(context);
            this.module = module;
            this.state = state;
        }
    }
}

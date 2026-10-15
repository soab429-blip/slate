package yt.ren.slate.input;

import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleRegistry;

public class ClientInput {
    @FunctionalInterface
    public interface UIInputConsumer {
        int consume(ClientInputEvent event);
    }

    public interface Runtime {
        boolean gameFocused();

        int uiInput(ClientInputEvent event, int unchanged);

        void friendClick();
    }

    public interface Bindings {
        KeyBindings profileKeys();

        KeyBindings moduleKeys();
    }

    public interface Flags {
        int guiEnabled();

        int friendsEnabled();
    }

    public static int routeGuiInput(ClientInputEvent event, int unchanged, ModuleRegistry registry,
                                    Module gui, int allowKeyboard, UIInputConsumer consumer) {
        if (gui.isEnabled() && consumer.consume(event) != 0) {
            return 1;
        }
        if (event.type == 2 && event.state == 1 && event.key != 0) {
            if (unchanged == 0 && event.key == gui.keybind()) {
                registry.queueModuleChange(gui, (gui.isEnabled() ? 0 : 1));
                return 1;
            }
            if (event.key == 0x57 && gui.isEnabled()) {
                registry.queueModuleChange(gui, 0);
                return 1;
            }
        }
        if (!gui.isEnabled() || (event.type == 2 && allowKeyboard != 0 && Integer.compareUnsigned(event.key, 5) > 0)) {
            return 0;
        }
        return 1;
    }

    public static int handleClientInput(ClientInputEvent event, byte[] keyStates, Flags flags, Bindings bindings,
                                        Runtime runtime) {
        int unchanged = 1;
        if (event.type == 2) {
            int old = keyStates[event.key] & 0xFF;
            int down = old & 1;
            int next = event.state;
            if (event.toggle != 1 || down != next) {
                if (event.toggle == 1) {
                    next = ((next | (old & 2)) ^ 2) & 0xFF;
                }
                keyStates[event.key] = (byte) next;
                unchanged = down == event.state ? 1 : 0;
            }
        }
        int consumed = runtime.uiInput(event, unchanged);
        if (consumed == 0 && unchanged == 0 && event.type == 2 && event.key != 0) {
            if (runtime.gameFocused() && flags.guiEnabled() == 0) {
                if (flags.friendsEnabled() == 1 && event.type == 2 && event.state == 1 && event.key == 3) {
                    runtime.friendClick();
                }
                if (bindings.profileKeys().dispatch(event.key, event.state) == 0) {
                    bindings.moduleKeys().dispatch(event.key, event.state);
                }
            }
        }
        return consumed;
    }
}

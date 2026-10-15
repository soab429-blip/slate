package yt.ren.slate.input;

import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class PhysicalKeys {
    public static boolean pressed(KeyBinding binding) {
        int code = binding.keyCode;
        if (code < 0) {
            return Mouse.isButtonDown(code + 100);
        }
        if (code > 256) {
            return false;
        }
        return Keyboard.isKeyDown(code);
    }
}

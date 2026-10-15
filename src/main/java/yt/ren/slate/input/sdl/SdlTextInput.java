package yt.ren.slate.input.sdl;

import net.minecraft.client.Minecraft;
import yt.ren.slate.input.WindowInput;
import yt.ren.slate.input.WindowInputHost;
import yt.ren.slate.mixin.client.window.sdl.SdlDisplayAccess;

public class SdlTextInput {
    private static boolean requested;

    public static void sync(SdlDisplayAccess display) {
        WindowInput input = WindowInputHost.input();
        boolean capture = input != null && input.textCapture();
        if (capture == requested) {
            return;
        }
        requested = capture;
        if (capture || Minecraft.getInstance().screen == null) {
            display.slate$setTextInputActive(capture);
        }
    }
}

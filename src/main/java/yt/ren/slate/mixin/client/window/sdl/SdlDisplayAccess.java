package yt.ren.slate.mixin.client.window.sdl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = {"pl.tomgirl.pylon.window.DisplaySdl", "pl.tomgirl.lenis.window.DisplaySdl"}, remap = false)
public interface SdlDisplayAccess {
    @Invoker("setTextInputActive")
    void slate$setTextInputActive(boolean active);
}

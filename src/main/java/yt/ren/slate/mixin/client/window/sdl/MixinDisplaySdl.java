package yt.ren.slate.mixin.client.window.sdl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.slate.input.sdl.SdlTextInput;

@Mixin(targets = {"pl.tomgirl.pylon.window.DisplaySdl", "pl.tomgirl.lenis.window.DisplaySdl"}, remap = false)
public abstract class MixinDisplaySdl {
    @Inject(method = "processMessages", at = @At("HEAD"))
    private void slate$syncTextInput(CallbackInfo info) {
        SdlTextInput.sync((SdlDisplayAccess) (Object) this);
    }
}

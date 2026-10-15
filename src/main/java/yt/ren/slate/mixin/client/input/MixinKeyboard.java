package yt.ren.slate.mixin.client.input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.slate.input.InputPump;

@Mixin(targets = "org.lwjgl.input.Keyboard", remap = false)
public abstract class MixinKeyboard {
    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private static void slate$next(CallbackInfoReturnable<Boolean> info) {
        if (!InputPump.pumping()) {
            info.setReturnValue(InputPump.nextKeyboard());
        }
    }

    @Inject(method = "getEventKey", at = @At("HEAD"), cancellable = true)
    private static void slate$eventKey(CallbackInfoReturnable<Integer> info) {
        InputPump.KeyEvent event = InputPump.keyEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.key);
        }
    }

    @Inject(method = "getEventCharacter", at = @At("HEAD"), cancellable = true)
    private static void slate$eventCharacter(CallbackInfoReturnable<Character> info) {
        InputPump.KeyEvent event = InputPump.keyEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.character);
        }
    }

    @Inject(method = "getEventKeyState", at = @At("HEAD"), cancellable = true)
    private static void slate$eventKeyState(CallbackInfoReturnable<Boolean> info) {
        InputPump.KeyEvent event = InputPump.keyEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.state);
        }
    }

    @Inject(method = "isRepeatEvent", at = @At("HEAD"), cancellable = true)
    private static void slate$repeat(CallbackInfoReturnable<Boolean> info) {
        InputPump.KeyEvent event = InputPump.keyEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.repeat);
        }
    }

    @Inject(method = "getEventNanoseconds", at = @At("HEAD"), cancellable = true)
    private static void slate$eventNanos(CallbackInfoReturnable<Long> info) {
        InputPump.KeyEvent event = InputPump.keyEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.nanos);
        }
    }
}

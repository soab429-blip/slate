package yt.ren.slate.mixin.client.input;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.slate.input.InputPump;

@Mixin(targets = "org.lwjgl.input.Mouse", remap = false)
public abstract class MixinMouse {
    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private static void slate$next(CallbackInfoReturnable<Boolean> info) {
        if (!InputPump.pumping()) {
            info.setReturnValue(InputPump.nextMouse());
        }
    }

    @Inject(method = "getEventButton", at = @At("HEAD"), cancellable = true)
    private static void slate$eventButton(CallbackInfoReturnable<Integer> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.button);
        }
    }

    @Inject(method = "getEventButtonState", at = @At("HEAD"), cancellable = true)
    private static void slate$eventButtonState(CallbackInfoReturnable<Boolean> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.state);
        }
    }

    @Inject(method = "getEventX", at = @At("HEAD"), cancellable = true)
    private static void slate$eventX(CallbackInfoReturnable<Integer> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.x);
        }
    }

    @Inject(method = "getEventY", at = @At("HEAD"), cancellable = true)
    private static void slate$eventY(CallbackInfoReturnable<Integer> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.y);
        }
    }

    @Inject(method = "getEventDX", at = @At("HEAD"), cancellable = true)
    private static void slate$eventDX(CallbackInfoReturnable<Integer> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.dx);
        }
    }

    @Inject(method = "getEventDY", at = @At("HEAD"), cancellable = true)
    private static void slate$eventDY(CallbackInfoReturnable<Integer> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.dy);
        }
    }

    @Inject(method = "getEventDWheel", at = @At("HEAD"), cancellable = true)
    private static void slate$eventWheel(CallbackInfoReturnable<Integer> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.wheel);
        }
    }

    @Inject(method = "getEventNanoseconds", at = @At("HEAD"), cancellable = true)
    private static void slate$eventNanos(CallbackInfoReturnable<Long> info) {
        InputPump.MouseEvent event = InputPump.mouseEvent();
        if (!InputPump.pumping() && event != null) {
            info.setReturnValue(event.nanos);
        }
    }

    @Inject(method = {"getDX", "getDY"}, at = @At("RETURN"), cancellable = true)
    private static void slate$delta(CallbackInfoReturnable<Integer> info) {
        if (info.getReturnValueI() != 0 && InputPump.blockLook()) {
            info.setReturnValue(0);
        }
    }

    @Inject(method = "isButtonDown", at = @At("RETURN"), cancellable = true)
    private static void slate$buttonDown(int button, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ() && InputPump.syntheticHeld(button)) {
            info.setReturnValue(true);
        }
    }
}

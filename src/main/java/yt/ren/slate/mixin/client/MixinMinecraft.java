package yt.ren.slate.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;pick(F)V"))
    private void slate$getMouseOver(GameRenderer renderer, float partial, Operation<Void> original) {
        ClientHooks.getMouseOver(() -> original.call(renderer, partial));
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;doAttack()V"))
    private void slate$clickMouse(Minecraft game, Operation<Void> original) {
        ClientHooks.clickMouse(() -> original.call(game));
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;doUse()V"))
    private void slate$rightClickMouse(Minecraft game, Operation<Void> original) {
        ClientHooks.rightClickMouse(() -> original.call(game));
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;tickEntities()V"))
    private void slate$updateEntities(ClientWorld world, Operation<Void> original) {
        ClientHooks.updateEntities(world, () -> original.call(world));
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientPlayerInteractionManager;tick()V"))
    private void slate$updateController(ClientPlayerInteractionManager controller, Operation<Void> original) {
        ClientHooks.updateController(() -> original.call(controller));
    }

    @WrapOperation(method = "updateDisplay()V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false))
    private void slate$updateDisplay(Operation<Void> original) {
        ClientHooks.updateDisplay(original::call);
    }
}

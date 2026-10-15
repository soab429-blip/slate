package yt.ren.slate.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @WrapOperation(method = "render(IFJ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;clear(I)V"))
    private void slate$postWorldClear(int mask, Operation<Void> original) {
        if (ClientHooks.unloaded) {
            original.call(mask);
            return;
        }
        ClientHooks.postWorldClear(mask);
    }

    @WrapOperation(method = "render(IFJ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setup(Lnet/minecraft/entity/living/player/PlayerEntity;Z)V"))
    private void slate$updateActiveRenderInfo(PlayerEntity player, boolean thirdPerson, Operation<Void> original) {
        ClientHooks.updateActiveRenderInfo(() -> original.call(player, thirdPerson));
    }

    @WrapOperation(method = "setupCamera(FI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;transformCamera(F)V"))
    private void slate$orientCamera(GameRenderer renderer, float partial, Operation<Void> original) {
        ClientHooks.orientCamera(() -> original.call(renderer, partial));
    }

    @WrapOperation(method = "render(FJ)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;isActive()Z", remap = false))
    private boolean slate$isDisplayActive(Operation<Boolean> original) {
        return ClientHooks.isDisplayActivePreRender(original::call);
    }
}

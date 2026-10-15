package yt.ren.slate.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {
    @WrapOperation(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V"))
    private void slate$renderPlayer(PlayerRenderer renderer, LivingEntity entity, double x, double y, double z,
                                    float yaw, float partial, Operation<Void> original) {
        ClientHooks.renderPlayer(entity, () -> original.call(renderer, entity, x, y, z, yaw, partial));
    }
}

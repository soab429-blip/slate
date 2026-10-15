package yt.ren.slate.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @WrapOperation(method = {"renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
            "shouldRenderNameTag(Lnet/minecraft/entity/Entity;)Z"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z"))
    private boolean slate$canRenderName(LivingEntityRenderer<?> renderer, LivingEntity entity,
                                        Operation<Boolean> original) {
        return ClientHooks.canRenderName(entity, () -> original.call(renderer, entity));
    }
}

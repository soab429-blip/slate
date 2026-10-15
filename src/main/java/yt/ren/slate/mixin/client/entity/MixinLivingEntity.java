package yt.ren.slate.mixin.client.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @WrapOperation(method = "moveRelative(FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/LivingEntity;updateVelocity(FFF)V"))
    private void slate$moveFlying(LivingEntity entity, float strafe, float forward, float friction,
                                  Operation<Void> original) {
        ClientHooks.moveFlying(entity, strafe, forward, friction,
                (s, f, r) -> original.call(entity, s, f, r));
    }
}

package yt.ren.slate.mixin.client.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @WrapOperation(method = "move(DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z"))
    private boolean slate$shouldClipLedge(Entity entity, Operation<Boolean> original) {
        return ClientHooks.shouldClipLedge(entity, () -> original.call(entity));
    }
}

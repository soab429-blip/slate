package yt.ren.slate.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    @Unique
    private static final String PREPARE = "prepare(Lnet/minecraft/world/World;Lnet/minecraft/client/render/TextRenderer;"
            + "Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/client/options/GameOptions;F)V";

    @WrapOperation(method = PREPARE, at = @At(value = "FIELD", opcode = 181,
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;cameraPitch:F"))
    private void slate$setRenderX(EntityRenderDispatcher manager, float value, Operation<Void> original) {
        original.call(manager, ClientHooks.setRenderX(value));
    }

    @WrapOperation(method = PREPARE, at = @At(value = "FIELD", opcode = 181,
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;cameraYaw:F"))
    private void slate$setRenderY(EntityRenderDispatcher manager, float value, Operation<Void> original) {
        original.call(manager, ClientHooks.setRenderY(value));
    }
}

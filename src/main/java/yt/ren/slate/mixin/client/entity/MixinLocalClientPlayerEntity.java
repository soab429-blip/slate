package yt.ren.slate.mixin.client.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import yt.ren.slate.util.NonVirtualSwing;

@Mixin(LocalClientPlayerEntity.class)
public abstract class MixinLocalClientPlayerEntity extends ClientPlayerEntity implements NonVirtualSwing {
    protected MixinLocalClientPlayerEntity(World world, GameProfile profile) {
        super(world, profile);
    }

    @Override
    public void slate$swingNonVirtual() {
        super.swingArm();
    }
}

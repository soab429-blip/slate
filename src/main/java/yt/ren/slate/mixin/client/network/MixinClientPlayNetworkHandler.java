package yt.ren.slate.mixin.client.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler {
    @WrapOperation(method = {
            "handleWorldChunk(Lnet/minecraft/network/packet/s2c/play/WorldChunkS2CPacket;)V",
            "handleWorldChunks(Lnet/minecraft/network/packet/s2c/play/WorldChunksS2CPacket;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;notifyRegionChanged(IIIIII)V"))
    private void slate$invalidateChunk(ClientWorld world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                       Operation<Void> original) {
        ClientHooks.invalidateChunk(world, minX, minZ,
                () -> original.call(world, minX, minY, minZ, maxX, maxY, maxZ));
    }

    @WrapOperation(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/packet/Packet;)V"))
    private void slate$sendPacket(Connection connection, Packet<?> packet, Operation<Void> original) {
        if (ClientHooks.sendPacket(connection, packet)) {
            original.call(connection, packet);
        }
    }
}

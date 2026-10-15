package yt.ren.slate.mixin.client.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yt.ren.slate.hook.ClientHooks;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld {
    @WrapOperation(method = "updateChunk(IIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/chunk/ClientChunkCache;unloadChunk(II)V"))
    private void slate$unloadChunk(ClientChunkCache provider, int x, int z, Operation<Void> original) {
        ClientHooks.unloadChunk(x, z, () -> original.call(provider, x, z));
    }

    @WrapOperation(method = "setBlockStateFromPacket(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;I)Z"))
    private boolean slate$setBlock(ClientWorld world, BlockPos pos, BlockState state, int flags,
                                   Operation<Boolean> original) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        Block block = state.getBlock();
        int id = Block.getId(block);
        return ClientHooks.setBlock(world, x, y, z, id,
                () -> original.call(world, pos, state, flags));
    }
}

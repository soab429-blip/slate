package yt.ren.slate.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import yt.ren.slate.game.attack.AttackTargetCache;
import yt.ren.slate.module.ModuleRegistry;

public class ClientGameContext {
    public final ModuleRegistry registry;
    public final Object profiles;
    public final Minecraft client;
    public final ClientWorld world;
    public final LocalClientPlayerEntity player;
    public final byte[] keys;
    public final AttackTargetCache targetCache;
    public int focused;

    public ClientGameContext(ModuleRegistry registry, Object profiles, Minecraft client, ClientWorld world,
                             LocalClientPlayerEntity player, byte[] keys, AttackTargetCache targetCache) {
        this.registry = registry;
        this.profiles = profiles;
        this.client = client;
        this.world = world;
        this.player = player;
        this.keys = keys;
        this.targetCache = targetCache;
    }
}

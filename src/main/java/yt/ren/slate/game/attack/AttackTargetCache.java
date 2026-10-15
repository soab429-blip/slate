package yt.ren.slate.game.attack;

import net.minecraft.entity.living.player.PlayerEntity;

public class AttackTargetCache {
    public int flags;
    public PlayerEntity target;
    public double squaredDistance;
    public int slowdownMovesCloser;
}

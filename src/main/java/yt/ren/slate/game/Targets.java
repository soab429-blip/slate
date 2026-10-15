package yt.ren.slate.game;

import net.minecraft.entity.living.player.PlayerEntity;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class Targets {
    public static boolean playerAhead(ClientGameContext c) {
        Vec3 position = new Vec3(c.player.x, c.player.y, c.player.z);
        float yaw = RotationLifecycle.read(c.player).yaw;
        List<PlayerEntity> players = c.world.players;
        for (int index = 0; index < players.size(); index++) {
            PlayerEntity player = players.get(index);
            if (c.player == player) {
                continue;
            }
            Vec3 target = new Vec3(player.x, player.y, player.z);
            if (Geometry.distanceSquared(position, target) > 36.0) {
                continue;
            }
            float angle = Geometry.wrapYaw(Geometry.directionBetween(position, target).yaw - yaw);
            if (!(Math.abs(angle) > 90.0f)) {
                return true;
            }
        }
        return false;
    }
}

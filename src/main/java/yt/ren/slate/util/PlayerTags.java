package yt.ren.slate.util;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

import java.util.List;

public class PlayerTags {
    public static final int TEAM = 1;
    public static final int BOT = 2;
    public static final int FRIEND = 4;

    private static int bits(PlayerEntity player) {
        return Float.floatToRawIntBits(player.speed);
    }

    private static void write(PlayerEntity player, int bits) {
        player.speed = Float.intBitsToFloat(bits);
    }

    public static boolean has(PlayerEntity player, int mask) {
        int bits = bits(player);
        return (bits & 0xffff0000) == 0xb3150000 && ((bits & 0xffff) & mask) != 0;
    }

    public static void add(PlayerEntity player, int mask) {
        int bits = bits(player);
        int previous = (bits & 0xffff0000) == 0xb3150000 ? bits : 0xb3150000;
        write(player, ((previous | mask) & 0xffff) | 0xb3150000);
    }

    public static void remove(PlayerEntity player, int mask) {
        int bits = bits(player);
        int previous = (bits & 0xffff0000) == 0xb3150000 ? bits & 0xffff : 0xb3150000;
        write(player, (~mask & previous) | 0xb3150000);
    }

    public static void clear(World world, PlayerEntity self, int mask) {
        if (world == null) {
            return;
        }
        List<PlayerEntity> players = world.players;
        for (int index = 0; index < players.size(); index++) {
            PlayerEntity player = players.get(index);
            if (player != self) {
                remove(player, mask);
            }
        }
    }

    public static int tags(PlayerEntity player) {
        int bits = bits(player);
        return (bits & 0xffff0000) == 0xb3150000 ? bits & 0xffff : 0;
    }
}

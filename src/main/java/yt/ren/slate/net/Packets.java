package yt.ren.slate.net;

import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import yt.ren.slate.util.math.Sse;

public class Packets {
    public static final int PASS = 0;
    public static final int DELAY = 1;
    public static final int CANCEL = 2;
    public static final int IMMEDIATE = 3;

    public static boolean realtime(Object packet) {
        return packet instanceof EntityEventS2CPacket || packet instanceof PlayerHealthS2CPacket
                || packet instanceof SoundEventS2CPacket;
    }

    public static int delayMillis(float value) {
        return Sse.cvttss2si(value) & 0xFFFF;
    }
}

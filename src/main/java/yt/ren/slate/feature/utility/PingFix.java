package yt.ren.slate.feature.utility;

import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;

public class PingFix extends Module {
    public PingFix(ClientRuntime runtime) {
        super(ModuleId.PING_FIX, "Ping Fix", "Shows your real ping while using lag mods.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.PACKET_RECEIVE, e -> pass(e, e.packet instanceof KeepAliveS2CPacket), -1000);
        on(EventTypes.PACKET_SEND, e -> pass(e, e.packet instanceof KeepAliveC2SPacket), -1000);
    }

    private static void pass(PacketEvent event, boolean keepAlive) {
        if (keepAlive) {
            event.action = 3;
        }
    }
}

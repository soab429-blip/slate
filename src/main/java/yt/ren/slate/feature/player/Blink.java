package yt.ren.slate.feature.player;

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.net.Packets;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Sse;

public class Blink extends Module {
    private static final int INBOUND_ONLY = 0;
    private static final int OUTBOUND_ONLY = 1;

    private final ChoiceSetting direction = register("direction", new ChoiceSetting("Direction", 3, 0, new String[]{"In", "Out", "Both"}, 1, 0, 0));
    private final ScalarSetting maxDuration = register("max_duration", new ScalarSetting("Maximum duration", "%.2f s", 1.0f, 0.05f, 0.0f, 1000.0f, 100.0f, 0.0f));
    private final BooleanSetting disableOnAttack = register("disable_on_attack", new BooleanSetting("Attack", 0, 0));
    private final BooleanSetting disableOnBlockInteract = register("disable_on_block_interact", new BooleanSetting("Block interact", 0, 0));
    private final BooleanSetting disableOnBlockDig = register("disable_on_block_dig", new BooleanSetting("Block dig", 0, 0));

    private int lastAttack;
    private int remaining;
    private volatile int activeDirection;

    public Blink(ClientRuntime runtime) {
        super(ModuleId.BLINK, "Blink", "Simulates teleporting using lag.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()));
        on(EventTypes.PACKET_RECEIVE, this::filterInbound);
        on(EventTypes.PACKET_SEND, this::filterOutbound);
        on(EventTypes.MODULE_TOGGLE, this::toggled);
        on(EventTypes.CLICK_PRE, this::clicked);
    }

    @Override
    public String suffix() {
        return direction.selected();
    }

    private void update(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        int attack = c.player.lastAttackTime;
        boolean attacked = attack > lastAttack;
        lastAttack = attack;
        if (--remaining == 0 || (attacked && disableOnAttack.get())) {
            setEnabled(false);
        }
    }

    private void filterInbound(PacketEvent e) {
        if (activeDirection != OUTBOUND_ONLY) {
            e.action = Packets.realtime(e.packet) || e.packet instanceof ParticleS2CPacket ? Packets.IMMEDIATE : Packets.DELAY;
        }
    }

    private void filterOutbound(PacketEvent e) {
        if (activeDirection != INBOUND_ONLY) {
            e.action = Packets.DELAY;
        }
    }

    private void toggled(ToggleEvent e) {
        if (e.module == this && (e.state & 0xff) == 1) {
            remaining = (int) Sse.cvttss2si64(maxDuration.get());
            lastAttack = Integer.MAX_VALUE;
            activeDirection = direction.index();
        }
    }

    private void clicked(ClickEvent e) {
        BooleanSetting disable = (e.left & 0xff) == 1 ? disableOnBlockDig : disableOnBlockInteract;
        HitResult hit = e.game().client.crosshairTarget;
        if (disable.get() && hit != null && hit.type.ordinal() == 1) {
            setEnabled(false);
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(direction);
        w.show(maxDuration);
        w.heading("Disable on");
        w.show(disableOnAttack);
        w.show(disableOnBlockInteract);
        w.show(disableOnBlockDig);
    }
}

package yt.ren.slate.feature.player;

import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.net.Packets;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Sse;

public class FakeLag extends Module {
    private static final int PULSE = 1;

    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 2, 0, new String[]{"Static", "Pulse"}, 0, 0, 0));
    private final ScalarSetting inbound = register("inbound", new ScalarSetting("Inbound", "%.0f ms", 1.0f, 0.0f, 0.0f, 1000.0f, 50.0f, 0.0f));
    private final ScalarSetting outbound = register("outbound", new ScalarSetting("Outbound", "%.0f ms", 1.0f, 0.0f, 0.0f, 1000.0f, 50.0f, 0.0f));
    private final BooleanSetting realtimeDamage = register("realtime_damage", new BooleanSetting("Realtime damage", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireAttack = register("require_attack", new BooleanSetting("Attacked", 0, 0));
    private final BooleanSetting requireIngame = register("require_ingame", new BooleanSetting("In-game", 0, 0));

    private final ClientRuntime runtime;
    private int lastAttack;
    private int recentAttack;
    private int inboundCycle;
    private int outboundCycle;
    private volatile boolean passRealtime;
    private volatile boolean flushInbound;
    private volatile boolean flushOutbound;
    private volatile int inboundDelay;
    private volatile int outboundDelay;

    public FakeLag(ClientRuntime runtime) {
        super(ModuleId.FAKE_LAG, "Fake Lag", "Adds an artificial delay to your packets.", Category.UTILITY);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()));
        on(EventTypes.PACKET_RECEIVE, this::filter, 100);
        on(EventTypes.PACKET_SEND, this::filter, 100);
    }

    @Override
    public String suffix() {
        return mode.selected();
    }

    private int pulse(int cycle, ScalarSetting delay, boolean[] flush) {
        if (cycle > 0) {
            flush[0] = cycle < 51;
            return cycle - 50;
        }
        flush[0] = false;
        return Sse.cvttss2si((float) cycle + delay.get());
    }

    private boolean allowed(ClientGameContext c) {
        if (c.world == null || (requireIngame.get() && c.focused == 0)) {
            recentAttack = 0;
            return false;
        }
        int attack = c.player.lastAttackTime;
        boolean allowed = false;
        if (requireWeapon.get() && !HeldItem.holdingWeapon(runtime, c.player)) {
            recentAttack = 0;
        } else {
            allowed = !requireAttack.get() || recentAttack != 0;
        }
        if (attack != lastAttack) {
            recentAttack = 20;
        }
        lastAttack = attack;
        return allowed;
    }

    private void update(ClientGameContext c) {
        if (recentAttack > 0) {
            --recentAttack;
        }
        passRealtime = realtimeDamage.get();
        if (mode.is(PULSE)) {
            boolean[] flush = new boolean[1];
            inboundCycle = pulse(inboundCycle, inbound, flush);
            flushInbound = flush[0];
            outboundCycle = pulse(outboundCycle, outbound, flush);
            flushOutbound = flush[0];
        }
        if (allowed(c)) {
            inboundDelay = Packets.delayMillis(inbound.get());
            outboundDelay = Packets.delayMillis(outbound.get());
        } else {
            outboundDelay = 0;
            inboundDelay = 0;
        }
    }

    private void filter(PacketEvent e) {
        int delay;
        if (!e.outbound) {
            if (passRealtime && Packets.realtime(e.packet)) {
                e.action = Packets.IMMEDIATE;
                return;
            }
            if (flushInbound) {
                return;
            }
            delay = inboundDelay;
        } else {
            if (flushOutbound) {
                return;
            }
            delay = outboundDelay;
        }
        e.elapsed -= delay;
        if (e.elapsed < 0) {
            e.elapsed = 0;
            e.action = Packets.DELAY;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Delay");
        w.show(mode);
        w.show(inbound);
        w.show(outbound);
        w.show(realtimeDamage);
        w.heading("Conditions");
        w.show(requireWeapon);
        w.show(requireAttack);
        w.show(requireIngame);
    }
}

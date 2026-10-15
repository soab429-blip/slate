package yt.ren.slate.feature.combat;

import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.game.Controls;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.Targets;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.net.Packets;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;

public class KnockbackDelay extends Module {
    private final ScalarSetting range = register("range", new ScalarSetting("Distance to target", "%.1f blocks", 0.0f, 0.0f, 2.0f, 8.0f, 4.0f, 0.0f));
    private final ScalarSetting chance = register("chance", new ScalarSetting("Chance", "%.0f%%", 0.01f, 100.0f, 0.0f, 1.0f, 1.0f, 0.0f));
    private final ScalarSetting maxDelay = register("max_delay", new ScalarSetting("Maximum delay", "%.0f ms", 0.0f, 0.0f, 25.0f, 1000.0f, 50.0f, 0.0f));
    private final BooleanSetting requireAir = register("require_air", new BooleanSetting("In air", 1, 0));
    private final BooleanSetting requireTarget = register("require_target", new BooleanSetting("Looking at player", 0, 0));
    private final BooleanSetting requireMouse = register("require_mouse", new BooleanSetting("Mouse pressed", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;
    private final TimedNoise probability = new TimedNoise();
    private boolean waiting;
    private int velocityPacketId;
    private volatile boolean grounded;
    private volatile boolean eligible;
    private volatile int delayLimit;
    private volatile int playerId;

    public KnockbackDelay(ClientRuntime runtime) {
        super(ModuleId.KNOCKBACK_DELAY, "Knockback Delay", "Delays your knockback in certain scenarios.", Category.COMBAT);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.PACKET_RECEIVE, this::filter);
        on(EventTypes.UPDATE_POST, e -> update(e.game()));
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.0fms", (double) maxDelay.get());
    }

    private void filter(PacketEvent e) {
        if (e.action != Packets.PASS) {
            return;
        }
        boolean own = e.packet instanceof EntityVelocityS2CPacket && ((EntityVelocityS2CPacket) e.packet).id == playerId;
        if (grounded) {
            waiting = e.elapsed == 0 && eligible;
        } else if (eligible && own) {
            waiting = true;
        }
        boolean flush = false;
        if (own && waiting) {
            if (velocityPacketId == 0) {
                velocityPacketId = e.id;
            } else if (velocityPacketId != e.id) {
                flush = true;
            }
        } else if (!waiting) {
            return;
        }
        if (!flush && e.elapsed <= (long) delayLimit) {
            e.action = Packets.realtime(e.packet) ? Packets.IMMEDIATE : Packets.DELAY;
            return;
        }
        waiting = false;
        eligible = false;
        velocityPacketId = 0;
    }

    private boolean conditionsMet(ClientGameContext c) {
        if (c.targetCache.target == null) {
            return false;
        }
        float reach = range.get();
        if (c.targetCache.squaredDistance > (double) (reach * reach)) {
            return false;
        }
        return (!requireWeapon.get() || HeldItem.holdingWeapon(runtime, c.player))
                && (!requireMouse.get() || Controls.attackPressed(c))
                && (!requireTarget.get() || Targets.playerAhead(c));
    }

    private void update(ClientGameContext c) {
        if (c.player == null) {
            return;
        }
        playerId = c.player.networkId;
        grounded = requireAir.get() && c.player.onGround;
        boolean isEligible = conditionsMet(c);
        double odds = chance.get();
        if (isEligible && !(odds > 0.999)) {
            isEligible = probability.belowSample(runtime.random, runtime.frameTime[0], (float) (-0.025 + odds),
                    (float) (odds + 0.025), 60.0);
        }
        eligible = isEligible;
        delayLimit = Packets.delayMillis(maxDelay.get());
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(range);
        w.show(chance);
        w.show(maxDelay);
        w.heading("Conditions");
        w.show(requireAir);
        w.show(requireTarget);
        w.show(requireMouse);
        w.show(requireWeapon);
    }
}

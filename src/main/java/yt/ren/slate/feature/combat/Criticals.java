package yt.ren.slate.feature.combat;

import net.minecraft.client.TickTimer;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.FrameEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.game.Controls;
import yt.ren.slate.game.HeldItem;
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

public class Criticals extends Module {
    private static final int PACKET = 0;
    private static final int TIMER = 1;

    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 2, 0, new String[]{"Packet", "Timer"}, 0, 0, 0));
    private final ScalarSetting chance = register("chance", new ScalarSetting("Chance", "%.0f%%", 0.01f, 100.0f, 0.0f, 1.0f, 1.0f, 0.0f));
    private final ScalarSetting maxDelay = register("max_delay", new ScalarSetting("Maximum delay", "%.0f ms", 1.0f, 0.0f, 50.0f, 300.0f, 100.0f, 0.0f));
    private final ScalarSetting timerSpeed = register("timer_speed", new ScalarSetting("Timer speed", "%.2fx", 0.01f, 0.0f, 0.5f, 1.0f, 0.85f, 0.0f));
    private final ScalarSetting boost = register("boost", new ScalarSetting("Boost", "%.0f ms", 1.0f, 50.0f, 0.0f, 5.0f, 0.0f, 0.0f));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireMousePressed = register("require_mouse_pressed", new BooleanSetting("Mouse pressed", 0, 0));

    private final ClientRuntime runtime;
    private volatile boolean delayEnabled;
    private volatile int delayLimit;
    private boolean slowTimer;
    private int chanceTicks;
    private boolean chancePassed;
    private boolean timerModified;
    private double credit;
    private int packetDelay;
    private double previousY;
    private int packetId;
    private boolean falling;

    public Criticals(ClientRuntime runtime) {
        super(ModuleId.CRITICALS, "Criticals", "Increases chance of landing critical hits.", Category.COMBAT);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()));
        on(EventTypes.FRAME, this::updateTimer, 100);
        on(EventTypes.PACKET_SEND, this::filter);
        on(EventTypes.MODULE_TOGGLE, this::toggled);
    }

    @Override
    public String suffix() {
        return mode.selected();
    }

    private boolean rollChance() {
        if (chanceTicks == 0) {
            chanceTicks = 10;
            chancePassed = runtime.random.below(chance.get());
        }
        --chanceTicks;
        return chancePassed;
    }

    private boolean allowed(ClientGameContext c) {
        if (c.focused != 1 || !rollChance()) {
            return false;
        }
        if (requireMousePressed.get() && !Controls.attackPressed(c)) {
            return false;
        }
        if (requireWeapon.get() && !HeldItem.holdingWeapon(runtime, c.player)) {
            return false;
        }
        return c.player.ticks - c.player.lastAttackTime < 11;
    }

    private static boolean fallingOntoEntity(ClientGameContext c) {
        if (c.player.onGround || c.player.velocityY >= 0.0 || c.player.damagedTimer == 10) {
            return false;
        }
        HitResult hit = c.client.crosshairTarget;
        return hit != null && hit.type.ordinal() == 2;
    }

    private void update(ClientGameContext c) {
        boolean allowed = allowed(c);
        if (mode.is(PACKET)) {
            delayEnabled = allowed;
            delayLimit = Packets.delayMillis(maxDelay.get());
            slowTimer = false;
        } else if (mode.is(TIMER)) {
            delayEnabled = false;
            slowTimer = allowed && fallingOntoEntity(c);
        }
    }

    private void updateTimer(FrameEvent e) {
        TickTimer timer = e.game().client.timer;
        if (slowTimer) {
            timer.tpsScale = timerSpeed.get();
            timerModified = true;
            credit += (double) (1.0f - timerSpeed.get()) * (e.elapsed * 20.0);
            credit = Math.min(credit, (double) boost.get());
            return;
        }
        if (credit > 1.0) {
            int ticks = Sse.cvttsd2si(credit);
            timer.tickDelta = (float) ticks + timer.tickDelta;
            credit -= (double) ticks;
        }
        if (timerModified) {
            timer.tpsScale = 1.0f;
            timerModified = false;
        }
    }

    private void filter(PacketEvent e) {
        if (e.packet instanceof PlayerMoveC2SPacket) {
            PlayerMoveC2SPacket movement = (PlayerMoveC2SPacket) e.packet;
            if (movement.hasPos && e.id > packetId) {
                double y = movement.minY;
                if (movement.onGround || previousY > y) {
                    falling = !movement.onGround;
                }
                packetDelay = delayEnabled && falling ? Math.min(packetDelay + delayLimit / 5, delayLimit) : 0;
                previousY = y;
                packetId = e.id;
            }
        }
        if (e.elapsed < (long) packetDelay) {
            e.action = Packets.DELAY;
        }
    }

    private void toggled(ToggleEvent e) {
        if (e.module == this && (e.state & 0xff) == 0 && timerModified) {
            ((ClientGameContext) e.context).client.timer.tpsScale = 1.0f;
            timerModified = false;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(mode);
        w.show(chance);
        if (mode.is(PACKET)) {
            w.show(maxDelay);
        } else {
            w.show(timerSpeed);
            w.show(boost);
        }
        w.heading("Conditions");
        w.show(requireWeapon);
        w.show(requireMousePressed);
    }
}

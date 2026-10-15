package yt.ren.slate.feature.combat;

import net.minecraft.block.Block;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.NonVirtualSwing;
import yt.ren.slate.util.math.Sse;

public class HitSelect extends Module {
    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 2, 0, new String[]{"Burst", "Criticals"}, 0, 0, 0));
    private final ScalarSetting cancelRate = register("cancel_rate", new ScalarSetting("In combat", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.8f, 0.0f));
    private final ScalarSetting cancelRateAir = register("cancel_rate_air", new ScalarSetting("Missed swings", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.4f, 0.0f));
    private final ScalarSetting pauseDur = register("pause_dur", new ScalarSetting("Pause duration", "%.0f ms", 0.001f, 50.0f, 0.0f, 10.0f, 10.0f, 0.0f));
    private final ScalarSetting waitForDamageDur = register("wait_for_damage_dur", new ScalarSetting("Wait for first hit", "%.0f ms", 0.001f, 50.0f, 0.0f, 8.0f, 0.0f, 0.0f));
    private final ScalarSetting tradesDelay = register("trades_delay", new ScalarSetting("Hit later in trades", "%.0f ms", 0.001f, 50.0f, 0.0f, 8.0f, 0.0f, 0.0f));
    private final BooleanSetting useServerTime = register("use_server_time", new BooleanSetting("Use server attack time", 0, 0));
    private final BooleanSetting fakeSwing = register("fake_swing", new BooleanSetting("Fake swing", 0, 0));
    private final BooleanSetting critsAllowReduce = register("crits_allow_reduce", new BooleanSetting("Disable during knockback", 1, 0));
    private final BooleanSetting requireDamage = register("require_damage", new BooleanSetting("Only while damaged", 1, 0));
    private final BooleanSetting requireSprint = register("require_sprint", new BooleanSetting("Sprinting", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private int decided;
    private int undamagedTicks;
    private int cancel;
    private int selectAttackTicks;
    private int waitingHits;
    private int serverReset;
    private int selectTargetId;

    public HitSelect(ClientRuntime runtime) {
        super(ModuleId.HIT_SELECT, "Hit Select", "Perfectly times your hits.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> beginHitSelectFrame(e.game()));
        on(EventTypes.CLICK_PRE, this::beforeHitSelectAttack, -100);
        on(EventTypes.UPDATE_ROTATION, e -> updateHitSelectClock(e.game()));
    }

    @Override
    public String suffix() {
        return mode.selected();
    }

    private int randomizedLow32(float value) {
        long wide = Sse.cvttss2si64(value);
        int integral = (int) wide;
        float converted = (float) (integral & 0xFFFFFFFFL);
        float fraction = value - converted;
        return integral + (random.below(fraction) ? 1 : 0);
    }

    private void cancelHit(ClickEvent e, ClientGameContext c) {
        e.cancelled = 1;
        if (fakeSwing.get() && c.player != null && !c.player.hasItemInUse()) {
            ((NonVirtualSwing) c.player).slate$swingNonVirtual();
        }
    }

    void beginHitSelectFrame(ClientGameContext c) {
        decided = 0;
        int count = 0;
        if (c.focused == 1 && c.player.damagedTimer == 0) {
            count = undamagedTicks + 1;
        }
        undamagedTicks = count;
    }

    private void allow() {
        cancel = 0;
        decided = 1;
    }

    private void cancelNow(ClickEvent e, ClientGameContext c) {
        cancel = 1;
        decided = 1;
        cancelHit(e, c);
    }

    void beforeHitSelectAttack(ClickEvent e) {
        if ((e.left & 0xff) != 1) {
            return;
        }
        ClientGameContext c = e.game();
        if (decided == 1) {
            if (cancel != 0) {
                cancelHit(e, c);
            }
            return;
        }
        if (requireSprint.get() && !c.player.isSprinting()) {
            allow();
            return;
        }
        if (requireWeapon.get()
                && !HeldItem.holdingWeapon(runtime, c.player)) {
            allow();
            return;
        }
        HitResult hit = c.client.crosshairTarget;
        boolean noEntity = true;
        if (hit != null) {
            int kind = hit.type.ordinal();
            if (kind == 2) {
                noEntity = hit.entity == null;
            } else if (kind == 1) {
                allow();
                return;
            } else if (kind == 0 && random.below(cancelRateAir.get())) {
                cancelNow(e, c);
                return;
            }
        }
        int pause = randomizedLow32(pauseDur.get());
        boolean onGround = c.player.onGround;
        boolean rising = true;
        if (onGround) {
            rising = c.player.velocityY > 0.0;
        }
        int closer = (rising ? 1 : 0) & c.targetCache.slowdownMovesCloser;
        int selected = mode.index();
        if (selected == 1) {
            int cycle = (Integer.compareUnsigned(selectAttackTicks - 9, 0xfffffff9) < 0 ? 1 : 0)
                    | (useServerTime.get() ? 1 : 0);
            boolean falling = false;
            if (!onGround) {
                falling = c.player.fallDistance > 0.0f;
                if (((falling ? 1 : 0) & cycle) != 0) {
                    allow();
                    return;
                }
            }
            if (closer != 0 && (cycle == 0 || critsAllowReduce.get())) {
                allow();
                return;
            }
            int x = Sse.cvttsd2si(Math.floor(c.player.x));
            int y = Sse.cvttsd2si(Math.floor(c.player.y));
            int z = Sse.cvttsd2si(Math.floor(c.player.z));
            Block block = c.world.getBlockState(new BlockPos(x, y, z)).getBlock();
            int id = Block.getId(block);
            if (id != 0) {
                allow();
                return;
            }
            if (!onGround && !falling
                    && !(requireDamage.get() && c.player.damagedTimer == 0)) {
                cancelNow(e, c);
                return;
            }
        } else if (selected == 0 && closer == 0) {
            if (Integer.compareUnsigned(selectAttackTicks, 15) < 0) {
                waitingHits = 0;
            } else if (!noEntity) {
                int count = ++waitingHits;
                if (Integer.compareUnsigned(count, randomizedLow32(waitForDamageDur.get())) <= 0) {
                    cancelNow(e, c);
                    return;
                }
            }
            if (Integer.compareUnsigned(undamagedTicks, 10) < 0) {
                pause += randomizedLow32(tradesDelay.get());
            }
            if (serverReset == 1) {
                serverReset = 0;
                allow();
                return;
            }
        } else {
            allow();
            return;
        }
        if (Integer.compareUnsigned(selectAttackTicks + 1, pause) < 0) {
            cancel = random.below(cancelRate.get()) ? 1 : 0;
            decided = 1;
            if (cancel != 0) {
                cancelHit(e, c);
            }
            return;
        }
        allow();
    }

    void updateHitSelectClock(ClientGameContext c) {
        PlayerEntity target = c.targetCache.target;
        if (useServerTime.get()) {
            if (target != null && target.damagedTimer == 10) {
                selectAttackTicks = 0;
                serverReset = 1;
                return;
            }
            ++selectAttackTicks;
            return;
        }
        int id = target != null ? target.networkId : 0;
        if (id != selectTargetId) {
            selectTargetId = id;
            selectAttackTicks = 1001;
        } else if (Integer.compareUnsigned(++selectAttackTicks, 10) < 0) {
            return;
        }
        int tick = c.player.ticks;
        if (tick == c.player.lastAttackTime) {
            selectAttackTicks = 0;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(mode);
        w.show(pauseDur);
        if (mode.is(0)) {
            w.show(waitForDamageDur);
            w.show(tradesDelay);
        }
        w.show(useServerTime);
        w.show(fakeSwing);
        if (mode.is(1)) {
            w.show(critsAllowReduce);
            w.show(requireDamage);
        }
        w.heading("Cancel rate");
        w.show(cancelRate);
        w.show(cancelRateAir);
        w.heading("Conditions");
        w.show(requireSprint);
        w.show(requireWeapon);
    }
}

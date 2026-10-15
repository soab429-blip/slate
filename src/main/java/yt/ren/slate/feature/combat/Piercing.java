package yt.ren.slate.feature.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.MouseOver;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;

public class Piercing extends Module {
    private final ChoiceSetting sortBy = register("sort_by", new ChoiceSetting("Sort targets by", 2, 0, new String[]{"Hurt time", "Health"}, 0, 0, 0));
    private final BooleanSetting nonPlayers = register("non_players", new BooleanSetting("Non-player entities", 1, 0));
    private final BooleanSetting friendlies = register("friendlies", new BooleanSetting("Friendlies", 1, 0));
    private final BooleanSetting bots = register("bots", new BooleanSetting("Bots", 1, 0));
    private final BooleanSetting enemies = register("enemies", new BooleanSetting("Enemies", 1, 0));
    private final BooleanSetting blocks = register("blocks", new BooleanSetting("Blocks", 1, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireInsideBb = register("require_inside_bb", new BooleanSetting("Inside entity hitbox", 0, 0));

    private final ClientRuntime runtime;

    public Piercing(ClientRuntime runtime) {
        super(ModuleId.PIERCING, "Piercing", "Allows hitting entities through obstructions.", Category.COMBAT);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.MOUSE_OVER, MouseOver::requestCustomMouseOver);
    }

    private static final int TARGET = 0;
    private static final int PIERCE = 1;
    private static final int BLOCKED = 2;

    private static int tieHash(PlayerEntity player) {
        int value = player.ticks + player.networkId;
        value = ((value >>> 16) ^ value) * 0x45d9f3b;
        value = ((value >>> 16) ^ value) * 0x45d9f3b;
        return (value >>> 16) ^ value;
    }

    private boolean weaponAllowed(ClientGameContext c) {
        return !requireWeapon.get() || HeldItem.holdingWeapon(runtime, c.player);
    }

    public int rank(ClientGameContext c, Entity previous, Entity candidate, boolean inside) {
        if (!isEnabled() || (!inside && requireInsideBb.get()) || !weaponAllowed(c)) {
            return BLOCKED;
        }
        PlayerEntity next = MouseOver.asPlayer(candidate);
        if (next == null) {
            return nonPlayers.get() ? PIERCE : BLOCKED;
        }
        int marked = PlayerTags.tags(next);
        if ((friendlies.get() && (marked & 5) != 0) || (bots.get() && (marked & 2) != 0)) {
            return PIERCE;
        }
        PlayerEntity current = MouseOver.asPlayer(previous);
        if (current == null) {
            return TARGET;
        }
        if (!enemies.get()) {
            return BLOCKED;
        }
        if (sortBy.is(1)) {
            float oldHealth = AttackPrediction.health(current);
            float newHealth = AttackPrediction.health(next);
            if (oldHealth != newHealth) {
                return oldHealth < newHealth ? PIERCE : TARGET;
            }
        } else if (sortBy.is(0) && current.damagedTimer != next.damagedTimer) {
            return current.damagedTimer < next.damagedTimer ? PIERCE : TARGET;
        }
        return tieHash(next) <= tieHash(current) ? PIERCE : TARGET;
    }

    public boolean ignoresBlocks(ClientGameContext c) {
        return isEnabled() && blocks.get() && weaponAllowed(c);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Always hit through");
        w.show(sortBy);
        w.show(nonPlayers);
        w.show(friendlies);
        w.show(bots);
        w.show(enemies);
        w.show(blocks);
        w.heading("Conditions");
        w.show(requireWeapon);
        w.show(requireInsideBb);
    }
}

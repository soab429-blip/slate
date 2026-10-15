package yt.ren.slate.feature.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;

public class AutoWeapon extends Module {
    private final ScalarSetting activationTime = register("activation_time", new ScalarSetting("Activation time", "%.0f ms", 1.0f, 50.0f, 0.0f, 5.0f, 2.0f, 0.0f));
    private final BooleanSetting requireMousePressed = register("require_mouse_pressed", new BooleanSetting("Mouse pressed", 0, 0));

    private final ClientRuntime runtime;
    private int activationTicks;

    public AutoWeapon(ClientRuntime runtime) {
        super(ModuleId.AUTO_WEAPON, "Auto Weapon", "Selects your weapon when aiming on players.", Category.COMBAT);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.CONTROLLER, e -> update(e.game()));
    }

    private static boolean attackMousePressed(ClientGameContext c) {
        int code = c.client.options.keyBindings[0].keyCode;
        return c.keys[code == -99 ? 2 : 1] != 0;
    }

    private int weaponSlot(ClientGameContext c) {
        if (c.focused != 1 || c.player.hasItemInUse()) {
            return -1;
        }
        if (requireMousePressed.get() && !attackMousePressed(c)) {
            return -1;
        }
        HitResult hit = c.client.crosshairTarget;
        if (hit == null || hit.type.ordinal() != 2) {
            return -1;
        }
        Entity entity = hit.entity;
        if (!(entity instanceof PlayerEntity) || PlayerTags.has((PlayerEntity) entity, 7)) {
            return -1;
        }
        if (HeldItem.holdingWeapon(runtime, c.player)) {
            return -1;
        }
        PlayerInventory inventory = c.player.inventory;
        int candidate = runtime.module(Weapons.class).findSlot(inventory);
        return candidate == inventory.selectedSlot ? -1 : candidate;
    }

    private void update(ClientGameContext c) {
        int candidate = weaponSlot(c);
        if (candidate == -1) {
            activationTicks = 0;
            return;
        }
        if ((float) activationTicks < activationTime.get()) {
            activationTicks++;
            return;
        }
        if (ModuleRegistry.claimOwner(runtime.owner, this)) {
            c.player.inventory.selectedSlot = candidate;
            ModuleRegistry.releaseOwner(runtime.owner, this);
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(activationTime);
        w.heading("Conditions");
        w.show(requireMousePressed);
    }
}

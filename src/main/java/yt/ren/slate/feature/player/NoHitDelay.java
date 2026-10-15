package yt.ren.slate.feature.player;

import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.NonVirtualSwing;

public class NoHitDelay extends Module {
    private static final int LEGIT = 1;

    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 2, 0, new String[]{"Regular", "Legit"}, 0, 0, 0));

    public NoHitDelay(ClientRuntime runtime) {
        super(ModuleId.NO_HIT_DELAY, "No Hit Delay", "Removes 1.8 swing delay.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.CLICK_PRE, this::clicked);
    }

    @Override
    public String suffix() {
        return mode.selected();
    }

    public boolean legit() {
        return mode.is(LEGIT);
    }

    private void clicked(ClickEvent event) {
        if (event.left != 1) {
            return;
        }
        ClientGameContext c = event.game();
        if (c.client.attackCooldown <= 0) {
            return;
        }
        HitResult hit = c.client.crosshairTarget;
        if (!legit() || (hit != null && hit.type.ordinal() != 0)) {
            c.client.attackCooldown = 0;
        } else if (!c.player.hasItemInUse()) {
            ((NonVirtualSwing) c.player).slate$swingNonVirtual();
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("In-game");
        w.show(mode);
    }
}

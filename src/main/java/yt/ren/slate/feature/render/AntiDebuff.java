package yt.ren.slate.feature.render;

import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class AntiDebuff extends Module {
    private final BooleanSetting blindness = register("blindness", new BooleanSetting("Blindness", 1, 0));
    private final BooleanSetting nausea = register("nausea", new BooleanSetting("Nausea", 1, 0));

    public AntiDebuff(ClientRuntime runtime) {
        super(ModuleId.ANTI_DEBUFF, "Anti Debuff", "Removes negative visual effects.", Category.VISUAL);
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_POST, e -> update(e.game()));
    }

    private void update(ClientGameContext c) {
        if (blindness.get()) {
            StatusEffectInstance effect = c.player.getEffectInstance(StatusEffect.BY_ID[15]);
            if (effect != null) {
                effect.duration = 0;
            }
        }
        if (nausea.get()) {
            c.player.portalTime = 0.0f;
            c.player.lastPortalTime = 0.0f;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Effects");
        w.show(blindness);
        w.show(nausea);
    }
}

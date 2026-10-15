package yt.ren.slate.feature.world;

import net.minecraft.client.ClientPlayerInteractionManager;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.Sse;

public class FastMine extends Module {
    private final ScalarSetting speed = register("speed", new ScalarSetting("Speed", "%.2fx", 0.01f, 0.0f, 1.0f, 2.0f, 1.0f, 0.0f));
    private final ScalarSetting breakDelay = register("break_delay", new ScalarSetting("Break delay", "%.0f ms", 1.0f, 50.0f, 0.0f, 5.0f, 5.0f, 0.0f));
    private final BooleanSetting decreaseHitDelay = register("decr_block_hit_delay", new BooleanSetting("Always decrease break delay", 0, 0));

    private int previousDelay;
    private float previousProgress;

    public FastMine(ClientRuntime runtime) {
        super(ModuleId.FAST_MINE, "Fast Mine", "Increases block mining speed.", Category.BLOCK);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> limitDelay(e.game()));
        on(EventTypes.UPDATE_POST, e -> accelerate(e.game()));
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.2fx", (double) speed.get());
    }

    private void limitDelay(ClientGameContext c) {
        ClientPlayerInteractionManager controller = c.client.interactionManager;
        if (controller == null) {
            previousDelay = 0;
            return;
        }
        previousProgress = controller.miningProgress;
        int delay = controller.miningCooldown;
        float limit = breakDelay.get();
        if ((float) delay > limit) {
            controller.miningCooldown = Sse.cvttss2si(limit);
        } else if (delay > 0 && decreaseHitDelay.get() && delay == previousDelay) {
            controller.miningCooldown = delay - 1;
        }
        previousDelay = delay;
    }

    private void accelerate(ClientGameContext c) {
        ClientPlayerInteractionManager controller = c.client.interactionManager;
        if (controller == null) {
            return;
        }
        float progress = controller.miningProgress;
        if (progress == 0.0f || previousProgress != 0.0f) {
            return;
        }
        long normalTicks = Sse.cvttss2si64(ScalarRounding.ceilGameFloat(1.0f / progress));
        double scaled = 1.0 / (double) speed.get() / (double) progress;
        long fastTicks = Sse.cvttss2si64(ScalarRounding.ceilGameFloat((float) scaled));
        int count = (int) normalTicks - (int) fastTicks + 1;
        controller.miningProgress = (float) Integer.toUnsignedLong(count) * progress;
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(speed);
        w.show(breakDelay);
        w.show(decreaseHitDelay);
    }
}

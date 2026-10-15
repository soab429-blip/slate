package yt.ren.slate.feature.player;

import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;

public abstract class AutomaticItemModule extends Module {
    protected final ClientRuntime runtime;
    protected final AimRandom random;

    protected AutomaticItemModule(ModuleId id, String name, String description, Category category, ClientRuntime runtime) {
        super(id, name, description, category);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    protected float noise(TimedNoise state, float minimum, float maximum, double interval) {
        return state.sample(random, runtime.frameTime[0], minimum, maximum, interval);
    }

    protected boolean chance(TimedNoise state, float minimum, float maximum, double interval) {
        return state.belowSample(random, runtime.frameTime[0], minimum, maximum, interval);
    }

    protected static int itemId(ItemStack stack) {
        return Item.getId(stack.getItem());
    }

    protected static void actionPress(ClientGameContext c, boolean drop, boolean increment) {
        KeyBinding key = c.client.options.keyBindings[drop ? 9 : 1];
        key.clickCount = increment ? key.clickCount + 1 : 1;
    }

    protected void requestDisable(ClientGameContext c) {
        c.registry.queueModuleChange(this, 0);
    }

    protected float shortRandomDelay(float base, TimedNoise delayNoise, TimedNoise probability) {
        float result = Math.max(0.0f, base - noise(delayNoise, 0.0f, 0.5f, 5.0));
        if (chance(probability, 0.01f, 0.03f, 30.0)) {
            result += random.range(0.5f, 1.0f);
        }
        return result;
    }
}

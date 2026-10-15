package yt.ren.slate.feature.world;

import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.feature.player.ItemUse;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;

public class FastPlace extends Module {
    private static final double[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private final ScalarSetting maxCps = register("max_cps", new ScalarSetting("Maximum CPS", "%0.1f CPS", 0.1f, 0.0f, 10.0f, 50.0f, 20.0f, 0.0f));
    private final ScalarSetting activateDelay = register("activate_delay", new ScalarSetting("Activate delay", "%0.0f ms", 1.0f, 50.0f, 0.0f, 10.0f, 0.0f, 0.0f));

    private final ClientRuntime runtime;
    private final TimedNoise noise = new TimedNoise();
    private float credit;
    private boolean previousPhysical;
    private int activationCountdown;

    public FastPlace(ClientRuntime runtime) {
        super(ModuleId.FAST_PLACE, "Fast Place", "Allows placing blocks faster.", Category.BLOCK);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()), -1000);
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.1f", (double) maxCps.get());
    }

    private static Vec3 adjacentBlock(HitResult hit) {
        BlockPos pos = hit.pos;
        double[] d = DIRECTIONS[hit.face.ordinal()];
        return new Vec3((double) pos.getX() + d[0], (double) pos.getY() + d[1], (double) pos.getZ() + d[2]);
    }

    private boolean canPlace(ClientGameContext c, double now) {
        HitResult hit = c.client.crosshairTarget;
        if (hit != null && hit.type.ordinal() == 1) {
            Item current = HeldItem.selected(c.player.inventory);
            if (current != null && Item.getId(current) == 0x41) {
                return true;
            }
            Vec3 adjacent = adjacentBlock(hit);
            Bounds block = new Bounds(adjacent, adjacent.add(new Vec3(1.0, 1.0, 1.0)));
            Box box = c.player.getShape();
            Bounds player = new Bounds(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
            if (!Geometry.overlaps(block, player)) {
                return true;
            }
        }
        return noise.belowSample(runtime.random, now, 0.02f, 0.08f, 20.0);
    }

    private void update(ClientGameContext c) {
        if (credit > 0.0f) {
            credit -= 1.0f;
            if (credit > 0.0f) {
                return;
            }
        }
        if (c.focused != 1) {
            return;
        }
        KeyBinding use = c.client.options.keyBindings[1];
        boolean physical = PhysicalKeys.pressed(use);
        boolean previous = previousPhysical;
        previousPhysical = physical;
        if (!physical) {
            activationCountdown = Sse.cvttss2si(activateDelay.get());
            return;
        }
        if (activationCountdown > 0) {
            --activationCountdown;
            return;
        }
        if (use.clickCount > 0 || credit > 0.0f) {
            return;
        }
        boolean skip = !previous;
        double now = runtime.frameTime[0];
        do {
            credit = (float) ((double) credit + 20.0 / (double) maxCps.get());
            if (!skip && ItemUse.itemIsNonAirBlock(HeldItem.selected(c.player.inventory)) && canPlace(c, now)) {
                use.clickCount++;
            }
            skip = false;
        } while (credit <= 0.0f);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(maxCps);
        w.show(activateDelay);
    }
}

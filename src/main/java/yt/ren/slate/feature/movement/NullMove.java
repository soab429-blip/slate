package yt.ren.slate.feature.movement;

import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;

public class NullMove extends Module {

    private final int[] nullMovePressedTicks = new int[4];
    public NullMove(ClientRuntime runtime) {
        super(ModuleId.NULL_MOVE, "Null Move", "Makes it easier to switch move direction instantly.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_PRE, this::updateNullMove, 100);
    }
    void updateNullMove(GameEvent event) {
        ClientGameContext c = event.game();
        if (!c.client.focused) {
            return;
        }
        int tick = c.player.ticks;
        GameOptions options = c.client.options;
        KeyBinding[] keys = {Motion.key(options, 2), Motion.key(options, 3), Motion.key(options, 4), Motion.key(options, 5)};
        for (int i = 0; i < keys.length; ++i) {
            int old = nullMovePressedTicks[i];
            boolean down = keys[i].pressed;
            KeyBinding opposite = keys[(i + 2) % 4];
            boolean oppositeDown = opposite.pressed;
            int next = tick;
            if (down) {
                if (old == 0 && oppositeDown) {
                    opposite.pressed = false;
                }
            } else {
                next = 0;
                if (old != 0 && PhysicalKeys.pressed(opposite)) {
                    opposite.pressed = true;
                }
            }
            nullMovePressedTicks[i] = next;
        }
    }

}

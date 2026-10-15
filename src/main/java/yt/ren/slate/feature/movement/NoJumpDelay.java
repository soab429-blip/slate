package yt.ren.slate.feature.movement;

import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;

public class NoJumpDelay extends Module {
    public NoJumpDelay(ClientRuntime runtime) {
        super(ModuleId.NO_JUMP_DELAY, "No Jump Delay", "Removes cooldown when holding jump key.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_ROTATION, this::updateNoJumpDelay, 100);
    }
    void updateNoJumpDelay(GameEvent event) {
        ClientGameContext c = event.game();
        if (c.focused == 1) {
            c.player.jumpingCooldown = 0;
        }
    }

}

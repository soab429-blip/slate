package yt.ren.slate.feature.player;

import net.minecraft.client.ClientPlayerInteractionManager;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;

public class ItemUseFix extends Module {
    private boolean wasMining;

    public ItemUseFix(ClientRuntime runtime) {
        super(ModuleId.ITEM_USE_FIX, "Item Use Fix", "Allows using items while breaking blocks.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.CLICK_PRE, this::beforeUse);
        on(EventTypes.CLICK_POST, this::afterUse);
    }

    private void beforeUse(ClickEvent event) {
        if (event.left != 0) {
            return;
        }
        ClientPlayerInteractionManager controller = event.game().client.interactionManager;
        wasMining = controller.isMiningBlock;
        if (wasMining) {
            controller.isMiningBlock = false;
        }
    }

    private void afterUse(ClickEvent event) {
        if (wasMining && event.left == 0) {
            event.game().client.interactionManager.isMiningBlock = true;
        }
    }
}

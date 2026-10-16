package yt.ren.slate.feature.visual;

import net.minecraft.client.options.GameOptions;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.RotationRequestEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.structs.Rotation;

public class FreeLook extends Module {
    private final ChoiceSetting targetPerspective = register("target_perspective", new ChoiceSetting("Switch to", 3, 0, new String[]{"First person", "Third person", "Third person (reverse)"}, 1, 0, 0));

    private final ClientRuntime runtime;
    private boolean restore;
    private Rotation saved = new Rotation(0.0f, 0.0f);
    private int perspective;

    public FreeLook(ClientRuntime runtime) {
        super(ModuleId.FREE_LOOK, "Free Look", "Freely move the camera around.", Category.VISUAL);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, this::toggled);
        on(EventTypes.ROTATION_REQUEST, this::lockRotation, -1000);
    }

    private void toggled(ToggleEvent event) {
        if (event.module != this) {
            return;
        }
        ClientGameContext c = (ClientGameContext) event.context;
        if (c.world == null) {
            return;
        }
        RotationLifecycle rotation = runtime.rotation;
        GameOptions options = c.client.options;
        if (event.state == 1) {
            restore = true;
            saved = rotation.displayRotationAvailable() ? rotation.displayRotation() : RotationLifecycle.read(c.player);
        }
        if (event.state != 0) {
            perspective = options.perspective;
            options.perspective = targetPerspective.index();
            return;
        }
        options.perspective = perspective;
        if (restore) {
            rotation.changeDisplayRotation(saved);
            restore = false;
        }
    }

    private void lockRotation(RotationRequestEvent event) {
        event.enabled = 1;
        event.angles = saved;
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(targetPerspective);
    }
}

package yt.ren.slate.feature.visual;

import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;

public class NoHurtCam extends Module {
    public NoHurtCam(ClientRuntime runtime) {
        super(ModuleId.NO_HURT_CAM, "No Hurt Cam", "Removes camera shake when taking damage.", Category.VISUAL);
    }
}

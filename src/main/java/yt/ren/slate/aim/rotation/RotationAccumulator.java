package yt.ren.slate.aim.rotation;

import yt.ren.slate.util.structs.Rotation;

public class RotationAccumulator {
    public Rotation delta = new Rotation(0.0f, 0.0f);
    public int flag;
}

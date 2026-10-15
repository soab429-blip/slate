package yt.ren.slate.aim.rotation;

import yt.ren.slate.util.structs.Rotation;

public class SharedRotation {
    public int priority;
    public final RotationAccumulator accumulator = new RotationAccumulator();
    public Rotation reference = new Rotation(0.0f, 0.0f);
    public boolean referenceAvailable;
}

package yt.ren.slate.util.structs;

import yt.ren.slate.util.math.Vec3;

public class Bounds {
    public final Vec3 minimum;
    public final Vec3 maximum;

    public Bounds(Vec3 minimum, Vec3 maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }
}

package yt.ren.slate.util.structs;

public class Rotation {
    public final float yaw;
    public final float pitch;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation add(Rotation other) {
        return new Rotation(other.yaw + yaw, other.pitch + pitch);
    }

    public Rotation subtract(Rotation other) {
        return new Rotation(yaw - other.yaw, pitch - other.pitch);
    }

    @Override
    public String toString() {
        return "Rotation(" + yaw + ", " + pitch + ")";
    }
}

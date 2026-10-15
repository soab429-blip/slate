package yt.ren.slate.util.math;

public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 scale(double factor) {
        return new Vec3(factor * x, factor * y, factor * z);
    }

    public Vec3 interpolate(Vec3 to, double fraction) {
        return new Vec3(
                fraction * (to.x - x) + x,
                fraction * (to.y - y) + y,
                fraction * (to.z - z) + z);
    }

    public Vec3 addStandingEyeHeight() {
        return new Vec3(x, y + 1.62, z);
    }

    public Vec3 subtractStandingEyeHeight() {
        return new Vec3(x, y + -1.62, z);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    @Override
    public String toString() {
        return "Vec3(" + x + ", " + y + ", " + z + ")";
    }
}

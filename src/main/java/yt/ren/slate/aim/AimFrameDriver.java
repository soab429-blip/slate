package yt.ren.slate.aim;

import yt.ren.slate.aim.rotation.SharedRotation;

public class AimFrameDriver {
    public static boolean claimRotationPriority(SharedRotation rotation, int priority) {
        if (Integer.compareUnsigned(rotation.priority, priority) > 0) {
            return false;
        }
        rotation.priority = priority;
        return true;
    }
}

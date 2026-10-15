package yt.ren.slate.aim;

import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.util.structs.Rotation;

public class AimState {
    public Rotation mouseDelta = new Rotation(0.0f, 0.0f);
    public final TimedNoise pitchAttenuation = new TimedNoise();
    public final TimedNoise pitchJitter = new TimedNoise();
    public final TimedNoise horizontalSpeed = new TimedNoise();
    public final TimedNoise verticalSpeed = new TimedNoise();
    public final TimedNoise speedDrift = new TimedNoise();
}

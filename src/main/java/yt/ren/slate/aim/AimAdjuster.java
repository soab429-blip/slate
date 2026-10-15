package yt.ren.slate.aim;

import yt.ren.slate.aim.rotation.RotationAccumulator;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.structs.Rotation;

public  class AimAdjuster {
    public static void adjustAim(AimSettings settings, AimState state, AimRandom random, double now,
                                 Rotation difference, float timestep, RotationAccumulator rotation) {
        float yaw = difference.yaw > 0.0f
                ? Floats.max(difference.yaw - settings.deadzone, 0.0f)
                : Floats.min(difference.yaw + settings.deadzone, 0.0f);
        float pitch = difference.pitch > 0.0f
                ? Floats.max(difference.pitch - settings.deadzone, 0.0f)
                : Floats.min(settings.deadzone + difference.pitch, 0.0f);

        if (settings.mode == 2) {
            if (!settings.verticalInModeTwo) {
                pitch = 0.0f;
            }
        } else {
            float attenuation = state.pitchAttenuation.sample(random, now, 40.0f, 120.0f, 5.0);
            float pitchFactor = Floats.max((float) ((-1.0 / (double) attenuation)
                    * (double) Floats.magnitude(yaw) + 1.0), 0.0f);
            pitch = pitch * pitchFactor;
            float horizontal = settings.horizontalSpeed;
            float vertical = settings.verticalSpeed;
            if (settings.mode == 3) {
                horizontal = Floats.max(5.0f, horizontal);
                if (5.0f > vertical) {
                    vertical = 5.0f;
                }
            }
            horizontal = horizontal * 0.5f;
            horizontal = horizontal * horizontal;
            horizontal = state.horizontalSpeed.sample(random, now, 0.9f, 1.1f, 1.0) * horizontal;
            vertical = vertical * 0.5f;
            vertical = vertical * vertical;
            vertical = state.verticalSpeed.sample(random, now, 0.9f, 1.1f, 1.0) * vertical;
            float drift = state.speedDrift.sample(random, now, 0.95f, 1.05f, 60.0);
            if (settings.mode == 1) {
                horizontal = horizontal * 5.0f * drift * timestep;
                vertical = vertical * 5.0f * drift * timestep;
                yaw = clampOriginal(Floats.negate(horizontal), horizontal, yaw);
                pitch = clampOriginal(Floats.negate(vertical), vertical, pitch);
            } else {
                float adjustedYaw = horizontal * yaw * drift * timestep;
                float adjustedPitch = vertical * pitch * drift * timestep;
                if (!(Floats.magnitude(adjustedYaw) > Floats.magnitude(yaw))) {
                    yaw = adjustedYaw;
                }
                if (!(Floats.magnitude(adjustedPitch) > Floats.magnitude(pitch))) {
                    pitch = adjustedPitch;
                }
            }
            float jitterRange = (float) ((double) Floats.magnitude(yaw) * 0.1);
            pitch = pitch + state.pitchJitter.sample(random, now,
                    Floats.negate(jitterRange), jitterRange, 1.0);
        }

        if (settings.limitToMouseDelta) {
            float yawLimit = Floats.magnitude(state.mouseDelta.yaw);
            float pitchLimit = Floats.magnitude(state.mouseDelta.pitch);
            if (pitchLimit < Floats.magnitude(pitch) && pitch != 0.0f) {
                pitch = pitch * (pitchLimit / Floats.magnitude(pitch));
            }

            float limitedYaw = (yawLimit / Floats.magnitude(yaw)) * yaw;
            if (yawLimit < Floats.magnitude(yaw) && yaw != 0.0f) {
                yaw = limitedYaw;
            }
            state.mouseDelta = new Rotation(0.0f, 0.0f);
        }

        rotation.delta = rotation.delta.add(new Rotation(yaw, pitch));
        rotation.flag = 0;
    }

    private static float clampOriginal(float lower, float upper, float value) {
        float upperLimited = Floats.min(upper, value);
        return value < lower ? lower : upperLimited;
    }
}

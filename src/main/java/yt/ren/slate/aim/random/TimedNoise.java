package yt.ren.slate.aim.random;

import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.util.math.Floats;

public class TimedNoise {
    private double startedAt;
    private float previous;
    private float next;

    private void reset(AimRandom random, boolean both) {
        do {
            if (both) {
                previous = random.nextUnit();
            }
            next = random.nextUnit();
        } while ((double) Floats.magnitude(previous - next) < 0.2);
    }

    public float sample(AimRandom random, double now, float minimum, float maximum, double interval) {
        if (startedAt + interval < now) {
            startedAt = now;
            if (previous != 0.0f || next != 0.0f) {
                previous = next;
                reset(random, false);
            } else {
                reset(random, true);
            }
        }
        double fraction = (now - startedAt) / interval;
        double blended = (double) (next - previous) * fraction + (double) previous;
        return (float) ((double) (maximum - minimum) * blended + (double) minimum);
    }

    public float sampleStepped(AimRandom random, float minimum, float maximum, double interval, double increment) {
        startedAt = increment + startedAt;
        if (startedAt > interval) {
            startedAt = 0.0;
            if (previous != 0.0f || next != 0.0f) {
                previous = next;
                reset(random, false);
            } else {
                reset(random, true);
            }
        }
        double fraction = startedAt / interval;
        double blended = (double) (next - previous) * fraction + (double) previous;
        return (float) ((double) (maximum - minimum) * blended + (double) minimum);
    }

    public boolean belowSample(AimRandom random, double now, float minimum, float maximum, double interval) {
        float comparison = random.nextUnit();
        return sample(random, now, minimum, maximum, interval) > comparison;
    }
}

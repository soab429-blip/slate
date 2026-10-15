package yt.ren.slate.util;

public class Clock {
    public double now() {
        return System.nanoTime() / 1.0e9;
    }
}

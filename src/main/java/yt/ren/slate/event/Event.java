package yt.ren.slate.event;

public abstract class Event {
    public final Object context;

    protected Event(Object context) {
        this.context = context;
    }
}

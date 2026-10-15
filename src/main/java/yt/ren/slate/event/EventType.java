package yt.ren.slate.event;

import yt.ren.slate.event.Event;

public final class EventType<E extends Event> {
    private final int id;
    private final String name;
    private final Class<E> eventClass;

    EventType(int id, String name, Class<E> eventClass) {
        this.id = id;
        this.name = name;
        this.eventClass = eventClass;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Class<E> eventClass() {
        return eventClass;
    }

    @Override
    public String toString() {
        return name;
    }
}

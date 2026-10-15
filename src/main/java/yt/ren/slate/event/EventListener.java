package yt.ren.slate.event;

import yt.ren.slate.event.Event;

@FunctionalInterface
public interface EventListener<E extends Event> {
    void handle(E event);
}

package yt.ren.slate.event;

import yt.ren.slate.event.Event;
import yt.ren.slate.module.Module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EventBus {
    public static final class Subscription<E extends Event> {
        private final Module owner;
        private final EventType<E> type;
        private final EventListener<? super E> listener;
        private final int priority;

        public Subscription(Module owner, EventType<E> type, EventListener<? super E> listener, int priority) {
            this.owner = owner;
            this.type = type;
            this.listener = listener;
            this.priority = priority;
        }

        public Module owner() {
            return owner;
        }

        public EventType<E> type() {
            return type;
        }

        public int priority() {
            return priority;
        }

        @SuppressWarnings("unchecked")
        void deliver(Event event) {
            listener.handle((E) event);
        }
    }

    private final List<List<Subscription<?>>> members = new ArrayList<>();
    private final Subscription<?>[][] active = new Subscription<?>[EventTypes.COUNT][];
    private final int[] activeCount = new int[EventTypes.COUNT];

    public EventBus(Iterable<Module> modules) {
        for (int i = 0; i < EventTypes.COUNT; i++) {
            members.add(new ArrayList<Subscription<?>>());
        }
        for (Module module : modules) {
            for (Subscription<?> subscription : module.subscriptions()) {
                members.get(subscription.type.id()).add(subscription);
            }
        }
        for (int i = 0; i < EventTypes.COUNT; i++) {
            List<Subscription<?>> list = members.get(i);
            list.sort(Comparator.comparingInt((Subscription<?> s) -> s.priority).reversed());
            active[i] = new Subscription<?>[list.size()];
            rebuild(i);
        }
    }

    private void rebuild(int type) {
        int count = 0;
        for (Subscription<?> subscription : members.get(type)) {
            if (subscription.owner.isEnabled()) {
                active[type][count++] = subscription;
            }
        }
        activeCount[type] = count;
    }

    public void refresh(Module module) {
        for (Subscription<?> subscription : module.subscriptions()) {
            rebuild(subscription.type.id());
        }
    }

    public <E extends Event> void post(EventType<E> type, E event) {
        Subscription<?>[] list = active[type.id()];
        int count = activeCount[type.id()];
        for (int i = 0; i < count; i++) {
            list[i].deliver(event);
        }
    }

    public <E extends Event> void postToAll(EventType<E> type, E event) {
        for (Subscription<?> subscription : members.get(type.id())) {
            subscription.deliver(event);
        }
    }

    public boolean hasListeners(EventType<?> type) {
        return activeCount[type.id()] != 0;
    }
}

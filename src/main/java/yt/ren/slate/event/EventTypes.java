package yt.ren.slate.event;

import yt.ren.slate.event.Event;
import yt.ren.slate.event.Events.BindingEvent;
import yt.ren.slate.event.Events.BlockChangeEvent;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.ControllerEvent;
import yt.ren.slate.event.Events.ErrorEvent;
import yt.ren.slate.event.Events.FrameEvent;
import yt.ren.slate.event.Events.FriendChangeEvent;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.event.Events.MouseOverEvent;
import yt.ren.slate.event.Events.MoveEvent;
import yt.ren.slate.event.Events.OutgoingPacketEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.event.Events.ProfileEvent;
import yt.ren.slate.event.Events.RotationRequestEvent;
import yt.ren.slate.event.Events.ScreenRenderEvent;
import yt.ren.slate.event.Events.UIPrepareEvent;
import yt.ren.slate.event.Events.UIRenderEvent;
import yt.ren.slate.event.Events.UnloadEvent;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.ModuleRegistry.VetoEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventTypes {
    private static final List<EventType<?>> ALL = new ArrayList<>();

    public static final EventType<GameEvent> TICK = register("tick", GameEvent.class);
    public static final EventType<FrameEvent> FRAME = register("frame", FrameEvent.class);
    public static final EventType<PacketEvent> PACKET_RECEIVE = register("packet_receive", PacketEvent.class);
    public static final EventType<PacketEvent> PACKET_SEND = register("packet_send", PacketEvent.class);
    public static final EventType<VetoEvent> MODULE_VETO = register("module_veto", VetoEvent.class);
    public static final EventType<ToggleEvent> MODULE_TOGGLE = register("module_toggle", ToggleEvent.class);
    public static final EventType<WorldRenderEvent> WORLD_RENDER = register("world_render", WorldRenderEvent.class);
    public static final EventType<ScreenRenderEvent> SCREEN_RENDER = register("screen_render", ScreenRenderEvent.class);
    public static final EventType<MouseOverEvent> MOUSE_OVER = register("mouse_over", MouseOverEvent.class);
    public static final EventType<UIPrepareEvent> UI_PREPARE = register("ui_prepare", UIPrepareEvent.class);
    public static final EventType<UIRenderEvent> UI_RENDER = register("ui_render", UIRenderEvent.class);
    public static final EventType<ProfileEvent> PROFILE = register("profile", ProfileEvent.class);
    public static final EventType<ErrorEvent> ERROR = register("error", ErrorEvent.class);
    public static final EventType<RotationRequestEvent> ROTATION_REQUEST = register("rotation_request", RotationRequestEvent.class);
    public static final EventType<ClickEvent> CLICK_PRE = register("click_pre", ClickEvent.class);
    public static final EventType<ClickEvent> CLICK_POST = register("click_post", ClickEvent.class);
    public static final EventType<GameEvent> UPDATE_PRE = register("update_pre", GameEvent.class);
    public static final EventType<GameEvent> UPDATE_ROTATION = register("update_rotation", GameEvent.class);
    public static final EventType<GameEvent> UPDATE_POST = register("update_post", GameEvent.class);
    public static final EventType<FriendChangeEvent> FRIEND_CHANGE = register("friend_change", FriendChangeEvent.class);
    public static final EventType<BlockChangeEvent> BLOCK_CHANGE = register("block_change", BlockChangeEvent.class);
    public static final EventType<MoveEvent> MOVE_PRE = register("move_pre", MoveEvent.class);
    public static final EventType<MoveEvent> MOVE_POST = register("move_post", MoveEvent.class);
    public static final EventType<OutgoingPacketEvent> OUTGOING_PACKET = register("outgoing_packet", OutgoingPacketEvent.class);
    public static final EventType<BindingEvent> BINDING = register("binding", BindingEvent.class);
    public static final EventType<ControllerEvent> CONTROLLER = register("controller", ControllerEvent.class);
    public static final EventType<UnloadEvent> UNLOAD = register("unload", UnloadEvent.class);

    public static final int COUNT = ALL.size();

    private static <E extends Event> EventType<E> register(String name, Class<E> eventClass) {
        EventType<E> type = new EventType<>(ALL.size(), name, eventClass);
        ALL.add(type);
        return type;
    }

    public static List<EventType<?>> all() {
        return Collections.unmodifiableList(ALL);
    }
}

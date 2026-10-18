package yt.ren.slate.event;

import net.minecraft.world.World;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.event.Event;
import yt.ren.slate.module.Module;
import yt.ren.slate.render.DrawList;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Rotation;

import java.nio.FloatBuffer;

public class Events {
    public static class GameEvent extends Event {
        public GameEvent(ClientGameContext context) {
            super(context);
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class FrameEvent extends Event {
        public final float partialTick;
        public final double elapsed;

        public FrameEvent(ClientGameContext context, float partialTick, double elapsed) {
            super(context);
            this.partialTick = partialTick;
            this.elapsed = elapsed;
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class PacketEvent extends Event {
        public final boolean outbound;
        public final Object packet;
        public long elapsed;
        public final int id;
        public int action;

        public PacketEvent(boolean outbound, Object packet, long elapsed, int id) {
            super(null);
            this.outbound = outbound;
            this.packet = packet;
            this.elapsed = elapsed;
            this.id = id;
        }
    }

    public static class WorldRenderEvent extends Event {
        public final Vec3 camera;
        public final float partialTick;
        public final int managed;
        public final DrawList draw;

        public WorldRenderEvent(ClientGameContext context, Vec3 camera, float partialTick, int managed, DrawList draw) {
            super(context);
            this.camera = camera;
            this.partialTick = partialTick;
            this.managed = managed;
            this.draw = draw;
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class ScreenRenderEvent extends Event {
        public final Vec3 camera;
        public final float width;
        public final float height;
        public final FloatBuffer modelview;
        public final FloatBuffer projection;
        public final float partialTick;
        public final int managed;
        public final DrawList draw;

        public ScreenRenderEvent(ClientGameContext context, Vec3 camera, float width, float height,
                                 FloatBuffer modelview, FloatBuffer projection, float partialTick, int managed,
                                 DrawList draw) {
            super(context);
            this.camera = camera;
            this.width = width;
            this.height = height;
            this.modelview = modelview;
            this.projection = projection;
            this.partialTick = partialTick;
            this.managed = managed;
            this.draw = draw;
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class MouseOverEvent extends Event {
        public int request;
        public double reach;
        public double expand;

        public MouseOverEvent(ClientGameContext context, double reach, double expand) {
            super(context);
            this.reach = reach;
            this.expand = expand;
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class UIPrepareEvent extends Event {
        public final float elapsed;
        public int result;

        public UIPrepareEvent(ClientGameContext context, float elapsed) {
            super(context);
            this.elapsed = elapsed;
        }
    }

    public static class UIRenderEvent extends Event {
        public final Object surface;
        public final float elapsed;

        public UIRenderEvent(ClientGameContext context, Object surface, float elapsed) {
            super(context);
            this.surface = surface;
            this.elapsed = elapsed;
        }
    }

    public static class ProfileEvent extends Event {
        public final byte[] name;

        public final int action;

        public ProfileEvent(byte[] name, int action) {
            super(name);
            this.name = name;
            this.action = action;
        }
    }

    public static class ErrorEvent extends Event {
        public final int status;
        public final String message;

        public ErrorEvent(int status, String message) {
            super(status);
            this.status = status;
            this.message = message;
        }
    }

    public static class RotationRequestEvent extends Event {
        public int enabled;
        public int remap;
        public int restoreReference;
        public Rotation angles;

        public RotationRequestEvent(Rotation angles) {
            super(null);
            this.angles = angles;
        }
    }

    public static class ClickEvent extends Event {
        public final int left;
        public int cancelled;

        public ClickEvent(ClientGameContext context, int left) {
            super(context);
            this.left = left;
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class FriendChangeEvent extends Event {
        private final boolean added;

        public FriendChangeEvent(boolean added) {
            super(null);
            this.added = added;
        }

        public boolean added() {
            return added;
        }
    }

    public static class BlockChangeEvent extends Event {
        public final World world;
        public final int kind;
        public final int x;
        public final int y;
        public final int z;
        public final int id;

        public BlockChangeEvent(World world, int kind, int x, int y, int z, int id) {
            super(null);
            this.world = world;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
        }
    }

    public static class MoveEvent extends Event {
        public float strafe;
        public float forward;
        public float friction;
        public int jumpPressed;

        public MoveEvent(ClientGameContext context, float strafe, float forward, float friction, int jumpPressed) {
            super(context);
            this.strafe = strafe;
            this.forward = forward;
            this.friction = friction;
            this.jumpPressed = jumpPressed;
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class OutgoingPacketEvent extends Event {
        public final Object owner;
        public final Object packet;
        public int cancel;

        public OutgoingPacketEvent(Object owner, Object packet) {
            super(null);
            this.owner = owner;
            this.packet = packet;
        }
    }

    public static class BindingEvent extends Event {
        public final Module module;

        public BindingEvent(Object registry, Module module) {
            super(registry);
            this.module = module;
        }
    }

    public static class ControllerEvent extends Event {
        public int drain;

        public ControllerEvent(ClientGameContext context) {
            super(context);
        }

        public ClientGameContext game() {
            return (ClientGameContext) context;
        }
    }

    public static class UnloadEvent extends Event {
        public UnloadEvent(Object context) {
            super(context);
        }
    }
}

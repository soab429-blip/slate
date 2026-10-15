package yt.ren.slate.feature.combat;

import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.attack.AttackTargetCache;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.render.Primitives;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

public class LagRange extends Module {
    private final ScalarSetting delay = register("delay", new ScalarSetting("Maximum delay", "%.0f ms", 0.0f, 0.0f, 10.0f, 1000.0f, 100.0f, 0.0f));
    private final BooleanSetting flushSprintResetSetting = register("flush_sprint_reset", new BooleanSetting("Sprint reset", 0, 0));
    private final BooleanSetting flushSplashPotion = register("flush_splash_potion", new BooleanSetting("Used splash potion", 0, 0));
    private final BooleanSetting renderRemotePos = register("render_remote_pos", new BooleanSetting("Enabled", 0, 0));
    private final ColorSetting indicatorColor = register("indicator_color", new ColorSetting("Color", 0.99f, 0.99f, 0.99f, 0.0f, 0.0f, 0.0f));
    private final ScalarSetting indicatorLineWidth = register("indicator_line_width", new ScalarSetting("Line width", "%0.0f px", 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f));
    private final BooleanSetting indicatorFilled = register("indicator_filled", new BooleanSetting("Filled", 1, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;

    private int lastPacketId;
    private final AtomicInteger lock = new AtomicInteger();
    private long maximumDelay;
    private int movingCloser;
    private int flushAttack;
    private int flushSprintReset;
    private int flushing;
    private Vec3 targetPosition = Vec3.ZERO;
    private int active;
    private Vec3 releasedPosition = Vec3.ZERO;
    private volatile int renderEnabled;
    private Vec3 previousRender = Vec3.ZERO;
    private Vec3 currentRender = Vec3.ZERO;
    private int attackedThisTick;
    private int wasSprinting;
    private int splashCooldown;
    private final TimedNoise targetRange = new TimedNoise();

    public LagRange(ClientRuntime runtime) {
        super(ModuleId.LAG_RANGE, "Lag Range", "Uses lag to stay out of target range.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }



    private void acquire() {
        while (lock.getAndSet(1) != 0) {
        }
    }

    private void release() {
        lock.set(0);
    }

    private void deactivate() {
        acquire();
        flushing = 0;
        flushAttack = 0;
        flushSprintReset = 0;
        active = 0;
        release();
        renderEnabled = 0;
    }

    private static Vec3 position(PlayerMoveC2SPacket movement) {
        return new Vec3(movement.x, movement.minY, movement.z);
    }

    void filterPacket(PacketEvent event) {
        acquire();
        try {
            if (event.id > lastPacketId) {
                if (active != 0) {
                    if (event.packet instanceof PlayerMoveC2SPacket && ((PlayerMoveC2SPacket) event.packet).hasPos) {
                        Vec3 point = position((PlayerMoveC2SPacket) event.packet);
                        double before = Geometry.distanceSquared(targetPosition, releasedPosition);
                        double after = Geometry.distanceSquared(targetPosition, point);
                        movingCloser = before > after ? 1 : 0;
                    }
                }
                lastPacketId = event.id;
            }
            if (active != 0 && movingCloser != 0 && flushAttack == 0 && flushing == 0 && flushSprintReset == 0
                    && event.elapsed < maximumDelay) {
                event.action = 1;
                return;
            }
            if (event.action != 0) {
                return;
            }
            Object packet = event.packet;
            if (packet instanceof PlayerMoveC2SPacket) {
                flushing = 0;
                PlayerMoveC2SPacket movement = (PlayerMoveC2SPacket) packet;
                if (movement.hasPos) {
                    releasedPosition = position(movement);
                }
                return;
            }
            if (packet instanceof PlayerInteractEntityC2SPacket) {
                if (flushAttack != 0) {
                    flushAttack = 0;
                    flushing = 1;
                }
                return;
            }
            if (packet instanceof PlayerMovementActionC2SPacket && flushSprintReset != 0
                    && ((PlayerMovementActionC2SPacket) packet).action.ordinal() == 4) {
                flushSprintReset = 0;
                flushing = 1;
            }
        } finally {
            release();
        }
    }

    void afterClick(ClickEvent event) {
        if (event.left != 1) {
            return;
        }
        ClientGameContext c = (ClientGameContext) event.context;
        attackedThisTick = c.player.ticks == c.player.lastAttackTime ? 1 : 0;
    }

    private static boolean holdingSplashPotion(ClientGameContext c) {
        PlayerInventory inventory = c.player.inventory;
        int slot = inventory.selectedSlot;
        ItemStack stack = inventory.getItem(slot);
        if (stack == null) {
            return false;
        }
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return false;
        }
        ItemStack held = inventory.items[slot];
        Item item = held != null ? held.getItem() : null;
        if (item == null) {
            return false;
        }
        return Item.getId(item) == 0x175 && (stack.metadata & 0x4000) != 0;
    }

    void update(ClientGameContext c) {
        int attacked = attackedThisTick;
        attackedThisTick = 0;
        if (c.focused != 1 || (requireWeapon.get()
                && !HeldItem.holdingWeapon(runtime, c.player))) {
            deactivate();
            return;
        }
        if (flushSplashPotion.get()) {
            if (holdingSplashPotion(c)) {
                splashCooldown = 15;
                deactivate();
                return;
            }
            if (splashCooldown > 0) {
                --splashCooldown;
                deactivate();
                return;
            }
        }
        boolean sprinting = c.player.isSprinting();
        int reset = 0;
        if ((wasSprinting & 1) != 0) {
            reset = (flushSprintResetSetting.get() ? 1 : 0);
        }
        if (sprinting) {
            reset = 0;
        }
        wasSprinting = sprinting ? 1 : 0;
        AttackTargetCache cache = c.targetCache;
        if (cache.target == null) {
            deactivate();
            return;
        }
        double distance = cache.squaredDistance;
        double range = targetRange.sample(random, runtime.frameTime[0], 10.0f, 15.0f, 10.0);
        if (distance > range * range) {
            deactivate();
            return;
        }
        int hurt = cache.target.damagedTimer;
        boolean display = false;
        if (renderRemotePos.get()) {
            display = c.client.options.perspective != 0;
        }
        int attack = attacked & (hurt < 5 ? 1 : 0);
        RemoteClientPlayerEntity remote = (RemoteClientPlayerEntity) cache.target;
        Vec3 target = new Vec3(remote.lerpX, remote.lerpY, remote.lerpZ);
        acquire();
        try {
            maximumDelay = Sse.cvttss2si64(delay.get());
            flushAttack = attack;
            flushSprintReset = reset;
            active = 1;
            targetPosition = target;
            previousRender = renderEnabled == 1 ? currentRender : releasedPosition;
            currentRender = releasedPosition;
            renderEnabled = display ? movingCloser : 0;
        } finally {
            release();
        }
    }

    void render(WorldRenderEvent event) {
        if (renderEnabled != 1) {
            return;
        }
        double fraction = event.partialTick;
        Vec3 point = previousRender.interpolate(currentRender, fraction);
        Vec3 relative = point.subtract(event.camera);
        Primitives.beginCubeVertices();
        GL11.glLineWidth(indicatorLineWidth.get());
        GL11.glPushMatrix();
        GL11.glTranslatef((float) relative.x, (float) (relative.y + 1.0), (float) relative.z);
        GL11.glScalef(0.3f, 1.0f, 0.3f);
        if (indicatorFilled.get()) {
            float[] color = indicatorColor.liveChannels();
            GL11.glColor4f(color[0], color[1], color[2], 0.4f);
            Primitives.drawFilledCube();
        }
        float[] color = indicatorColor.liveChannels();
        GL11.glColor3f(color[0], color[1], color[2]);
        Primitives.drawCubeOutline();
        GL11.glPopMatrix();
        Primitives.endPrimitiveVertices();
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.0fms", (double) delay.get());
    }

    @Override
    protected void setup() {
        on(EventTypes.PACKET_SEND, e -> filterPacket(e), -100);
        on(EventTypes.WORLD_RENDER, e -> render(e));
        on(EventTypes.CLICK_POST, e -> afterClick(e));
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(delay);
        w.heading("Flush if");
        w.show(flushSprintResetSetting);
        w.show(flushSplashPotion);
        w.heading("Real position indicator");
        w.show(renderRemotePos);
        if (renderRemotePos.get()) {
            w.show(indicatorColor);
            w.show(indicatorLineWidth);
            w.show(indicatorFilled);
        }
        w.heading("Conditions");
        w.show(requireWeapon);
    }
}

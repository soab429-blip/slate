package yt.ren.slate.hook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandSuggestionsC2SPacket;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.core.ClientFrame;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.*;
import yt.ren.slate.event.Events;
import yt.ren.slate.feature.utility.ItemUse;
import yt.ren.slate.feature.visual.Chams;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.input.InputPump;
import yt.ren.slate.input.WindowInputHost;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.net.ChannelBridge;
import yt.ren.slate.render.GLSM;
import yt.ren.slate.render.DrawList;
import yt.ren.slate.ui.UIController;
import yt.ren.slate.util.PlayerTags;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Rotation;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

public class ClientHooks {
    private static final float[] GUI_SCALES = {0.75f, 1.0f, 1.5f, 2.0f};

    private static ClientRuntime rt() {
        return ClientRuntime.get();
    }

    public interface Initializer {
        void initialize(ClientRuntime runtime);
    }

    public static Initializer initializer;

    public interface ChannelInstaller {
        void install(Minecraft game);
    }

    public static ChannelInstaller channelInstaller;

    public interface Commands {
        int handleChat(ClientGameContext context, ChatMessageC2SPacket packet);

        int handleCompletion(ClientGameContext context, CommandSuggestionsC2SPacket packet);
    }

    public static Commands commands;

    public static int safeWalkClip;

    public static volatile boolean unloaded;

    public static void getMouseOver(Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        original.run();
        ClientRuntime runtime = rt();
        if (runtime.initialized.compareAndSet(false, true)) {
            if (initializer != null) {
                initializer.initialize(runtime);
            }
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (channelInstaller != null) {
            try {
                channelInstaller.install(game);
            } catch (RuntimeException exception) {
                exception.printStackTrace();
            }
        }
        runtime.rotation.beginRotationFrame(game);
        ClientGameContext context = runtime.context();
        AttackPrediction.refreshAttackTarget(context, runtime.targetCache, runtime.aimTargetReference);
        runtime.registry.post(EventTypes.TICK, new GameEvent(context));
        if (!game.focused) {
            return;
        }
        MouseOverEvent event = new MouseOverEvent(context, 3.0, 0.1);
        runtime.registry.post(EventTypes.MOUSE_OVER, event);
        if ((event.request & 0xFF) == 1) {
            runtime.mouseOver.updateMouseOverTarget(event);
        }
        ItemUse.finishHotbarFrame(runtime.itemUse);
    }

    public static boolean isDisplayActivePreRender(BooleanSupplier original) {
        if (unloaded) {
            return original.getAsBoolean();
        }
        ClientRuntime runtime = rt();
        runtime.rotation.beginInputRotation();
        if (runtime.module(ModuleId.NO_HURT_CAM).isEnabled()) {
            LocalClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                player.damagedTime = 0x7fffffff;
            }
        }
        return original.getAsBoolean();
    }

    public static void updateDisplay(Runnable displayUpdate) {
        if (unloaded) {
            displayUpdate.run();
            InputPump.pump();
            return;
        }
        ClientRuntime runtime = rt();
        if (runtime.initialized.get()) {
            WindowInputHost.installOnce(runtime.windowInput);
        }
        Minecraft game = Minecraft.getInstance();
        ClientGameContext context = new ClientGameContext(runtime.registry, runtime.profiles, game, game.world,
                game.player, runtime.keys, runtime.targetCache);
        int focused = runtime.guiEnabled() == 0 && game.focused ? 1 : 0;
        context.focused = focused;
        if (focused == 0 && runtime.previousFocused != 0) {
            runtime.moduleKeys.releaseAll();
            if (!Display.isActive()) {
                Arrays.fill(runtime.keys, (byte) 0);
            }
        }
        runtime.previousFocused = focused;
        Module gui = runtime.module(ModuleId.GUI);
        if (gui.keybind() == 0) {
            runtime.registry.context.change(gui, 99);
        }
        double now = runtime.clock.now();
        int width = Display.getWidth();
        int height = Display.getHeight();
        if (runtime.lastGuiTime < 0.0) {
            runtime.lastGuiTime = now;
        }
        if (runtime.surface != null && runtime.registry.hasListeners(EventTypes.UI_RENDER)) {
            runtime.overlayWidth = width;
            runtime.overlayHeight = height;
            boolean changed = runtime.surface.resize(width, height);
            int countdown = 10;
            if (!changed) {
                countdown = runtime.redrawCountdown;
                if (0 < countdown) {
                    countdown = countdown - 1;
                }
            }
            runtime.redrawCountdown = countdown;
            runtime.surface.setScale(GUI_SCALES[runtime.gui.scaleIndex()]);
            double last = runtime.lastGuiTime;
            if (0.008333333333333333 < now - runtime.lastGuiTime) {
                float elapsed = (float) (now - runtime.lastGuiTime);
                UIPrepareEvent prepare = new UIPrepareEvent(context, elapsed);
                runtime.registry.post(EventTypes.UI_PREPARE, prepare);
                runtime.renderFlag = prepare.result & 1;
                last = now;
                if ((prepare.result & 1) != 0
                        && ((prepare.result & 2) != 0 || runtime.redrawRequested != 0 || 0 < runtime.redrawCountdown)) {
                    runtime.surface.beginFrame(now);
                    runtime.registry.post(EventTypes.UI_RENDER, new UIRenderEvent(context, runtime.surface, elapsed));
                    runtime.surface.finishFrame();
                    runtime.redrawRequested = (prepare.result & 2) >> 1;
                }
            }
            runtime.lastGuiTime = last;
            if (runtime.renderFlag == 0) {
                runtime.redrawRequested = 1;
            } else {
                runtime.surface.beginPresent(now);
                runtime.surface.present();
                runtime.surface.finishPresent();
            }
        }
        if (runtime.registry.changesPending()) {
            runtime.acquire();
            runtime.registry.applyModuleChanges(context, runtime.owner);
            runtime.release();
        }
        ClientFrame.update(context, runtime.registry, game.timer, now, runtime.frameDriverState,
                runtime.frameTime, runtime.clock, runtime.aimAdjustment, runtime.rotation);
        displayUpdate.run();
        InputPump.pump();
        if (runtime.gui != null && UIController.unloadRequested(runtime.gui.state) != 0) {
            unload(runtime, context);
        }
    }

    private static void unload(ClientRuntime runtime, ClientGameContext context) {
        runtime.configuration.saveGlobalFile();
        Events.UnloadEvent event = new Events.UnloadEvent(context);
        runtime.registry.bus.postToAll(EventTypes.UNLOAD, event);
        for (Module module : runtime.registry.all) {
            runtime.registry.queueModuleChange(module, 0);
        }
        runtime.acquire();
        runtime.bypass = 1;
        ChannelBridge.remove(Minecraft.getInstance());
        runtime.release();
        runtime.registry.applyModuleChanges(context, runtime.owner);
        AttackPrediction.clearAttackTarget(runtime.targetCache);
        WindowInputHost.remove();
        unloaded = true;
    }

    public static void postWorldClear(int mask) {
        ClientRuntime runtime = rt();
        if (runtime.surface == null) {
            return;
        }
        if (mask == 0x100) {
            Minecraft game = Minecraft.getInstance();
            LocalClientPlayerEntity player = game.player;
            boolean restore = runtime.rotation.displayRotationAvailable();
            if (restore) {
                runtime.rotation.swapDisplayRotation(player, 1);
            }
            boolean worldEmpty = !runtime.registry.hasListeners(EventTypes.WORLD_RENDER);
            boolean screenEmpty = !runtime.registry.hasListeners(EventTypes.SCREEN_RENDER);
            if (!(worldEmpty && screenEmpty)) {
                ClientWorld world = game.world;
                Entity view = game.camera;
                if (view != null && world != null && player != null) {
                    ClientGameContext context = new ClientGameContext(runtime.registry, runtime.profiles, game, world,
                            player, runtime.keys, runtime.targetCache);
                    if (runtime.guiEnabled() == 0) {
                        context.focused = game.focused ? 1 : 0;
                    }
                    float partial = game.timer.partialTick;
                    Vec3 current = new Vec3(view.x, view.y, view.z);
                    Vec3 previous = new Vec3(view.lastX, view.lastY, view.lastZ);
                    double t = partial;
                    Vec3 camera = new Vec3(t * (current.x - previous.x) + previous.x,
                            t * (current.y - previous.y) + previous.y,
                            (current.z - previous.z) * t + previous.z);
                    GLSM.disableDepth();
                    GLSM.disableTexture();
                    GLSM.disableLighting();
                    GLSM.enableBlend();
                    GLSM.blendFunc(0x302, 0x303);
                    GL11.glDisable(0xb71);
                    GL11.glDisable(0xde1);
                    GL11.glDisable(0xb50);
                    GL11.glEnable(0xbe2);
                    GL11.glBlendFunc(0x302, 0x303);
                    GL11.glEnable(0xb20);
                    DrawList draw = runtime.drawList;
                    if (!worldEmpty) {
                        draw.begin();
                        runtime.registry.post(EventTypes.WORLD_RENDER, new WorldRenderEvent(context, camera, partial, 1, draw));
                        draw.flush();
                    }
                    if (!screenEmpty) {
                        runtime.savedModelview.clear();
                        runtime.savedProjection.clear();
                        draw.readMatrices(runtime.savedModelview, runtime.savedProjection);
                        runtime.savedModelview.clear();
                        runtime.savedProjection.clear();
                        GL11.glMatrixMode(0x1701);
                        GL11.glLoadIdentity();
                        GL11.glOrtho(0.0, runtime.overlayWidth, runtime.overlayHeight, 0.0, -3000.0, 3000.0);
                        GL11.glMatrixMode(0x1700);
                        GL11.glLoadIdentity();
                        draw.begin();
                        runtime.registry.post(EventTypes.SCREEN_RENDER, new ScreenRenderEvent(context, camera, (float) runtime.overlayWidth,
                                (float) runtime.overlayHeight, runtime.savedModelview, runtime.savedProjection, partial, 1, draw));
                        draw.flush();
                    }
                    GLSM.enableTexture();
                    GLSM.enableDepth();
                    GLSM.enableCull();
                    GLSM.enableAlpha();
                    GLSM.color(1.0f, 1.0f, 1.0f, 1.0f);
                    GL11.glEnable(0xde1);
                    GL11.glEnable(0xb71);
                    GL11.glEnable(0xb44);
                    GL11.glEnable(0xbc0);
                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
            if (restore) {
                runtime.rotation.swapDisplayRotation(player, 0);
            }
        }
        GL11.glClear(mask);
    }

    public static void clickMouse(Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        ClientRuntime runtime = rt();
        ClientGameContext context = runtime.context();
        AttackPrediction.predictAttackSlowdown(context, runtime.targetCache);
        ClickEvent event = new ClickEvent(context, 1);
        runtime.registry.post(EventTypes.CLICK_PRE, event);
        if ((event.cancelled & 0xFF) != 0) {
            return;
        }
        original.run();
        runtime.registry.post(EventTypes.CLICK_POST, event);
    }

    public static void rightClickMouse(Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        ClientRuntime runtime = rt();
        ClientGameContext context = runtime.context();
        ClickEvent event = new ClickEvent(context, 0);
        runtime.registry.post(EventTypes.CLICK_PRE, event);
        ItemUse.beforeItemUse(context, runtime.itemUse);
        if ((event.cancelled & 0xFF) != 0) {
            return;
        }
        original.run();
        runtime.registry.post(EventTypes.CLICK_POST, event);
        ItemUse.afterItemUse(context, runtime.itemUse);
    }

    public static void orientCamera(Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        RotationLifecycle rotation = rt().rotation;
        if (!rotation.displayRotationAvailable()) {
            original.run();
            return;
        }
        Minecraft game = Minecraft.getInstance();
        LocalClientPlayerEntity player = game.player;
        game.worldRenderer.viewChanged = true;
        rotation.finishInputRotation(player);
        rotation.swapDisplayRotation(player, 1);
        original.run();
        rotation.swapDisplayRotation(player, 0);
    }

    public static void updateActiveRenderInfo(Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        RotationLifecycle rotation = rt().rotation;
        if (!rotation.displayRotationAvailable()) {
            original.run();
            return;
        }
        LocalClientPlayerEntity local = Minecraft.getInstance().player;
        Rotation current = RotationLifecycle.read(local);
        Rotation previous = RotationLifecycle.read(local, true);
        Rotation display = rotation.displayRotation();
        RotationLifecycle.write(local, display, RotationLifecycle.CURRENT);
        RotationLifecycle.write(local, display, RotationLifecycle.PREVIOUS);
        original.run();
        RotationLifecycle.write(local, current, RotationLifecycle.CURRENT);
        RotationLifecycle.write(local, previous, RotationLifecycle.PREVIOUS);
    }

    public static float setRenderX(float value) {
        if (unloaded) {
            return value;
        }
        RotationLifecycle rotation = rt().rotation;
        return rotation.displayRotationAvailable() ? rotation.displayRotation().pitch : value;
    }

    public static float setRenderY(float value) {
        if (unloaded) {
            return value;
        }
        RotationLifecycle rotation = rt().rotation;
        return rotation.displayRotationAvailable() ? rotation.displayRotation().yaw : value;
    }

    public static void updateEntities(ClientWorld world, Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        ClientRuntime runtime = rt();
        Minecraft game = Minecraft.getInstance();
        LocalClientPlayerEntity player = game.player;
        ClientGameContext context = new ClientGameContext(runtime.registry, runtime.profiles, game, world, player,
                runtime.keys, runtime.targetCache);
        if (runtime.guiEnabled() == 0) {
            context.focused = game.focused ? 1 : 0;
        }
        GameEvent event = new GameEvent(context);
        runtime.registry.post(EventTypes.UPDATE_PRE, event);
        runtime.rotation.prepareRotationUpdate();
        runtime.registry.post(EventTypes.UPDATE_ROTATION, event);
        original.run();
        runtime.registry.post(EventTypes.UPDATE_POST, event);
        RotationRequestEvent request = new RotationRequestEvent(runtime.rotation.displayRotationAvailable()
                ? runtime.rotation.displayRotation() : RotationLifecycle.read(player));
        if (runtime.registry.hasListeners(EventTypes.ROTATION_REQUEST)) {
            runtime.registry.post(EventTypes.ROTATION_REQUEST, request);
        }
        runtime.rotation.finishRotationUpdate(context, request.enabled & 0xFF, request.remap & 0xFF,
                request.restoreReference & 0xFF, request.angles);
        RotationLifecycle.applyRotation(player, runtime.sharedRotation.accumulator, runtime.rotation.step, 1);
    }

    public static boolean canRenderName(LivingEntity entity, BooleanSupplier original) {
        if (unloaded) {
            return original.getAsBoolean();
        }
        if (rt().module(ModuleId.NAME_TAGS).isEnabled()) {
            if (entity instanceof PlayerEntity && !PlayerTags.has((PlayerEntity) entity, 2)) {
                return false;
            }
        }
        return original.getAsBoolean();
    }

    public static void renderPlayer(LivingEntity entity, Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        ClientRuntime runtime = rt();
        int hide = runtime.module(Chams.class).hidesFriendlies() ? 1 : 0;
        if (runtime.module(ModuleId.CHAMS).isEnabled()
                && !PlayerTags.has((PlayerEntity) entity, hide * 5 + 2)) {
            GL11.glEnable(0x8037);
            GL11.glPolygonOffset(1.0f, -3000000.0f);
            original.run();
            GL11.glDisable(0x8037);
            return;
        }
        original.run();
    }

    public static void unloadChunk(int x, int z, Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        ClientRuntime runtime = rt();
        World world = Minecraft.getInstance().world;
        original.run();
        runtime.registry.post(EventTypes.BLOCK_CHANGE, new BlockChangeEvent(world, 0, x, 0, z, 0));
    }

    public static boolean setBlock(World world, int x, int y, int z, int id, BooleanSupplier original) {
        if (unloaded) {
            return original.getAsBoolean();
        }
        rt().registry.post(EventTypes.BLOCK_CHANGE, new BlockChangeEvent(world, 2, x, y, z, id));
        return original.getAsBoolean();
    }

    public static void invalidateChunk(World world, int minX, int minZ, Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        original.run();
        rt().registry.post(EventTypes.BLOCK_CHANGE, new BlockChangeEvent(world, 1, minX >> 4, 0, minZ >> 4, 0));
    }

    public static boolean shouldClipLedge(Entity entity, BooleanSupplier original) {
        if (unloaded) {
            return original.getAsBoolean();
        }
        if (rt().module(ModuleId.SAFE_WALK).isEnabled()) {
            LocalClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null && player == entity && safeWalkClip != 0) {
                return true;
            }
        }
        return original.getAsBoolean();
    }

    @FunctionalInterface
    public interface MoveFlying {
        void call(float strafe, float forward, float friction);
    }

    public static void moveFlying(Entity entity, float strafe, float forward, float friction, MoveFlying original) {
        if (unloaded) {
            original.call(strafe, forward, friction);
            return;
        }
        ClientRuntime runtime = rt();
        if (!runtime.registry.hasListeners(EventTypes.MOVE_PRE) && !runtime.registry.hasListeners(EventTypes.MOVE_POST)) {
            original.call(strafe, forward, friction);
            return;
        }
        Minecraft game = Minecraft.getInstance();
        LocalClientPlayerEntity player = game.player;
        if (entity != player) {
            original.call(strafe, forward, friction);
            return;
        }
        ClientGameContext context = runtime.context();
        KeyBinding jump = game.options.keyBindings[6];
        int jumpTicks = player.jumpingCooldown;
        int pressed = jumpTicks > 0 && jump.pressed ? 1 : 0;
        MoveEvent event = new MoveEvent(context, strafe, forward, friction, pressed);
        runtime.registry.post(EventTypes.MOVE_PRE, event);
        original.call(event.strafe, event.forward, event.friction);
        runtime.registry.post(EventTypes.MOVE_POST, event);
    }

    public static void updateController(Runnable original) {
        if (unloaded) {
            original.run();
            return;
        }
        original.run();
        ClientRuntime runtime = rt();
        Minecraft game = Minecraft.getInstance();
        ControllerEvent event = new ControllerEvent(runtime.context());
        runtime.registry.post(EventTypes.CONTROLLER, event);
        if ((event.drain & 1) == 0) {
            return;
        }
        Screen current = game.screen;
        InventoryMenuScreen screen = current instanceof InventoryMenuScreen ? (InventoryMenuScreen) current : null;
        KeyBinding binding = game.options.keyBindings[10];
        int inventoryKey = binding.keyCode;
        while (Mouse.next()) {
        }
        while (Keyboard.next()) {
            if (screen == null || !Keyboard.getEventKeyState()) {
                continue;
            }
            int key = Keyboard.getEventKey();
            if (key == 1 || key == inventoryKey) {
                ((Screen) screen).keyPressed((char) 0, key);
            }
        }
    }

    public static boolean sendPacket(Object owner, Object packet) {
        if (unloaded) {
            return true;
        }
        ClientRuntime runtime = rt();
        ClientGameContext context = runtime.context();
        if (packet instanceof ChatMessageC2SPacket) {
            if (commands != null && commands.handleChat(context, (ChatMessageC2SPacket) packet) != 0) {
                return false;
            }
        } else if (packet instanceof CommandSuggestionsC2SPacket) {
            if (commands != null && commands.handleCompletion(context, (CommandSuggestionsC2SPacket) packet) != 0) {
                return false;
            }
        }
        OutgoingPacketEvent event = new OutgoingPacketEvent(owner, packet);
        runtime.registry.post(EventTypes.OUTGOING_PACKET, event);
        return (event.cancel & 0xFF) == 0;
    }
}

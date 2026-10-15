package yt.ren.slate.feature.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.menu.PlayerMenu;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.event.Events.OutgoingPacketEvent;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;

public class InvWalk extends Module {
    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 2, 0, new String[]{"Vanilla", "Spoof"}, 0, 0, 0));
    private final ChoiceSetting stopOnClick = register("stop_on_click", new ChoiceSetting("Stop on interact", 3, 0, new String[]{"Full", "Partial", "No"}, 0, 0, 0));
    private final ScalarSetting stopDuration = register("stop_duration", new ScalarSetting("Stop duration", "%.0f ms", 1.0f, 50.0f, 1.0f, 20.0f, 2.0f, 0.0f));
    private final ChoiceSetting stopNonInv = register("stop_non_inv", new ChoiceSetting("Always stop", 3, 0, new String[]{"Full", "Partial", "No"}, 0, 0, 0));

    private final Packet<?>[] invPackets = new Packet<?>[32];
    private int invCount;
    private int invBlocked;
    private int invTick;
    private int invPhase;
    private int invLastInteraction;
    private int invSentOpen;

    public InvWalk(ClientRuntime runtime) {
        super(ModuleId.INV_WALK, "Inv Walk", "Allows moving in inventory.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, this::handleInvWalkToggle);
        on(EventTypes.UPDATE_ROTATION, this::updateInvWalk);
        on(EventTypes.OUTGOING_PACKET, this::filterInvWalkPacket);
        on(EventTypes.CONTROLLER, e -> finishInvWalkController(e.game()));
    }

    @Override
    public String suffix() {
        return invWalkLabel();
    }

    private static void sendNetworkPacket(Connection owner, Packet<?> packet) {
        owner.send(packet);
    }

    private long invStopDuration() {
        float value = stopDuration.get();
        return (long) value & 0xFFFFFFFFL;
    }

    private boolean flushInvWalkPackets(Minecraft client) {
        if (invCount == 0) {
            return false;
        }
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        Connection owner = handler != null ? handler.connection : null;
        boolean connected = handler != null && owner != null;
        for (int i = 0; i < invCount; ++i) {
            Packet<?> packet = invPackets[i];
            if (connected) {
                sendNetworkPacket(owner, packet);
            }
        }
        invCount = 0;
        return connected;
    }

    void handleInvWalkToggle(ToggleEvent event) {
        if ((event.state & 0xff) == 0 && event.module == this) {
            ClientGameContext c = (ClientGameContext) event.context;
            flushInvWalkPackets(c.client);
        }
    }

    void updateInvWalk(GameEvent event) {
        ClientGameContext c = event.game();
        InventoryMenuScreen current = Motion.screen(c.client);
        GameOptions options = c.client.options;
        KeyBinding[] keys = {Motion.key(options, 4), Motion.key(options, 3), Motion.key(options, 5), Motion.key(options, 2), Motion.key(options, 6)};
        boolean[] physical = new boolean[5];
        for (int i = 0; i < keys.length; ++i) {
            physical[i] = PhysicalKeys.pressed(keys[i]);
        }
        int initialMode = mode.index();
        int initialStop = stopOnClick.index();
        boolean stopSprint = initialMode == 1 && initialStop != 2 && invCount != 0;
        boolean stopMovement = initialStop == 0 && stopSprint;
        ++invTick;
        if (current == null) {
            if (invPhase != 0) {
                writeKeys(keys, physical, false);
            }
            invPhase = 0;
            if (c.focused == 0) {
                return;
            }
            if ((invBlocked & 1) != 0 || stopMovement) {
                writeKeys(keys, physical, stopMovement);
            }
        } else {
            boolean anyInput = false;
            for (boolean down : physical) {
                anyInput = anyInput || down;
            }
            boolean isInventory = current instanceof SurvivalInventoryScreen;
            if (isInventory) {
                boolean blockInventory = false;
                boolean activePhase;
                if (mode.index() == 1) {
                    PlayerInventory inventory = c.player.inventory;
                    ClientPlayNetworkHandler handler = c.client.getNetworkHandler();
                    Connection owner = handler.connection;
                    boolean occupied = inventory.cursorItem != null;
                    if (!occupied) {
                        if (invPhase != 1) {
                            CraftingInventory crafting =
                                    ((PlayerMenu) c.player.playerMenu).craftingInventory;
                            for (int slot = 0; slot < 4; ++slot) {
                                if (crafting.getItem(slot) != null) {
                                    occupied = true;
                                    break;
                                }
                            }
                        }
                        if (!occupied) {
                            occupied = stopSprint;
                        }
                    }
                    boolean canMove = anyInput && !occupied;
                    int phase = invPhase;
                    if (phase == 2 && !canMove) {
                        Packet<?> packet = new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT);
                        phase = 3;
                        sendNetworkPacket(owner, packet);
                        invPhase = phase;
                    } else if (canMove && phase == 3 && invCount == 0
                            && (stopOnClick.index() == 2
                            || Integer.toUnsignedLong(invTick - invLastInteraction) >= invStopDuration())) {
                        Packet<?> packet = new CloseInventoryMenuC2SPacket(0);
                        phase = 2;
                        sendNetworkPacket(owner, packet);
                        invPhase = phase;
                    }
                    blockInventory = occupied || phase == 3;
                    activePhase = phase != 0;
                } else {
                    invPhase = 3;
                    activePhase = true;
                }
                int stop = stopOnClick.index();
                stopSprint = stopSprint || (stop != 2 && blockInventory);
                stopMovement = stop == 0 && stopSprint;
                if (activePhase || (invBlocked & 1) != 0 || stopMovement) {
                    writeKeys(keys, physical, stopMovement);
                }
            } else {
                invPhase = 1;
                if (mode.index() == 1) {
                    int stop = stopOnClick.index();
                    boolean recent = stop != 2
                            && Integer.toUnsignedLong(invTick - invLastInteraction) < invStopDuration();
                    boolean interaction = stopSprint || !anyInput || recent;
                    int nonInventory = stopNonInv.index();
                    stopSprint = nonInventory != 2 || interaction;
                    stopMovement = (stop == 0 && interaction) || (nonInventory == 0 && stopSprint);
                }
                writeKeys(keys, physical, stopMovement);
            }
        }
        if (stopSprint) {
            c.player.setSprinting(false);
            KeyBinding binding = Motion.key(options, 8);
            binding.pressed = false;
        }
        invBlocked = stopMovement ? 1 : 0;
        invSentOpen = 0;
    }

    private static void writeKeys(KeyBinding[] keys, boolean[] physical, boolean block) {
        for (int i = 0; i < keys.length; ++i) {
            keys[i].pressed = !block && physical[i];
        }
    }

    void filterInvWalkPacket(OutgoingPacketEvent event) {
        if (mode.index() != 1) {
            return;
        }
        Object packet = event.packet;
        if (packet instanceof ClientStatusC2SPacket) {
            if (((ClientStatusC2SPacket) packet).status.ordinal() == 2) {
                event.cancel = 1;
                invPhase = 2;
            }
            return;
        }
        if (packet instanceof InventoryMenuClickSlotC2SPacket) {
            if (invPhase == 2) {
                Packet<?> open = new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT);
                sendNetworkPacket((Connection) event.owner, open);
                invPhase = 3;
                invSentOpen = 1;
            } else if (invCount == 0 && (invSentOpen & 1) == 0) {
                invLastInteraction = invTick;
                return;
            }
        } else {
            if (!(packet instanceof CloseInventoryMenuC2SPacket)) {
                return;
            }
            if (invPhase == 2) {
                event.cancel = 1;
                return;
            }
            if (invCount == 0) {
                return;
            }
        }
        if (invCount != 32) {
            invPackets[invCount] = (Packet<?>) packet;
            ++invCount;
        }
        event.cancel = 1;
    }

    void finishInvWalkController(ClientGameContext c) {
        if (invSentOpen != 0) {
            return;
        }
        if (!flushInvWalkPackets(c.client)) {
            return;
        }
        invLastInteraction = invTick;
        if (c.focused != 1) {
            return;
        }
        while (Mouse.next()) {
        }
        while (Keyboard.next()) {
        }
    }

    String invWalkLabel() {
        return ValueFormatter.format("%s", mode.choice(mode.index()));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(mode);
        if (mode.index() == 0) return;
        w.show(stopOnClick);
        if (stopOnClick.index() != 2) w.show(stopDuration);
        w.heading("Other containers");
        w.show(stopNonInv);
    }
}

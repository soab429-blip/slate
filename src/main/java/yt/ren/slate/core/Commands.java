package yt.ren.slate.core;

import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandSuggestionsC2SPacket;
import yt.ren.slate.feature.utility.Friends;
import yt.ren.slate.hook.ClientHooks;
import yt.ren.slate.module.Module;
import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.text.ModifiedUtf8;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Commands implements ClientHooks.Commands {
    private static class Argument {
        final String name;
        final int kind;
        final int flags;

        Argument(String name, int kind, int flags) {
            this.name = name;
            this.kind = kind;
            this.flags = flags;
        }
    }

    @FunctionalInterface
    private interface Handler {
        void run(ClientGameContext context, Object[] values);
    }

    private static class Node {
        final String name;
        final String description;
        final Argument[] arguments;
        final Handler handler;
        final Node[] children;

        Node(String name, String description, Argument[] arguments, Handler handler, Node[] children) {
            this.name = name;
            this.description = description;
            this.arguments = arguments;
            this.handler = handler;
            this.children = children;
        }
    }

    private static final String[] ARGUMENT_TYPES = {null, "string", "integer", "yes/no"};

    private final Node root;

    public Commands(Friends friends, Module gui) {
        Node[] friendCommands = {
                new Node("add", "Add a friend", new Argument[]{new Argument("username", 1, 0)},
                        (c, v) -> friends.commandAdd(c, (byte[]) v[0]), null),
                new Node("remove", "Remove a friend", new Argument[]{new Argument("username", 1, 0)},
                        (c, v) -> friends.commandRemove(c, (byte[]) v[0]), null),
                new Node("addnearby", "Add nearby players as friends",
                        new Argument[]{new Argument("range", 2, 0), new Argument("replace", 3, 1)},
                        (c, v) -> friends.commandNearby(c, (Long) v[0], (Integer) v[1]), null),
                new Node("clear", "Remove all friends", new Argument[0], (c, v) -> friends.commandClear(c), null),
        };
        Node[] children = {
                new Node("f", "Manage your friends list", new Argument[0], null, friendCommands),
                new Node("gui", "Opens the GUI", new Argument[0],
                        (c, v) -> c.registry.queueModuleChange(gui, 1), null),
        };
        root = new Node("root", "All available commands", new Argument[0], null, children);
    }

    private static String prefixText(byte[] prefix) {
        int length = 0;
        while (length < prefix.length && prefix[length] != 0) {
            ++length;
        }
        return new String(prefix, 0, length, StandardCharsets.UTF_8);
    }

    private void help(Node node, ByteTextBuffer prefix) {
        prefix.finish();
        String text = prefixText(prefix.data);
        ClientChat.display("§7%s:", node.description);
        for (Node child : node.children) {
            ClientChat.display("§f  %s%s §7- %s", text, child.name, child.description);
        }
        if (node == root) {
            ClientChat.display("§f  !!<message> §7- Send §o!message §7in chat");
        }
    }

    private void usage(Node node, ByteTextBuffer prefix) {
        prefix.finish();
        byte[] storage = new byte[256];
        ByteTextBuffer message = new ByteTextBuffer(storage, 256);
        message.appendFormat("§c%s", prefixText(prefix.data));
        for (Argument argument : node.arguments) {
            if (argument.name == null) {
                continue;
            }
            boolean optional = (argument.flags & 1) != 0;
            message.appendFormat("%c§o%s§c: %s%c ", (int) (optional ? '[' : '<'), argument.name,
                    ARGUMENT_TYPES[argument.kind], (int) (optional ? ']' : '>'));
        }
        message.finish();

        ClientChat.display(prefixText(storage));
    }

    private static class Cursor {
        final byte[] data;
        final int length;
        int position;

        Cursor(byte[] data) {
            this.data = data;
            int end = 0;
            while (end < data.length && data[end] != 0) {
                ++end;
            }
            this.length = end;
        }

        int[] next() {
            int start = position;
            while (position < length && data[position] != ' ') {
                ++position;
            }
            int tokenLength = position - start;
            while (position < length && data[position] == ' ') {
                ++position;
            }
            return new int[]{start, tokenLength};
        }
    }

    void dispatchText(ClientGameContext context, byte[] text) {
        byte[] storage = new byte[256];
        ByteTextBuffer prefix = new ByteTextBuffer(storage, 256);
        prefix.appendCharacter((byte) '!');
        Cursor input = new Cursor(text);
        Node node = root;
        while (node.children != null) {
            if (input.position == input.length) {
                help(node, prefix);
                return;
            }
            int[] token = input.next();
            Node match = null;
            for (Node child : node.children) {
                byte[] name = child.name.getBytes(StandardCharsets.ISO_8859_1);
                if (name.length == token[1]
                        && Arrays.equals(name, Arrays.copyOfRange(text, token[0], token[0] + token[1]))) {
                    match = child;
                    break;
                }
            }
            if (match == null) {
                help(node, prefix);
                return;
            }
            prefix.appendFormat("%s ", match.name);
            node = match;
        }
        Object[] values = new Object[3];
        for (int index = 0; index < node.arguments.length; ++index) {
            Argument argument = node.arguments[index];
            boolean missing = input.position == input.length;
            if (missing && (argument.flags & 1) == 0) {
                ClientChat.display("§cMissing argument §o%s§c.", argument.name);
                usage(node, prefix);
                return;
            }
            int[] token = missing ? new int[]{0, 0} : input.next();
            String error = null;
            if (argument.kind == 1) {
                values[index] = missing ? null : Arrays.copyOfRange(text, token[0], token[0] + token[1]);
            } else if (argument.kind == 2) {
                long value = 0;
                if (!missing) {
                    value = 0;
                    for (int i = 0; i < token[1]; ++i) {
                        int digit = text[token[0] + i] & 0xff;
                        long next = (digit & 15) + value * 10;
                        if (digit < '0' || digit > '9' || Long.compareUnsigned(next, value) < 0) {
                            error = "§cArgument §o%s§c must be a valid integer.";
                            break;
                        }
                        value = next;
                    }
                }
                if (error == null) {
                    values[index] = value;
                }
            } else if (argument.kind == 3) {
                int value = 0;
                if (!missing) {
                    int shift = (text[token[0]] & 0xff) - 0x46;
                    if (Integer.compareUnsigned(shift, 0x33) > 0
                            || (((0x10100000101L | 0x8400000084000L) >>> shift) & 1) == 0) {
                        error = "§cArgument §o%s§c must be a boolean (y/n).";
                    } else {
                        value = ((0x10100000101L >>> shift) & 1) == 0 ? 1 : 0;
                    }
                }
                if (error == null) {
                    values[index] = value;
                }
            }
            if (error != null) {
                ClientChat.display(error, argument.name);
                usage(node, prefix);
                return;
            }
        }
        if (input.position < input.length) {
            ClientChat.display("§cToo many arguments provided.");
            usage(node, prefix);
            return;
        }
        node.handler.run(context, values);
    }

    @Override
    public int handleChat(ClientGameContext context, ChatMessageC2SPacket packet) {
        String message = packet.message;
        if (message.isEmpty()) {
            return 0;
        }
        String copied = message.length() > 100 ? message.substring(0, 100) : message;
        byte[] text = ModifiedUtf8.encode(copied);
        if (text[0] != '!') {
            return 0;
        }
        if (message.length() >= 2 && text[1] == '!') {
            byte[] escaped = Arrays.copyOfRange(text, 1, text.length);
            packet.message = ModifiedUtf8.decode(escaped, escaped.length);
            return 0;
        }
        dispatchText(context, Arrays.copyOfRange(text, 1, text.length));
        return 1;
    }

    @Override
    public int handleCompletion(ClientGameContext context, CommandSuggestionsC2SPacket packet) {
        String message = packet.command;
        if (message.isEmpty()) {
            return 0;
        }
        return message.charAt(0) == '!' ? 1 : 0;
    }
}

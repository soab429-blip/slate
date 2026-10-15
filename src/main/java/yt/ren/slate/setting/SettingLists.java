package yt.ren.slate.setting;

import java.nio.charset.StandardCharsets;

public class SettingLists {
    public static class NamedItem {
        public final String name;
        public final int id;

        public NamedItem(String name, int id) {
            this.name = name;
            this.id = id;
        }
    }

    public static class ColoredItem {
        public final String name;
        public final float[] color;
        public final int id;

        ColoredItem(String name, float[] color, int id) {
            this.name = name;
            this.color = color;
            this.id = id;
        }
    }

    public static class FriendEntry {
        public long uuidMost;
        public long uuidLeast;
        public long lastSeen;
        public long reserved;

        public FriendEntry(long uuidMost, long uuidLeast, long lastSeen, long reserved) {
            this.uuidMost = uuidMost;
            this.uuidLeast = uuidLeast;
            this.lastSeen = lastSeen;
            this.reserved = reserved;
        }

        byte[] bytes() {
            byte[] out = new byte[32];
            long[] words = {uuidMost, uuidLeast, lastSeen, reserved};
            for (int w = 0; w < 4; w++) {
                for (int b = 0; b < 8; b++) {
                    out[w * 8 + b] = (byte) (words[w] >>> (8 * b));
                }
            }
            return out;
        }
    }

    public static int compareFriendIdentity(FriendEntry a, FriendEntry b) {
        byte[] x = a.bytes();
        byte[] y = b.bytes();
        for (int i = 0; i < 16; i++) {
            if (x[i] != y[i]) {
                return (x[i] & 0xFF) < (y[i] & 0xFF) ? -1 : 1;
            }
        }
        return 0;
    }

    private static int subtractIds(int a, int b) {
        return a - b;
    }

    public final CompactList<NamedItem> autoSoup = new CompactList<>();
    public final CompactList<NamedItem> autoTool = new CompactList<>();
    public final CompactList<NamedItem> weapons = new CompactList<>();
    public final CompactList<Byte> teams = new CompactList<>();
    public final CompactList<FriendEntry> friends = new CompactList<>();
    public final ColoredList blockEsp = new ColoredList();
    public final ColoredList itemEsp = new ColoredList();

    public Runnable blockCacheInvalidator;

    public static class ColoredList {
        public ColoredItem[] data;
        public int count;
        int capacity;
    }

    static void appendItemNameCharacter(StringBuilder text, int c) {
        int length = text.length();
        int u = c & 0xFF;
        if (length < 100 && (((u & 0xdf) - 'A' & 0xFF) < 26 || u == ' ' || ((u - '0') & 0xFF) < 10)) {
            text.append((char) u);
        }
    }

    static void appendTeamColorCharacter(StringBuilder text, int c) {
        int length = text.length();
        int u = c & 0xFF;
        if (length < 100 && (((u - 'a') & 0xFF) < 26 || u == ' ' || ((u - 'A') & 0xFF) < 26)) {
            text.append((char) u);
        }
    }

    private ListStorage named(final CompactList<NamedItem> list, final boolean blocks) {
        return new ListStorage() {
            public long count() {
                return list.count();
            }

            public String name(long index) {
                return list.get((int) index).name;
            }

            public float[] color(long index) {
                return null;
            }

            public boolean insert(String name, float[] color) {
                int id = ItemCatalog.idFromName(name);
                if (id == -1) {
                    return false;
                }
                list.insert(new NamedItem(ItemCatalog.nameFromId(id), id), (a, b) -> subtractIds(a.id, b.id));
                return true;
            }

            public void remove(long index) {
                list.remove((int) index);
            }

            public void clear() {
                list.setCount(0);
            }

            public String[] suggestions() {
                return blocks ? ItemCatalog.blockSuggestions() : ItemCatalog.itemSuggestions();
            }

            public boolean acceptsInput() {
                return true;
            }

            public void inputCharacter(StringBuilder text, int character) {
                appendItemNameCharacter(text, character);
            }
        };
    }

    private void invalidateBlocks(ColoredList list) {
        if (list == blockEsp && blockCacheInvalidator != null) {
            blockCacheInvalidator.run();
        }
    }

    private ListStorage colored(final ColoredList list, final boolean blocks) {
        return new ListStorage() {
            public long count() {
                return list.count;
            }

            public String name(long index) {
                return list.data[(int) index].name;
            }

            public float[] color(long index) {
                return list.data[(int) index].color;
            }

            public boolean insert(String name, float[] color) {
                int id = ItemCatalog.idFromName(name);
                if (id == -1) {
                    return false;
                }
                if (list.data != null) {
                    int lo = 0;
                    int hi = list.count - 1;
                    while (lo <= hi) {
                        int mid = (lo + hi) >>> 1;
                        int r = subtractIds(id, list.data[mid].id);
                        if (r == 0) {
                            return false;
                        }
                        if (r < 0) {
                            hi = mid - 1;
                        } else {
                            lo = mid + 1;
                        }
                    }
                }
                int index = 0;
                while (index < list.count && id > list.data[index].id) {
                    ++index;
                }
                ColoredItem item = new ColoredItem(ItemCatalog.nameFromId(id),
                        new float[]{color[0], color[1], color[2]}, id);
                invalidateBlocks(list);
                if (list.count >= list.capacity) {
                    int capacity = list.data != null ? list.capacity * 2 : 8;
                    ColoredItem[] grown = new ColoredItem[capacity];
                    if (list.data != null) {
                        System.arraycopy(list.data, 0, grown, 0, list.count);
                    }
                    list.data = grown;
                    list.capacity = capacity;
                }
                System.arraycopy(list.data, index, list.data, index + 1, list.count - index);
                list.data[index] = item;
                ++list.count;
                return true;
            }

            public void remove(long index) {
                --list.count;
                System.arraycopy(list.data, (int) index + 1, list.data, (int) index, list.count - (int) index);
                invalidateBlocks(list);
            }

            public void clear() {
                list.count = 0;
                list.capacity = 0;
                list.data = null;
                invalidateBlocks(list);
            }

            public String[] suggestions() {
                return blocks ? ItemCatalog.blockSuggestions() : ItemCatalog.itemSuggestions();
            }

            public boolean acceptsInput() {
                return true;
            }

            public void inputCharacter(StringBuilder text, int character) {
                appendItemNameCharacter(text, character);
            }
        };
    }

    private static boolean decodeNibble(int input, int[] output) {
        int value = (input - '0') & 0xFF;
        if (value <= 9) {
            output[0] = value;
            return true;
        }
        if (((input - 'a') & 0xFF) > 25) {
            return false;
        }
        output[0] = (input - 87) & 0xFF;
        return true;
    }

    public ListStorage friendsStorage() {
        return new ListStorage() {
            public long count() {
                return friends.count();
            }

            public String name(long index) {
                byte[] item = friends.get((int) index).bytes();
                StringBuilder out = new StringBuilder(48);
                String digits = "0123456789abcdef";
                for (int i = 0; i < 24; i++) {
                    out.append(digits.charAt((item[i] >> 4) & 15)).append(digits.charAt(item[i] & 15));
                }
                return out.toString();
            }

            public float[] color(long index) {
                return null;
            }

            public boolean insert(String text, float[] color) {
                byte[] raw = text.getBytes(StandardCharsets.UTF_8);
                if (raw.length < 48) {
                    return false;
                }
                byte[] item = new byte[32];
                int[][] ranges = {{0, 16}, {16, 24}};
                int[] high = new int[1];
                int[] low = new int[1];
                for (int[] bounds : ranges) {
                    for (int i = bounds[0]; i < bounds[1]; i++) {
                        if (!decodeNibble(raw[i * 2] & 0xFF, high) || !decodeNibble(raw[i * 2 + 1] & 0xFF, low)) {
                            break;
                        }
                        item[i] = (byte) ((high[0] << 4) | low[0]);
                    }
                }
                long[] words = new long[4];
                for (int w = 0; w < 4; w++) {
                    for (int b = 0; b < 8; b++) {
                        words[w] |= (long) (item[w * 8 + b] & 0xFF) << (8 * b);
                    }
                }
                friends.insert(new FriendEntry(words[0], words[1], words[2], words[3]),
                        SettingLists::compareFriendIdentity);
                return true;
            }

            public void remove(long index) {
                friends.remove((int) index);
            }

            public void clear() {
                friends.setCount(0);
            }

            public String[] suggestions() {
                return null;
            }

            public boolean acceptsInput() {
                return false;
            }

            public void inputCharacter(StringBuilder text, int character) {
            }
        };
    }

    public ListStorage teamsStorage() {
        return new ListStorage() {
            public long count() {
                return teams.count();
            }

            public String name(long index) {
                return ItemCatalog.teamColorNames()[teams.get((int) index)];
            }

            public float[] color(long index) {
                return null;
            }

            public boolean insert(String name, float[] color) {
                String[] names = ItemCatalog.teamColorNames();
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(name)) {
                        teams.insert((byte) i, (a, b) -> a - b);
                        return true;
                    }
                }
                return false;
            }

            public void remove(long index) {
                teams.remove((int) index);
            }

            public void clear() {
                teams.setCount(0);
            }

            public String[] suggestions() {
                return ItemCatalog.teamColorNames();
            }

            public boolean acceptsInput() {
                return true;
            }

            public void inputCharacter(StringBuilder text, int character) {
                appendTeamColorCharacter(text, character);
            }
        };
    }

    public ListStorage autoSoupStorage() {
        return named(autoSoup, false);
    }

    public ListStorage autoToolStorage() {
        return named(autoTool, true);
    }

    public ListStorage weaponsStorage() {
        return named(weapons, false);
    }

    public ListStorage blockEspStorage() {
        return colored(blockEsp, true);
    }

    public ListStorage itemEspStorage() {
        return colored(itemEsp, false);
    }
}

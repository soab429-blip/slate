package yt.ren.slate.setting;

import java.nio.charset.StandardCharsets;

public class ItemCatalog {
    private static int strcmp(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int d = (x[i] & 0xFF) - (y[i] & 0xFF);
            if (d != 0) {
                return d;
            }
        }
        return x.length - y.length;
    }

    public static int idFromName(String name) {
        int lo = 0;
        int hi = ItemCatalogData.BY_NAME_NAMES.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int result = strcmp(name, ItemCatalogData.BY_NAME_NAMES[mid]);
            if (result == 0) {
                return ItemCatalogData.BY_NAME_IDS[mid];
            }
            if (result < 0) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return -1;
    }

    public static String nameFromId(int id) {
        int lo = 0;
        int hi = ItemCatalogData.BY_ID_IDS.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int result = id - ItemCatalogData.BY_ID_IDS[mid];
            if (result == 0) {
                return ItemCatalogData.BY_ID_NAMES[mid];
            }
            if (result < 0) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return null;
    }

    public static String[] itemSuggestions() {
        return ItemCatalogData.ITEM_SUGGESTIONS;
    }

    public static String[] blockSuggestions() {
        return ItemCatalogData.BLOCK_SUGGESTIONS;
    }

    public static String[] teamColorNames() {
        return ItemCatalogData.TEAM_COLOR_LABELS;
    }
}

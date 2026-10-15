package yt.ren.slate.config.profile;

import yt.ren.slate.text.ByteTextBuffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfileRegistry {
    public static class Entry {
        public final byte[] name;

        public final int displayEnd;
        public final byte[] modificationText = new byte[32];
        public final long ageSeconds;
        public int bindCode;

        Entry(byte[] name, long ageSeconds) {
            this.name = name;
            this.displayEnd = Math.min(name.length, 15);
            this.ageSeconds = ageSeconds;
        }

        public byte[] modification() {
            int length = 0;
            while (length < modificationText.length && modificationText[length] != 0) {
                ++length;
            }
            return Arrays.copyOf(modificationText, length);
        }
    }

    public final List<Entry> entries = new ArrayList<>();

    private static int strcmp(byte[] a, byte[] b) {
        int i = 0;
        while (true) {
            int x = i < a.length ? a[i] & 0xff : 0;
            int y = i < b.length ? b[i] & 0xff : 0;
            if (x != y) {
                return x - y;
            }
            if (x == 0) {
                return 0;
            }
            ++i;
        }
    }

    public Entry find(byte[] name) {
        for (Entry entry : entries) {
            if (strcmp(name, entry.name) == 0) {
                return entry;
            }
        }
        return null;
    }

    private static void copy(byte[] target, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(bytes, 0, target, 0, bytes.length);
    }

    public int refresh(ProfileConfiguration configuration) {
        List<Entry> saved = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.bindCode != 0) {
                saved.add(entry);
            }
        }
        entries.clear();
        int status = configuration.enumerateProfiles(file -> {
            Entry entry = new Entry(file.name.clone(), file.ageSeconds);
            for (Entry previous : saved) {
                if (strcmp(previous.name, entry.name) == 0) {
                    entry.bindCode = previous.bindCode;
                    break;
                }
            }
            if (file.ageSeconds == -1L) {
                copy(entry.modificationText, "Unknown modification dat");
            } else {
                long days = file.ageSeconds / 86400;
                if (days == 0) {
                    copy(entry.modificationText, "Modified today.");
                } else if (days == 1) {
                    copy(entry.modificationText, "Modified 1 day ago.");
                } else {
                    new ByteTextBuffer(entry.modificationText, 25).appendFormat("Modified %lld days ago.", days);
                }
            }
            entries.add(entry);
            return 0;
        });
        entries.sort((a, b) -> {
            if (a.ageSeconds < b.ageSeconds) {
                return -1;
            }
            if (a.ageSeconds > b.ageSeconds) {
                return 1;
            }
            return strcmp(a.name, b.name);
        });
        return status;
    }
}

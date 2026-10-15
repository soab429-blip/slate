package yt.ren.slate.config.profile;

import yt.ren.slate.platform.Desktop;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

public class ProfileFiles {
    public static class Entry {
        public final byte[] name;
        public final long ageSeconds;

        Entry(byte[] name, long ageSeconds) {
            this.name = name;
            this.ageSeconds = ageSeconds;
        }
    }

    @FunctionalInterface
    public interface Callback {
        int accept(Entry entry);
    }

    public static class PathHolder {
        String value;
    }

    private static final long FILETIME_EPOCH = 116444736000000000L;

    public int home(PathHolder path) {
        String value = System.getenv("USERPROFILE");
        if (value == null || value.isEmpty()) {
            value = System.getProperty("user.home");
        }
        if (value == null || value.isEmpty()) {
            return 1004;
        }
        path.value = value;
        return 0;
    }

    public int append(PathHolder path, String component) {
        path.value = Paths.get(path.value, component).toString();
        return 0;
    }

    public int ensureDirectory(PathHolder path) {
        try {
            Files.createDirectory(Paths.get(path.value));
            return 0;
        } catch (FileAlreadyExistsException exists) {
            return 0;
        } catch (IOException | RuntimeException exception) {
            return 1014;
        }
    }

    public int remove(PathHolder path) {
        try {
            Path target = Paths.get(path.value);
            if (Files.isDirectory(target)) {
                return 1011;
            }
            Files.delete(target);
            return 0;
        } catch (IOException | RuntimeException exception) {
            return 1011;
        }
    }

    public int explore(PathHolder path) {
        try {
            Desktop.open(Paths.get(path.value));
            return 0;
        } catch (IOException | RuntimeException exception) {
            return 4000;
        }
    }

    public int read(PathHolder path, byte[][] output) {
        Path target = Paths.get(path.value);
        if (!Files.isRegularFile(target)) {
            return 1008;
        }
        try {
            output[0] = Files.readAllBytes(target);
            return 0;
        } catch (NoSuchFileException missing) {
            return 1008;
        } catch (IOException | RuntimeException exception) {
            return 1009;
        }
    }

    public int write(PathHolder path, byte[] text, int overwrite) {
        Path target = Paths.get(path.value);
        try {
            if (overwrite != 0) {
                Files.write(target, text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            } else {
                Files.write(target, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            return 0;
        } catch (FileAlreadyExistsException exists) {
            return 1012;
        } catch (NoSuchFileException missing) {
            return 1008;
        } catch (IOException | RuntimeException exception) {
            return 1010;
        }
    }

    private static long filetime(Instant instant) {
        return instant.getEpochSecond() * 10000000L + instant.getNano() / 100 + FILETIME_EPOCH;
    }

    public int enumerate(PathHolder path, Callback callback) {
        Path directory = Paths.get(path.value);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        long now = filetime(Instant.now());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                if (attributes.isDirectory()) {
                    continue;
                }
                String name = file.getFileName().toString();
                byte[] converted = new byte[name.length()];
                for (int i = 0; i < name.length(); ++i) {
                    char c = name.charAt(i);
                    if (c > 0xff) {
                        return 1006;
                    }
                    converted[i] = (byte) c;
                }
                long timestamp = filetime(attributes.lastModifiedTime().toInstant());
                long age = Long.compareUnsigned(timestamp - 1, now) < 0 ? Long.divideUnsigned(now - timestamp, 10000000L)
                        : -1L;
                int status = callback.accept(new Entry(converted, age));
                if (status != 0) {
                    return status;
                }
            }
            return 0;
        } catch (IOException | RuntimeException exception) {
            return 1015;
        }
    }
}

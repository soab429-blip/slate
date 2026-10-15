package yt.ren.slate.input;

import java.util.Arrays;

public class KeyBindings {
    @FunctionalInterface
    public interface Handler {
        int handle(Object value, int key, int state);
    }

    @FunctionalInterface
    public interface Destructor {
        void destroy(Object value);
    }

    static class Bucket {
        int key;
        int count;
        Object[] values;
    }

    final Handler handler;
    final Destructor destructor;
    final int multiple;
    Bucket[] buckets = new Bucket[0];
    int count;

    public KeyBindings(Handler handler, Destructor destructor, int multiple) {
        this.handler = handler;
        this.destructor = destructor;
        this.multiple = multiple;
    }

    private static int compare(int first, int second) {
        return first - second;
    }

    private Bucket bucketFor(int key) {
        int lo = 0;
        int hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int result = compare(key, buckets[mid].key);
            if (result == 0) {
                return buckets[mid];
            }
            if (result < 0) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return null;
    }

    private int indexOf(Bucket bucket) {
        for (int i = 0; i < count; i++) {
            if (buckets[i] == bucket) {
                return i;
            }
        }
        return -1;
    }

    public boolean insert(int key, Object value) {
        if (key == 0) {
            return true;
        }
        Bucket bucket = bucketFor(key);
        if (bucket == null) {
            int position = 0;
            while (position < count && compare(key, buckets[position].key) > 0) {
                ++position;
            }
            if (count == buckets.length) {
                buckets = Arrays.copyOf(buckets, buckets.length == 0 ? 8 : buckets.length * 2);
            }
            System.arraycopy(buckets, position, buckets, position + 1, count - position);
            bucket = new Bucket();
            bucket.key = key;
            bucket.values = new Object[0];
            buckets[position] = bucket;
            ++count;
        }
        if (multiple == 1) {
            for (int i = 0; i < bucket.count; i++) {
                if (bucket.values[i] == value) {
                    return true;
                }
            }
        } else if (bucket.count != 0) {
            if (destructor != null) {
                destructor.destroy(bucket.values[0]);
            }
            bucket.count = 0;
            bucket.values = new Object[0];
        }
        if (bucket.count == bucket.values.length) {
            bucket.values = Arrays.copyOf(bucket.values, bucket.values.length == 0 ? 8 : bucket.values.length * 2);
        }
        bucket.values[bucket.count++] = value;
        return true;
    }

    public void remove(int key, Object value) {
        if (key == 0) {
            return;
        }
        Bucket bucket = bucketFor(key);
        if (bucket == null || bucket.count == 0) {
            return;
        }
        int originalCount = bucket.count;
        if (multiple == 1) {
            int index = 0;
            while (index < originalCount && bucket.values[index] != value) {
                ++index;
            }
            if (index == originalCount) {
                return;
            }
            int current = originalCount;
            if (destructor != null) {
                destructor.destroy(bucket.values[0]);
                current = bucket.count;
            }
            bucket.count = current - 1;
            System.arraycopy(bucket.values, index + 1, bucket.values, index, current - 1 - index);
            if (originalCount != 1) {
                return;
            }
        } else if (destructor != null) {
            destructor.destroy(bucket.values[0]);
        }
        bucket.count = 0;
        bucket.values = new Object[0];
        int position = indexOf(bucket);
        int total = count;
        count = total - 1;
        System.arraycopy(buckets, position + 1, buckets, position, total - position - 1);
        buckets[count] = null;
    }

    public Object find(int key, Object value) {
        if (key == 0) {
            return null;
        }
        Bucket bucket = bucketFor(key);
        if (bucket == null) {
            return null;
        }
        if (multiple == 1) {
            for (int i = 0; i < bucket.count; i++) {
                if (bucket.values[i] == value) {
                    return value;
                }
            }
        } else if (bucket.count != 0) {
            return bucket.values[0];
        }
        return null;
    }

    public int dispatch(int key, int state) {
        Bucket bucket = bucketFor(key);
        if (bucket == null || bucket.count == 0) {
            return 0;
        }
        int result = 0;
        int i = 0;
        do {
            result = (result | handler.handle(bucket.values[i], key, state)) & 0xFF;
            ++i;
        } while (i < bucket.count);
        return result & 1;
    }

    public void releaseAll() {
        int total = count;
        for (int i = 0; i < total; i++) {
            if (buckets[i].count != 0) {
                Bucket bucket = buckets[i];
                int value = 0;
                do {
                    handler.handle(bucket.values[value], bucket.key, 0);
                    ++value;
                } while (value < bucket.count);
                total = count;
            }
        }
    }
}

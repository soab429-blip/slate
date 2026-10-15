package yt.ren.slate.setting;

import java.util.Arrays;

public class CompactList<T> {
    @FunctionalInterface
    public interface Compare<T> {
        int compare(T value, T item);
    }

    private Object[] data = new Object[0];
    private int count;

    public int count() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void release() {
        data = new Object[0];
        count = 0;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        return (T) data[index];
    }

    public void append(T value) {
        reserve();
        data[count++] = value;
    }

    private void reserve() {
        if (data.length <= count) {
            data = Arrays.copyOf(data, data.length == 0 ? 8 : data.length * 2);
        }
    }

    @SuppressWarnings("unchecked")
    private T insert(T value, Compare<T> compare, boolean unique) {
        int position = 0;
        if (count != 0) {
            int lower = 0;
            int upper = count;
            do {
                position = lower + ((upper - lower) >>> 1);
                int result = compare.compare(value, (T) data[position]);
                if (result == 0) {
                    if (unique) {
                        return (T) data[position];
                    }
                    break;
                }
                if (result < 0) {
                    upper = position;
                } else {
                    lower = position + 1;
                    position = upper;
                }
            } while (lower < upper);
        }
        reserve();
        System.arraycopy(data, position, data, position + 1, count - position);
        data[position] = value;
        ++count;
        return value;
    }

    public T insert(T value, Compare<T> compare) {
        return insert(value, compare, true);
    }

    public T insertWithDuplicates(T value, Compare<T> compare) {
        return insert(value, compare, false);
    }

    @SuppressWarnings("unchecked")
    public T find(T value, Compare<T> compare) {
        int lower = 0;
        int upper = count;
        while (lower < upper) {
            int middle = lower + ((upper - lower) >>> 1);
            int result = compare.compare(value, (T) data[middle]);
            if (result == 0) {
                return (T) data[middle];
            }
            if (result < 0) {
                upper = middle;
            } else {
                lower = middle + 1;
            }
        }
        return null;
    }

    public void remove(int index) {
        --count;
        System.arraycopy(data, index + 1, data, index, count - index);
        data[count] = null;
    }

    public void removeItem(T item) {
        for (int i = 0; i < count; i++) {
            if (data[i] == item) {
                remove(i);
                return;
            }
        }
    }
}

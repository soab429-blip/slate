package yt.ren.slate.util.math;

import java.util.Comparator;

public class QSort {
    private static final int CUTOFF = 8;
    private static final int STKSIZ = 8 * 8 - 2;

    public static <T> void sort(T[] base, int num, Comparator<? super T> compare) {
        if (num < 2) {
            return;
        }
        int[] lostk = new int[STKSIZ];
        int[] histk = new int[STKSIZ];
        int stkptr = 0;
        int lo = 0;
        int hi = num - 1;
        while (true) {
            int size = hi - lo + 1;
            if (size <= CUTOFF) {
                shortsort(base, lo, hi, compare);
            } else {
                int mid = lo + size / 2;
                if (cmp(base, lo, mid, compare) > 0) {
                    swap(base, lo, mid);
                }
                if (cmp(base, lo, hi, compare) > 0) {
                    swap(base, lo, hi);
                }
                if (cmp(base, mid, hi, compare) > 0) {
                    swap(base, mid, hi);
                }
                int loguy = lo;
                int higuy = hi;
                for (; ; ) {
                    if (mid > loguy) {
                        do {
                            loguy++;
                        } while (loguy < mid && cmp(base, loguy, mid, compare) <= 0);
                    }
                    if (mid <= loguy) {
                        do {
                            loguy++;
                        } while (loguy <= hi && cmp(base, loguy, mid, compare) <= 0);
                    }
                    do {
                        higuy--;
                    } while (higuy > mid && cmp(base, higuy, mid, compare) > 0);
                    if (higuy < loguy) {
                        break;
                    }
                    swap(base, loguy, higuy);
                    if (mid == higuy) {
                        mid = loguy;
                    }
                }
                higuy++;
                if (mid < higuy) {
                    do {
                        higuy--;
                    } while (higuy > mid && cmp(base, higuy, mid, compare) == 0);
                }
                if (mid >= higuy) {
                    do {
                        higuy--;
                    } while (higuy > lo && cmp(base, higuy, mid, compare) == 0);
                }
                if (higuy - lo >= hi - loguy) {
                    if (lo < higuy) {
                        lostk[stkptr] = lo;
                        histk[stkptr] = higuy;
                        ++stkptr;
                    }
                    if (loguy < hi) {
                        lo = loguy;
                        continue;
                    }
                } else {
                    if (loguy < hi) {
                        lostk[stkptr] = loguy;
                        histk[stkptr] = hi;
                        ++stkptr;
                    }
                    if (lo < higuy) {
                        hi = higuy;
                        continue;
                    }
                }
            }
            --stkptr;
            if (stkptr >= 0) {
                lo = lostk[stkptr];
                hi = histk[stkptr];
            } else {
                return;
            }
        }
    }

    private static <T> void shortsort(T[] base, int lo, int hi, Comparator<? super T> compare) {
        while (hi > lo) {
            int max = lo;
            for (int p = lo + 1; p <= hi; p++) {
                if (cmp(base, p, max, compare) > 0) {
                    max = p;
                }
            }
            swap(base, max, hi);
            hi--;
        }
    }

    private static <T> int cmp(T[] base, int a, int b, Comparator<? super T> compare) {
        return compare.compare(base[a], base[b]);
    }

    private static <T> void swap(T[] base, int a, int b) {
        if (a != b) {
            T t = base[a];
            base[a] = base[b];
            base[b] = t;
        }
    }
}

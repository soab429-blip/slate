package yt.ren.slate.ui.vector;

public class VectorCache {
    public static class Point {
        public float x;
        public float y;
        public float dx;
        public float dy;
        public float length;
        public float dmx;
        public float dmy;
        public int flags;

        void set(Point o) {
            x = o.x;
            y = o.y;
            dx = o.dx;
            dy = o.dy;
            length = o.length;
            dmx = o.dmx;
            dmy = o.dmy;
            flags = o.flags;
        }
    }

    public static class Path {
        public int first;
        public int count;
        public int closed;
        public int bevelCount;
        public int fill = -1;
        public int fillCount;
        public int stroke = -1;
        public int strokeCount;
        public int winding;
        public int convex;

        void clear() {
            first = 0;
            count = 0;
            closed = 0;
            bevelCount = 0;
            fill = -1;
            fillCount = 0;
            stroke = -1;
            strokeCount = 0;
            winding = 0;
            convex = 0;
        }
    }

    public Point[] points;
    public int pointCount;
    public int pointCapacity;
    public Path[] paths;
    public int pathCount;
    public int pathCapacity;
    public float[] vertices;
    public int vertexCount;
    public int vertexCapacity;
    public final float[] bounds = new float[4];

    VectorCache() {
        points = newPoints(128);
        pointCapacity = 128;
        paths = newPaths(16);
        pathCapacity = 16;
        vertices = new float[256 * 4];
        vertexCapacity = 256;
    }

    static Point[] newPoints(int count) {
        Point[] result = new Point[count];
        for (int i = 0; i < count; ++i) {
            result[i] = new Point();
        }
        return result;
    }

    static Path[] newPaths(int count) {
        Path[] result = new Path[count];
        for (int i = 0; i < count; ++i) {
            result[i] = new Path();
        }
        return result;
    }

    void growPoints(int capacity) {
        Point[] next = newPoints(capacity);
        int keep = Math.min(points.length, capacity);
        for (int i = 0; i < keep; ++i) {
            next[i].set(points[i]);
        }
        points = next;
    }

    void growPaths(int capacity) {
        Path[] next = new Path[capacity];
        int keep = Math.min(paths.length, capacity);
        System.arraycopy(paths, 0, next, 0, keep);
        for (int i = keep; i < capacity; ++i) {
            next[i] = new Path();
        }
        paths = next;
    }

    void growVertices(int capacity) {
        float[] next = new float[capacity * 4];
        System.arraycopy(vertices, 0, next, 0, Math.min(vertices.length, next.length));
        vertices = next;
    }
}

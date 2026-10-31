package autotrade;

/** Cardinal BFS over server collision tiles. No diagonal corner cutting. */
public final class GridPath {
    public static int[] find(int[] tiles,int width,int height,int sx,int sy,int tx,int ty,int radius) {
        if (tiles==null || width<=0 || height<=0 || (long)width*height>tiles.length
                || sx<0 || sy<0 || sx>=width || sy>=height) return null;

        // Clamp destination coordinates to map boundaries
        tx = Math.max(0, Math.min(width - 1, tx));
        ty = Math.max(0, Math.min(height - 1, ty));

        if (sx == tx && sy == ty) return new int[0];

        int length = width * height, start = sy * width + sx;
        int targetIdx = ty * width + tx;
        boolean targetBlocked = (tiles[targetIdx] == 1 || tiles[targetIdx] == -1);
        int effectiveRadius = Math.max(radius, targetBlocked ? 1 : 0);

        int[] parent = new int[length], queue = new int[length];
        for (int i = 0; i < length; i++) parent[i] = -1;
        int head = 0, tail = 1, end = -1;
        queue[0] = start; parent[start] = start;

        int bestNode = -1, bestDist = Integer.MAX_VALUE;

        while (head < tail) {
            int n = queue[head++], x = n % width, y = n / width;
            int dist = Math.abs(x - tx) + Math.abs(y - ty);
            if (dist < bestDist) {
                bestDist = dist;
                bestNode = n;
            }
            if (dist <= effectiveRadius) { end = n; break; }
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int next = ny * width + nx;
                if (parent[next] != -1 || tiles[next] == 1 || tiles[next] == -1) continue;
                parent[next] = n; queue[tail++] = next;
            }
        }

        // If target tile was blocked (e.g. portal arrow inside border wall), fall back to closest reachable walkable tile
        if (end < 0 && targetBlocked && bestNode >= 0 && bestDist <= 3) {
            end = bestNode;
        }

        if (end < 0) return null;
        if (end == start) return new int[0];

        int count = 0; for (int n = end; n != start; n = parent[n]) count++;
        int[] result = new int[count];
        for (int n = end; count > 0; n = parent[n]) result[--count] = n;
        return result;
    }
}


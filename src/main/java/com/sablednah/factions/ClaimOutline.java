package com.sablednah.factions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The outline of a set of claimed chunks: one shape per connected block of land, with holes.
 *
 * <h2>Why this is its own class, and imports nothing but {@code java.util}</h2>
 *
 * <p>So that {@link FactionsSelfTest} can exercise the real tracer rather than a copy of it. The
 * only consumer is the JourneyMap overlay, which lives in a package that is never loaded unless
 * JourneyMap is installed — a test living there could never run. Same reasoning as Standards'
 * {@code PermissionRules}: keep the decision pure, test the decision.</p>
 *
 * <h2>What it is for</h2>
 *
 * <p>The map draws a faction's territory as a filled, stroked region. The obvious implementation —
 * one rectangle per horizontal run of chunks — strokes each rectangle all the way round, so two
 * rows of the same faction stacked on each other get a border drawn <b>between</b> them. The fill
 * looks right and the borders are nonsense. That shipped, and was reported from a real map.</p>
 *
 * <p>So the boundary is computed rather than approximated. Each claimed chunk contributes only the
 * edges it does <em>not</em> share with another claimed chunk, wound so the land is always on the
 * same side, and those edges are stitched into closed rings. An interior edge is never emitted, so
 * it cannot be stroked.</p>
 *
 * <p>⚠ It takes <b>{@code int[] {x, z}}</b> rather than {@code ChunkPos}, and that is deliberate:
 * {@code ChunkPos} is a plain class with public fields on 1.21.11 and a record with accessors on
 * 26.x, so naming it here would make this file diverge on every branch. The one loop that converts
 * lives in the overlay, which has to diverge anyway.</p>
 *
 * <p>Corners are in <b>chunk</b> units, and a corner is a chunk <em>boundary</em> — so a single
 * chunk at (0,0) traces (0,0) → (1,0) → (1,1) → (0,1). Scaling to blocks is the caller's job.</p>
 */
public final class ClaimOutline {

    /** One connected block of land: its outer ring, and any enclosed pockets that are not its. */
    public record Shape(List<Corner> outer, List<List<Corner>> holes) {}

    /** A chunk-grid corner. */
    public record Corner(int x, int z) {}

    private ClaimOutline() {}

    /**
     * The same land as plain rectangles: one per horizontal run of chunks.
     *
     * <h2>⚠ Why the fill is rectangles when the border is an outline</h2>
     *
     * <p>Because JourneyMap resolves a tooltip against an overlay's <b>bounding box</b>, not its
     * shape — {@code BaseOverlayDrawStep.screenBounds} is a {@code Rectangle2D}. One polygon for a
     * whole territory therefore claims every point in its bounding rectangle: hovering the hole in
     * the middle of a faction reported that faction, and so did ground three chunks outside it that
     * merely fell inside the box. Reported from a real map, twice in one sitting.</p>
     *
     * <p>A run is a rectangle, so its bounding box <em>is</em> its area, exactly. Emitting one
     * overlay per run makes the tooltip truthful with no hit-testing of our own — and it is why
     * they are separate overlays rather than several shapes in one, since the bounds belong to the
     * overlay rather than to each shape.</p>
     *
     * @return one {@code {x0, z, x1}} per run, inclusive at both ends, in chunk units
     */
    public static List<int[]> rows(List<int[]> claims) {
        Set<Long> held = new HashSet<>();
        for (int[] c : claims) {
            held.add(pack(c[0], c[1]));
        }
        List<int[]> out = new ArrayList<>();
        Set<Long> done = new HashSet<>();
        for (int[] c : claims) {
            int x = c[0];
            int z = c[1];
            if (!done.add(pack(x, z))) {
                continue;
            }
            // Start only at a run's left end, or the same row is emitted once per chunk in it.
            if (held.contains(pack(x - 1, z))) {
                continue;
            }
            int x1 = x;
            while (held.contains(pack(x1 + 1, z))) {
                x1++;
                done.add(pack(x1, z));
            }
            out.add(new int[] {x, z, x1});
        }
        return out;
    }

    public static List<Shape> trace(List<int[]> claims) {
        Set<Long> held = new HashSet<>();
        for (int[] c : claims) {
            held.add(pack(c[0], c[1]));
        }

        // From-corner -> the corners it leads to, wound clockwise on screen (x east, z south) so
        // the land is always on the right of the direction of travel.
        Map<Corner, List<Corner>> edges = new HashMap<>();
        for (int[] c : claims) {
            int x = c[0];
            int z = c[1];
            if (!held.contains(pack(x, z - 1))) {
                edge(edges, x, z, x + 1, z);
            }
            if (!held.contains(pack(x + 1, z))) {
                edge(edges, x + 1, z, x + 1, z + 1);
            }
            if (!held.contains(pack(x, z + 1))) {
                edge(edges, x + 1, z + 1, x, z + 1);
            }
            if (!held.contains(pack(x - 1, z))) {
                edge(edges, x, z + 1, x, z);
            }
        }

        List<List<Corner>> outers = new ArrayList<>();
        List<List<Corner>> holes = new ArrayList<>();
        for (Corner seed : List.copyOf(edges.keySet())) {
            while (!edges.getOrDefault(seed, List.of()).isEmpty()) {
                List<Corner> ring = walk(edges, seed);
                if (ring.size() >= 3) {
                    (area(ring) > 0 ? outers : holes).add(ring);
                }
            }
        }

        List<Shape> out = new ArrayList<>();
        for (List<Corner> ring : outers) {
            List<List<Corner>> mine = new ArrayList<>();
            for (List<Corner> hole : holes) {
                if (inside(hole.get(0), ring)) {
                    mine.add(hole);
                }
            }
            out.add(new Shape(ring, mine));
        }
        return out;
    }

    /**
     * The chunk whose land this edge belongs to.
     *
     * <p>Every ring is emitted with the land on the <b>right</b> of travel, so the owning chunk is
     * the one a quarter turn that way from the direction a→b. Lives here rather than in the
     * renderer because it is a fact about the winding this class chose, and because a client class
     * cannot be reached by the self-test — the arithmetic is exactly the sort that is silently
     * wrong by one and looks fine until two factions swap colours along a shared border.</p>
     *
     * @return {@code {chunkX, chunkZ}}
     */
    public static int[] landSideOf(Corner a, Corner b) {
        int dx = Integer.signum(b.x() - a.x());
        int dz = Integer.signum(b.z() - a.z());
        int inx = -dz;      // a quarter turn towards the land
        int inz = dx;
        // Corners name grid lines; a chunk is named by its lower corner, so step back on any axis
        // we are travelling or leaning negatively along.
        return new int[] {a.x() + Math.min(0, dx) + Math.min(0, inx),
                          a.z() + Math.min(0, dz) + Math.min(0, inz)};
    }

    private static void edge(Map<Corner, List<Corner>> edges, int x0, int z0, int x1, int z1) {
        edges.computeIfAbsent(new Corner(x0, z0), k -> new ArrayList<>()).add(new Corner(x1, z1));
    }

    /**
     * Follow edges from a corner until the ring closes.
     *
     * <p>⚠ Where two chunks of the same faction meet only <b>diagonally</b>, four edges meet at one
     * corner and there is a genuine choice of which to take. Taking the sharpest <b>right</b> turn
     * keeps the two lobes as separate rings that touch at a point; the other choice crosses them
     * and draws a bow-tie. Both cover the same ground; only one looks like a border.</p>
     */
    private static List<Corner> walk(Map<Corner, List<Corner>> edges, Corner from) {
        List<Corner> ring = new ArrayList<>();
        Corner at = from;
        int dx = 0;
        int dz = 0;
        while (true) {
            List<Corner> next = edges.get(at);
            if (next == null || next.isEmpty()) {
                break;      // an open chain cannot happen for a closed region — but never spin
            }
            Corner step = next.size() == 1 ? next.remove(0) : sharpestRight(next, at, dx, dz);
            dx = step.x() - at.x();
            dz = step.z() - at.z();
            ring.add(at);
            at = step;
            if (at.equals(from)) {
                break;
            }
            if (ring.size() > 1_000_000) {
                break;      // belt and braces: a malformed edge set must not hang the server
            }
        }
        return simplify(ring);
    }

    private static Corner sharpestRight(List<Corner> options, Corner at, int dx, int dz) {
        Corner best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Corner option : options) {
            int ox = option.x() - at.x();
            int oz = option.z() - at.z();
            // cross > 0 is a right turn on screen; straight sits between right and left.
            int cross = dx * oz - dz * ox;
            int score = cross > 0 ? 2 : cross == 0 ? 1 : 0;
            if (score > bestScore) {
                bestScore = score;
                best = option;
            }
        }
        options.remove(best);
        return best;
    }

    /** Drop the middle of any three collinear corners: a straight edge needs two points, not ten. */
    private static List<Corner> simplify(List<Corner> ring) {
        if (ring.size() < 3) {
            return ring;
        }
        List<Corner> out = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) {
            Corner prev = ring.get((i - 1 + ring.size()) % ring.size());
            Corner here = ring.get(i);
            Corner next = ring.get((i + 1) % ring.size());
            long cross = (long) (here.x() - prev.x()) * (next.z() - here.z())
                    - (long) (here.z() - prev.z()) * (next.x() - here.x());
            if (cross != 0) {
                out.add(here);
            }
        }
        return out.size() >= 3 ? out : ring;
    }

    /** Twice the signed area. Positive for an outer ring under our winding, negative for a hole. */
    static long area(List<Corner> ring) {
        long twice = 0;
        for (int i = 0; i < ring.size(); i++) {
            Corner a = ring.get(i);
            Corner b = ring.get((i + 1) % ring.size());
            twice += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return twice;
    }

    /** Ray cast, to decide which outer ring a hole belongs to. */
    private static boolean inside(Corner point, List<Corner> ring) {
        boolean in = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            Corner a = ring.get(i);
            Corner b = ring.get(j);
            if ((a.z() > point.z()) != (b.z() > point.z())
                    && point.x() < (double) (b.x() - a.x()) * (point.z() - a.z())
                            / (b.z() - a.z()) + a.x()) {
                in = !in;
            }
        }
        return in;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}

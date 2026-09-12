package com.sablednah.factions.client;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.factions.ClaimOutline;
import com.sablednah.factions.ClaimsNearbyPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Faction borders drawn as geometry, for a client that can.
 *
 * <h2>⚠ The same answer, a better surface — and nothing more</h2>
 *
 * <p>This shows exactly what the particle border shows: which chunks are claimed, and what each
 * owner is to you. A vanilla client is not missing a fact, it is looking at a dotted line rather
 * than a wall and a tinted floor. That is the rule the whole client half of this pair runs on, and
 * it is the reason this is allowed to exist alongside decision 2 of Standards' {@code CLAUDE.md}.
 * The moment this can say something {@code /f map} cannot, the rule is broken.</p>
 *
 * <h2>Why a debug renderer rather than a render-stage event</h2>
 *
 * <p>Because {@code Gizmos} — vanilla's own line and rect primitives, the ones F3+G is drawn with —
 * throw unless a {@code GizmoCollector} is active on the thread, and vanilla installs one only
 * around debug rendering. NeoForge's {@code RegisterDebugRenderersEvent} puts us inside that
 * context. A mod-registered renderer is added <b>unconditionally</b> and its {@code emitGizmos}
 * runs every frame with no F3 gate, so the decision about whether to draw is entirely ours.</p>
 *
 * <p>The alternative was hand-rolled vertex buffers against a render pipeline that has changed
 * shape in every recent version. This is the one surface in either mod that renders in the world,
 * so it is also the most version-fragile thing here — borrowing vanilla's own primitives is what
 * keeps it small enough to port.</p>
 */
public final class ClaimBorderRenderer implements DebugRenderer.SimpleDebugRenderer {

    /** How far up the wall stands from the ground. A full-height curtain is unreadable. */
    private static final double WALL_HEIGHT = 5.0D;

    /** And a little below, so it does not float over a dip in the terrain. */
    private static final double WALL_SINK = 1.5D;

    /**
     * Alphas, and they are the whole difference between "solid visible area" and "did that draw?".
     *
     * <p>⚠ The floor started at 60 and was invisible — a 24% green wash over grass is nothing, and
     * it read as the quad not rendering at all rather than as a faint one. Found by looking, which
     * is the only way this kind of thing is ever found.</p>
     */
    private static final int WALL_LINE_ALPHA = 220;
    private static final int WALL_FILL_ALPHA = 55;

    /**
     * Inside this many blocks a panel is drawn as edges only.
     *
     * <p>⚠ Translucent panels COMPOUND. One at a distance is a hint; standing in your own claim you
     * are inside a box of them and looking through two or three at once, and the view goes green.
     * It looks fine from outside and oppressive from within, which is why it survived until
     * somebody stood in it. Close up the outline says everything the fill would, so the fill is
     * simply dropped — no fading, nothing to tune.</p>
     */
    private static final double FILL_CULL = 6.0D;
    private static final int FLOOR_ALPHA = 110;

    private static final float WALL_WIDTH = 3.0F;

    /**
     * How far inside its own land each wall stands, in blocks.
     *
     * <p>⚠ <b>Two factions meeting draw two lines, and they must not be the same line.</b> Drawn
     * exactly on the boundary they occupy identical geometry and z-fight — one line flickering
     * between green and red, which reads as a rendering fault rather than as a shared border. Held
     * a fifth of a block inside each owner's own chunks, they are two parallel lines you can name
     * at a glance: yours is the one on your side.</p>
     *
     * <p>It is the same trade the JourneyMap layer makes with its one-pixel inset, and the cost is
     * the same: the wall is no longer exactly on the block line. At this distance that is far
     * cheaper than the alternative, and the particles — which still sit on the corner lattice —
     * are what to trust for the exact edge.</p>
     */
    private static final double WALL_INSET = 0.2D;

    /**
     * How often the wall re-asks the ground how high it is, in blocks.
     *
     * <p>⚠ <b>One drawn edge is not one chunk.</b> {@code ClaimOutline.simplify} collapses a
     * straight run of chunks into two corners — that is the right thing for a map polygon and a
     * trap here, because a panel anchored only at its two ends is a straight ramp over everything
     * between them. At a radius of one it was never longer than a chunk and the assumption held;
     * at eight, a tidy rectangular territory produces a single 270-block edge that sets off across
     * a valley and ends up in the sky. Which is exactly how it was reported: the wider radius did
     * not cause the bug, it made an old one reachable.</p>
     *
     * <p>Four blocks, judged by looking at it over real terraced hillsides rather than derived:
     * sixteen would already have fixed the sky-ramp, and four is what makes the wall read as
     * following the ground rather than approximating it. It is the dial to raise if the walls ever
     * cost frames — the cost is linear in it and nothing else depends on the value.</p>
     */
    private static final double WALL_STEP = 4.0D;

    /**
     * How far down to look for something you could actually stand on.
     *
     * <p>⚠ <b>The heightmap's idea of solid is not the player's.</b> {@code MOTION_BLOCKING} counts
     * anything with a collision box, so a banner — two blocks of it — puts the surface two blocks
     * up, and the tinted ground floats in the air over it. That matters here more than it would
     * anywhere else: a standard <i>is</i> a banner, so the one place a faction border most wants to
     * look right is the one place this is guaranteed to be wrong. Fences, walls, panes, chains and
     * open trapdoors are the same bug wearing different hats.</p>
     */
    private static final int GROUND_PROBE = 8;

    /** Every relation that gets an outline, in the order they are drawn. */
    private static final byte[] RELATIONS = {
        ClaimsNearbyPayload.OWN, ClaimsNearbyPayload.ALLY,
        ClaimsNearbyPayload.ENEMY, ClaimsNearbyPayload.OTHER,
    };

    private final Minecraft minecraft;

    public ClaimBorderRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access,
            Frustum frustum, float partial) {
        ClaimsNearbyPayload claims = ClaimGrid.current();
        if (claims == null || minecraft.level == null) {
            return;
        }
        List<int[]> held = claims.claimedChunks();
        if (held.isEmpty()) {
            return;
        }
        walls(claims, held, minecraft.level);
        floor(claims, minecraft.level);
    }

    /**
     * The outline, as upright panels on the boundary.
     *
     * <p>⚠ The edges come from {@link ClaimOutline}, the same tracer the JourneyMap overlay uses,
     * so an edge <b>between two chunks of the same faction</b> is never drawn. That is the whole
     * difference between this and F3+G: vanilla draws the chunk grid, and a territory wants its
     * border. It also means the logic is unit-tested rather than re-derived here.</p>
     *
     * <p>⚠ <b>Traced once per relation, not once for everything claimed.</b> Tracing the union of
     * every claim in range draws the outside of the <i>whole</i> settled area and nothing within
     * it — so two factions whose land touches got no line between them at all, which is the one
     * border a player most needs to see. Reported from a real world, and invisible until somebody
     * stood where two territories met. Four traces, one per relation, and each is bounded by the
     * same radius the union was.</p>
     *
     * <p>Worth stating plainly, because it is the rule this class lives or dies by: the
     * <b>particles were already right</b> — they draw a side wherever ownership changes, including
     * between two claims — so this was the modded surface showing <i>less</i> than the vanilla one,
     * which is the one direction it is never allowed to differ in.</p>
     *
     * <p>Grouping by relation rather than by faction is deliberate and is the limit of what the
     * payload can do: it carries what an owner is to you, never who they are. Two <i>different</i>
     * enemies whose land touches still merge — and they would have been drawn in the same red
     * anyway, so the line between them would have said nothing.</p>
     */
    private void walls(ClaimsNearbyPayload claims, List<int[]> held, ClientLevel level) {
        for (byte rel : RELATIONS) {
            List<int[]> group = new ArrayList<>();
            for (int[] chunk : held) {
                if (claims.at(chunk[0], chunk[1]) == rel) {
                    group.add(chunk);
                }
            }
            if (group.isEmpty()) {
                continue;
            }
            int colour = colourOfRelation(rel);
            for (ClaimOutline.Shape shape : ClaimOutline.trace(group)) {
                ring(shape.outer(), colour, level);
                for (List<ClaimOutline.Corner> hole : shape.holes()) {
                    ring(hole, colour, level);
                }
            }
        }
    }

    private void ring(List<ClaimOutline.Corner> ring, int colour, ClientLevel level) {
        int line = argb(colour, WALL_LINE_ALPHA);
        GizmoStyle fill = GizmoStyle.fill(argb(colour, WALL_FILL_ALPHA));
        int n = ring.size();

        // ⚠ Every corner is MITRED, not extended along its edges. Insetting each edge on its own
        // and pushing the ends out by the same amount closes a corner that turns one way and opens
        // it wider on a corner that turns the other — half the corners in any territory, reported
        // as "the internal ones are not quite joining up". Offsetting the shared corner by BOTH
        // edges' inward steps is the meeting point of the two inset lines exactly, for a right
        // angle, and every corner here is a right angle: simplify() has already dropped every
        // vertex that was not a turn.
        double[] vx = new double[n];
        double[] vz = new double[n];
        for (int i = 0; i < n; i++) {
            ClaimOutline.Corner here = ring.get(i);
            int[] from = ClaimOutline.inwardOf(ring.get((i + n - 1) % n), here);
            int[] to = ClaimOutline.inwardOf(here, ring.get((i + 1) % n));
            vx[i] = here.x() * 16.0D + (from[0] + to[0]) * WALL_INSET;
            vz[i] = here.z() * 16.0D + (from[1] + to[1]) * WALL_INSET;
        }

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            wall(level, vx[i], vz[i], vx[j], vz[j], line, fill);
        }
    }

    /**
     * One stretch of wall, walked in {@link #WALL_STEP} steps so it follows the ground.
     *
     * <p>⚠ Anchored to the GROUND, never to the player. It used to take the player's own Y, which
     * is invisible while you walk — and then you fly up and the walls come with you, standing in
     * the clouds over a claim they no longer touch. Found by going up and looking down, which is
     * the only view that shows it.</p>
     */
    private void wall(ClientLevel level, double ax, double az, double bx, double bz,
            int line, GizmoStyle fill) {
        double span = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        int steps = Math.max(1, (int) Math.ceil(span / WALL_STEP));

        double px = ax;
        double pz = az;
        double py = groundAt(level, px, pz);

        // The upright, once per corner rather than once per step: a rung every four blocks reads
        // as a fence, and the corner is the only place the eye wants a vertical.
        Gizmos.line(new Vec3(px, py - WALL_SINK, pz), new Vec3(px, py + WALL_HEIGHT, pz),
                line, WALL_WIDTH);

        for (int s = 1; s <= steps; s++) {
            double t = (double) s / steps;
            double qx = ax + (bx - ax) * t;
            double qz = az + (bz - az) * t;
            double qy = groundAt(level, qx, qz);

            if (!nearCamera(px, pz, qx, qz)) {
                Gizmos.rect(new Vec3(px, py - WALL_SINK, pz), new Vec3(px, py + WALL_HEIGHT, pz),
                        new Vec3(qx, qy + WALL_HEIGHT, qz), new Vec3(qx, qy - WALL_SINK, qz), fill);
            }
            Gizmos.line(new Vec3(px, py - WALL_SINK, pz), new Vec3(qx, qy - WALL_SINK, qz),
                    line, WALL_WIDTH);
            Gizmos.line(new Vec3(px, py + WALL_HEIGHT, pz), new Vec3(qx, qy + WALL_HEIGHT, qz),
                    line, WALL_WIDTH);

            px = qx;
            pz = qz;
            py = qy;
        }
    }

    /** Whether either end of this edge is close enough that a filled panel would swamp the view. */
    private boolean nearCamera(double ax, double az, double bx, double bz) {
        if (minecraft.player == null) {
            return false;
        }
        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();
        double da = Math.min(Math.abs(px - ax), Math.abs(pz - az));
        double db = Math.min(Math.abs(px - bx), Math.abs(pz - bz));
        return Math.min(da, db) < FILL_CULL;
    }

    /** The surface at a corner, so the wall stands on the land rather than on the player. */
    private static double groundAt(ClientLevel level, double x, double z) {
        return surfaceAt(level, (int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * The first thing in this column you could actually stand on top of.
     *
     * <p>Starts at the heightmap and walks down past anything whose top face is not sturdy — see
     * {@link #GROUND_PROBE} for why the heightmap alone is not the answer. Usually the very first
     * test passes, so on ordinary ground this is one block lookup.</p>
     *
     * <p>Water stops the descent rather than being walked through: the surface of a lake is where
     * a border wants to be marked, and following the bed down would put the line under the boat.
     * </p>
     */
    private static int surfaceAt(ClientLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int drop = 0; drop < GROUND_PROBE; drop++) {
            pos.set(x, y - 1, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.isFaceSturdy(level, pos, Direction.UP)) {
                break;
            }
            y--;
        }
        return y;
    }

    /**
     * A wash over the ground itself, so the claim is marked where you are standing rather than only
     * at its edge.
     *
     * <p>⚠ <b>Deliberately a smaller radius than the walls, and the client's own number.</b> This
     * is one quad per column — 256 per chunk — and it is emitted every frame, so its cost is real
     * in a way the walls' is not. The wall answers "where does it end"; the floor answers "am I
     * standing in it", and that second question is only ever about here. Both radii live in the
     * client's config because only the machine drawing them knows what it can afford.</p>
     *
     * <p>The height is {@link #surfaceAt}, not the raw heightmap — the wash has to sit on the
     * ground rather than on top of whatever is standing on it.</p>
     */
    private void floor(ClaimsNearbyPayload claims, ClientLevel level) {
        if (minecraft.player == null) {
            return;
        }
        int reach = FactionsClientConfig.FLOOR_RADIUS.get();
        if (reach < 0) {
            return;
        }
        int pcx = minecraft.player.blockPosition().getX() >> 4;
        int pcz = minecraft.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - reach; cx <= pcx + reach; cx++) {
            for (int cz = pcz - reach; cz <= pcz + reach; cz++) {
                byte rel = claims.at(cx, cz);
                if (rel == ClaimsNearbyPayload.WILDERNESS) {
                    continue;
                }
                int colour = colourOfRelation(rel);
                GizmoStyle style = GizmoStyle.fill(argb(colour, FLOOR_ALPHA));
                for (int x = cx * 16; x < cx * 16 + 16; x++) {
                    for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        int y = surfaceAt(level, x, z);
                        // A hair above the block, or it z-fights with the surface it is marking.
                        double top = y + 0.02D;
                        Gizmos.rect(new Vec3(x, top, z), new Vec3(x + 1.0D, top, z + 1.0D),
                                Direction.UP, style);
                    }
                }
            }
        }
    }

    private static int argb(int rgb, int alpha) {
        return ARGB.color(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** The same four colours the particles use, so the two surfaces cannot drift apart. */
    private static int colourOfRelation(byte rel) {
        return switch (rel) {
            case ClaimsNearbyPayload.OWN -> 0x55FF55;
            case ClaimsNearbyPayload.ALLY -> 0x55FFFF;
            case ClaimsNearbyPayload.ENEMY -> 0xFF5555;
            default -> 0xFFFFFF;
        };
    }
}

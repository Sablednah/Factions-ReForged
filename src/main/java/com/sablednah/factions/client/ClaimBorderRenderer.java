package com.sablednah.factions.client;

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

    /** How far up the wall stands. A full-height curtain is unmissable and unreadable. */
    private static final double WALL_HEIGHT = 6.0D;

    /**
     * Alphas, and they are the whole difference between "solid visible area" and "did that draw?".
     *
     * <p>⚠ The floor started at 60 and was invisible — a 24% green wash over grass is nothing, and
     * it read as the quad not rendering at all rather than as a faint one. Found by looking, which
     * is the only way this kind of thing is ever found.</p>
     */
    private static final int WALL_LINE_ALPHA = 220;
    private static final int WALL_FILL_ALPHA = 70;
    private static final int FLOOR_ALPHA = 110;

    private static final float WALL_WIDTH = 3.0F;

    /** Chunks each way that get a tinted floor. Cheaper than the wall radius, and close in. */
    private static final int FLOOR_RADIUS = 1;

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
        double base = minecraft.player == null ? camY : minecraft.player.getY();
        walls(claims, held, base);
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
     * <p>One ring at a time, so a faction's colour is decided once per ring by the chunk inside it
     * rather than per edge — two factions sharing a border draw two panels, one each, and neither
     * has to know about the other.</p>
     */
    private void walls(ClaimsNearbyPayload claims, List<int[]> held, double baseY) {
        double top = baseY + WALL_HEIGHT;
        double bottom = baseY - 2.0D;
        for (ClaimOutline.Shape shape : ClaimOutline.trace(held)) {
            ring(claims, shape.outer(), bottom, top);
            for (List<ClaimOutline.Corner> hole : shape.holes()) {
                ring(claims, hole, bottom, top);
            }
        }
    }

    private void ring(ClaimsNearbyPayload claims, List<ClaimOutline.Corner> ring,
            double bottom, double top) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            ClaimOutline.Corner a = ring.get(i);
            ClaimOutline.Corner b = ring.get((i + 1) % n);
            // The land is on the right of travel — ClaimOutline guarantees it — so the chunk that
            // owns this edge is the one just to the right of its midpoint.
            int colour = colourOf(claims, ClaimOutline.landSideOf(a, b));
            double ax = a.x() * 16.0D;
            double az = a.z() * 16.0D;
            double bx = b.x() * 16.0D;
            double bz = b.z() * 16.0D;
            int line = argb(colour, WALL_LINE_ALPHA);
            // A translucent PANEL, not just an outline: the ask was a solid visible area rather
            // than a line of particles, and an outline at this scale reads as scaffolding. The
            // four corners are given explicitly — the cuboid-face overload cannot express a
            // vertical quad on an arbitrary bearing, only one of the six axis-aligned faces.
            Gizmos.rect(new Vec3(ax, bottom, az), new Vec3(ax, top, az),
                    new Vec3(bx, top, bz), new Vec3(bx, bottom, bz),
                    GizmoStyle.fill(argb(colour, WALL_FILL_ALPHA)));
            // ...and keep the edges crisp, or the panel has no silhouette against the sky.
            Gizmos.line(new Vec3(ax, bottom, az), new Vec3(bx, bottom, bz), line, WALL_WIDTH);
            Gizmos.line(new Vec3(ax, top, az), new Vec3(bx, top, bz), line, WALL_WIDTH);
            Gizmos.line(new Vec3(ax, bottom, az), new Vec3(ax, top, az), line, WALL_WIDTH);
        }
    }

    /**
     * A wash over the ground itself, so the claim is marked where you are standing rather than only
     * at its edge.
     *
     * <p>⚠ <b>Deliberately a small radius.</b> This is one quad per column — 256 per chunk — and it
     * is emitted every frame, so the cost is the honest reason it covers the chunk you are in and
     * its neighbours rather than the whole border radius. The wall answers "where does it end"; the
     * floor answers "am I standing in it", and that question is only ever about here.</p>
     *
     * <p>The height comes from the client's own heightmap, so the wash follows hills and sits on
     * whatever the top block actually is.</p>
     */
    private void floor(ClaimsNearbyPayload claims, ClientLevel level) {
        if (minecraft.player == null) {
            return;
        }
        int pcx = minecraft.player.blockPosition().getX() >> 4;
        int pcz = minecraft.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - FLOOR_RADIUS; cx <= pcx + FLOOR_RADIUS; cx++) {
            for (int cz = pcz - FLOOR_RADIUS; cz <= pcz + FLOOR_RADIUS; cz++) {
                byte rel = claims.at(cx, cz);
                if (rel == ClaimsNearbyPayload.WILDERNESS) {
                    continue;
                }
                int colour = colourOfRelation(rel);
                GizmoStyle style = GizmoStyle.fill(argb(colour, FLOOR_ALPHA));
                for (int x = cx * 16; x < cx * 16 + 16; x++) {
                    for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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

    private static int colourOf(ClaimsNearbyPayload claims, int[] chunk) {
        return colourOfRelation(claims.at(chunk[0], chunk[1]));
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

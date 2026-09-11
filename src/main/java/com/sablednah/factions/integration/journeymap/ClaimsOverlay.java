package com.sablednah.factions.integration.journeymap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.overlay.OverlayPoints;
import journeymap.api.v2.server.overlay.OverlayPolygon;
import journeymap.api.v2.server.overlay.OverlayShapeProps;
import journeymap.api.v2.server.overlay.ServerPolygon;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.sablednah.factions.ClaimOutline;
import com.sablednah.factions.FactionBridge;
import com.sablednah.factions.FactionStore;
import com.sablednah.factions.Factions;

/**
 * Every faction's territory, drawn on the map.
 *
 * <h2>⚠ Why every polygon is built per viewer</h2>
 *
 * <p>The border colour is the <b>relation</b> — white for your own land, green for an ally, red for
 * an enemy, grey for everybody else — and a relation is not a property of a faction, it is a
 * property of a <em>pair</em>. Ashfell's border is red to the faction it declared on and grey to
 * everyone else, at the same moment, on the same chunk. So there is no such thing as "the overlay
 * for Ashfell"; there is only "Ashfell as seen by this player", and the whole set is rebuilt per
 * viewer.</p>
 *
 * <p>The <b>fill</b> is the opposite: it is the faction's own banner colour, the same for everybody,
 * because that is its identity rather than its politics. Border says what it is to you, fill says
 * who it is.</p>
 *
 * <h2>Rows, not chunks</h2>
 *
 * <p>A faction can hold hundreds of chunks and a polygon per chunk would be hundreds of shapes for
 * one shape's worth of information. Horizontal runs are merged into single rectangles first —
 * cheap, and it collapses the common case (a block of contiguous land) by an order of magnitude.
 * Vertical merging is deliberately not attempted: it costs a second pass to save shapes nobody is
 * counting, and the borders it removes are ones the player wants to see anyway.</p>
 */
final class ClaimsOverlay {

    /** Drawn under waypoints and above the terrain; JourneyMap sorts ascending. */
    private static final int DISPLAY_ORDER = 900;

    /** Enough to see the shape of a border without hiding the ground under it. */
    private static final float FILL_OPACITY = 0.30f;
    private static final float STROKE_OPACITY = 0.85f;
    private static final float STROKE_WIDTH = 2.0f;

    private static final int OWN = 0xFFFFFF;
    private static final int ALLY = 0x55FF55;
    private static final int ENEMY = 0xFF5555;
    private static final int OTHER = 0x9A9A9A;

    private final IServerAPI api;

    /**
     * Who has turned the layer off.
     *
     * <p>⚠ <b>Held on the SERVER, because that is where the overlays are pushed from.</b> The
     * client's switch was written first and was purely cosmetic: it flipped its own label, the
     * server went on sending polygons, and the territory stayed exactly where it was. A button that
     * lies is worse than no button — found by pressing it, which is the only way it could have been
     * found, since both halves were individually correct.</p>
     *
     * <p>Not persisted here on purpose. The client owns the preference (JourneyMap stores its own
     * option) and re-states it when the map opens, so this is a cache of what the viewer last said
     * rather than a second copy of the truth — two stores of one preference is how they come to
     * disagree.</p>
     */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    ClaimsOverlay(IServerAPI api) {
        this.api = api;
    }

    /** Turn the layer on or off for one viewer, taking down what they are already being shown. */
    void setVisible(ServerPlayer viewer, boolean on) {
        if (on) {
            hidden.remove(viewer.getUUID());
            showFor(viewer);
        } else {
            hidden.add(viewer.getUUID());
            api.getOverlayApi().clearAll(viewer, Factions.MODID);
        }
    }

    /** Forget a preference on the way out; a UUID kept here would outlive the session it came from. */
    void forget(ServerPlayer viewer) {
        hidden.remove(viewer.getUUID());
    }

    /**
     * Rebuild and push every faction's territory as this player sees it.
     *
     * <p>The whole set rather than a delta, and on purpose: {@code show} replaces by id, so
     * re-sending a faction's polygon is how it is updated, and re-sending all of them is how one
     * that vanished stops being drawn. A delta would need us to remember what each player was last
     * sent, which is state that can disagree with the map.</p>
     */
    void showFor(ServerPlayer viewer) {
        if (hidden.contains(viewer.getUUID())) {
            return;
        }
        FactionStore store = FactionStore.get(viewer.level().getServer());
        String dimension = FactionBridge.dimensionOf(viewer.level());
        String mine = store.of(viewer.getUUID()).map(FactionStore.Faction::id).orElse(null);

        List<ServerPolygon> polygons = new ArrayList<>();
        for (FactionStore.Faction faction : store.all()) {
            List<ChunkPos> claims = store.claimsOf(faction.id(), dimension);
            if (claims.isEmpty()) {
                continue;
            }
            polygons.add(polygonFor(store, faction, claims, dimension,
                    viewer.level().dimension(), mine));
        }
        if (polygons.isEmpty()) {
            // ⚠ NOT a return. "Nothing to draw" and "draw nothing" are the same instruction here:
            // show() replaces by id, so an empty push replaces nothing and the last faction to
            // release its last chunk would keep its territory drawn until the viewer relogged.
            api.getOverlayApi().clearAll(viewer, Factions.MODID);
            return;
        }
        api.getOverlayApi().show(viewer, Factions.MODID, polygons.toArray(new ServerPolygon[0]));
    }

    private ServerPolygon polygonFor(FactionStore store, FactionStore.Faction faction,
            List<ChunkPos> claims, String dimension, ResourceKey<Level> levelKey, String mine) {
        int fill = store.colourOf(faction.id()).getTextureDiffuseColor() & 0xFFFFFF;
        int stroke = strokeFor(store, faction.id(), mine);

        List<OverlayPolygon> shapes = outline(claims);

        OverlayShapeProps props = OverlayProps.everywhere(fill, FILL_OPACITY, stroke, STROKE_WIDTH,
                STROKE_OPACITY, DISPLAY_ORDER, UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX,
                null, tooltip(store, faction, claims.size(), mine));
        // The id is what makes a re-show an UPDATE rather than a second overlay stacked on the
        // first. Per faction and dimension, because that is the unit that changes.
        // ⚠ Two spellings of the same dimension, deliberately. The ID uses Factions' own string
        // because that is what the store keys claims by; the polygon needs Minecraft's ResourceKey
        // because that is what JourneyMap takes. Deriving one from the other would be a parse that
        // can fail, where the viewer's own level already answers both.
        return new ServerPolygon("factions_claims_" + faction.id() + "_" + dimension,
                levelKey, shapes, props);
    }

    /**
     * ⚠ Relation from the VIEWER's side, and asymmetry is the point.
     *
     * <p>{@code store.relation(a, b)} resolves a pair, and Factions stores declarations one way —
     * so a faction that has declared on you is your enemy whether or not you have answered. Asking
     * in the order (mine, theirs) is what makes an unanswered declaration show red on their land
     * rather than grey.</p>
     */
    private int strokeFor(FactionStore store, String factionId, String mine) {
        if (mine == null) {
            return OTHER;
        }
        if (mine.equals(factionId)) {
            return OWN;
        }
        return switch (store.relation(mine, factionId)) {
            case ALLY -> ALLY;
            case ENEMY -> ENEMY;
            case NEUTRAL -> OTHER;
        };
    }

    /** What hovering the territory says. Kept to what a player would ask standing at the border. */
    private String tooltip(FactionStore store, FactionStore.Faction faction, int chunks,
            String mine) {
        StringBuilder out = new StringBuilder(faction.name());
        if (!faction.tag().isEmpty()) {
            out.append(" [").append(faction.tag()).append(']');
        }
        if (faction.peaceful()) {
            out.append(" (peaceful)");
        }
        out.append(" · ").append(chunks).append(" chunk").append(chunks == 1 ? "" : "s");
        out.append(" · ").append(faction.members().size()).append(" member")
                .append(faction.members().size() == 1 ? "" : "s");
        out.append(" · power ").append(Math.round(store.powerOf(faction)));
        if (mine != null && !mine.equals(faction.id())) {
            out.append(" · ").append(store.relation(mine, faction.id()).key());
        }
        return out.toString();
    }

    /**
     * Turn the traced outline into JourneyMap's shapes.
     *
     * <p>The tracing itself is {@link com.sablednah.factions.ClaimOutline}, which imports nothing
     * but {@code java.util} so the self-test can drive it — this package is never loaded without
     * JourneyMap, so a test living here could never run. All that happens here is the scale from
     * chunk corners to block coordinates.</p>
     */
    private static List<OverlayPolygon> outline(List<ChunkPos> claims) {
        // The one place ChunkPos is read for the tracer — see ClaimOutline on why it takes int[].
        List<int[]> coords = new ArrayList<>(claims.size());
        for (ChunkPos c : claims) {
            coords.add(new int[] {c.x(), c.z()});
        }
        List<OverlayPolygon> out = new ArrayList<>();
        for (ClaimOutline.Shape shape : ClaimOutline.trace(coords)) {
            List<OverlayPoints> holes = new ArrayList<>();
            for (List<ClaimOutline.Corner> hole : shape.holes()) {
                holes.add(points(hole));
            }
            out.add(new OverlayPolygon(points(shape.outer()), holes.isEmpty() ? null : holes));
        }
        return out;
    }

    private static OverlayPoints points(List<ClaimOutline.Corner> ring) {
        // y is only what the overlay is anchored at; JourneyMap draws it flat. Sea level reads
        // sensibly on a surface map and costs nothing on any other.
        final int y = 64;
        List<Long> out = new ArrayList<>(ring.size());
        for (ClaimOutline.Corner corner : ring) {
            out.add(BlockPos.asLong(corner.x() * 16, y, corner.z() * 16));
        }
        return new OverlayPoints(out);
    }

    /** A rectangle in block coordinates, clockwise. {@code x1}/{@code z1} are exclusive edges. */
    private static OverlayPoints rect(int x0, int z0, int x1, int z1) {
        // y is only what the overlay is anchored at; JourneyMap draws it flat. Sea level reads
        // sensibly on a surface map and costs nothing on any other.
        final int y = 64;
        return new OverlayPoints(List.of(
                BlockPos.asLong(x0, y, z0),
                BlockPos.asLong(x1, y, z0),
                BlockPos.asLong(x1, y, z1),
                BlockPos.asLong(x0, y, z1)));
    }
}

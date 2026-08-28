package com.sablednah.factions;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A claims atlas, on an ordinary vanilla map item.
 *
 * <h2>The accident that makes this work</h2>
 *
 * <p>A map's scale controls how many blocks each pixel covers: {@code 1 << scale}. At the maximum
 * scale of 4 that is <b>sixteen blocks — exactly one chunk per pixel</b>. So a fully zoomed-out
 * vanilla map is already a 128×128 chunk grid with its pixels aligned to chunk boundaries, which
 * is precisely the shape a claims map wants and is not something anybody designed on purpose.</p>
 *
 * <p>The server owns the colour array and pushes it with {@code ClientboundMapItemDataPacket}, so
 * an <b>unmodded client renders this</b>. No client mod, no resource pack, no rendering code — the
 * thing you are holding is a real map item that happens to have been painted by us.</p>
 *
 * <h2>Why the map is locked</h2>
 *
 * <p>Vanilla rewrites a carried map's pixels from the terrain beneath it. Locking freezes them,
 * which is what a cartography table does when you copy a map. Without it the atlas would slowly
 * erase itself into an ordinary map as you walked around, which is a maddening bug to be told
 * about second-hand.</p>
 *
 * <h2>Reading it</h2>
 *
 * <p>Colour is <em>relation</em>, not identity — green yours, blue allied, red hostile, white
 * everybody else. Identity would need a legend and a good memory; relation is the question you are
 * actually asking when you look at a map before walking somewhere.</p>
 *
 * <p>And the four brightness levels vanilla gives each colour are spent on <b>edges</b>: a chunk
 * with a differently-owned neighbour is drawn bright, the interior dim. That turns a field of flat
 * colour into an outlined territory you can read the shape of at a glance.</p>
 */
public final class FactionMap {

    /** 1 << 4 = 16 blocks per pixel = one chunk. The whole reason this works. */
    private static final byte CHUNK_SCALE = 4;
    private static final int SIZE = 128;

    /**
     * The map scale that gives this many pixels per chunk.
     *
     * <p>A pixel covers {@code 1 << scale} blocks, so a chunk occupies {@code 16 >> scale} of
     * them — the zoom levels are 1, 2, 4, 8, 16 and nothing in between, which is why the config
     * is a power of two rather than a percentage. Zooming in costs coverage in exact step: at two
     * pixels per chunk the same 128-pixel map shows 64 chunks instead of 128.</p>
     */
    private static byte scaleFor(int pixelsPerChunk) {
        int scale = CHUNK_SCALE;
        for (int p = pixelsPerChunk; p > 1; p >>= 1) {
            scale--;
        }
        return (byte) Math.max(0, scale);
    }

    /**
     * Build the atlas as a locked map item, centred on the player.
     *
     * @param pixelsPerChunk 1 for the whole region, higher to zoom in. Rounded down to a power of
     *                       two, because a map pixel has no finer setting than that.
     * @return the item to hand over, or empty if the map data could not be created
     */
    public static Optional<ItemStack> create(ServerPlayer player, ServerLevel level,
            int pixelsPerChunk) {
        int ppc = Integer.highestOneBit(Math.max(1, Math.min(16, pixelsPerChunk)));
        // Built directly rather than through MapItem.create, which would allocate a map id we
        // then throw away when locking — one orphaned map file per atlas, on a server where
        // people ask for these often. createFresh does the centre-snapping arithmetic for us,
        // which is the only part worth borrowing.
        MapItemSavedData data = MapItemSavedData.createFresh(
                player.getX(), player.getZ(), scaleFor(ppc), false, false, level.dimension());

        // Paint before locking: locked() copies, and a locked map refuses to be drawn on.
        paint(data, level, ChunkPos.containing(player.blockPosition()), player, ppc);

        MapId id = level.getFreeMapId();
        level.setMapData(id, data.locked());
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        stack.set(DataComponents.CUSTOM_NAME,
                com.sablednah.standards.neoforge.Feedback.colored(
                        com.sablednah.standards.neoforge.Lang.get("msg.factions.map_title")));
        return Optional.of(stack);
    }

    /**
     * Paint the grid, coloured by what each chunk means to this player.
     *
     * <p>At one pixel per chunk "edge" and "chunk with a differing neighbour" are the same thing.
     * Zoomed in they are not, and painting a whole chunk bright would thicken the outline with
     * the zoom until a small territory is solid highlight. So the test is per <em>pixel</em>: a
     * pixel is bright when it lies on the side of its chunk that faces a different owner, which
     * keeps the outline one pixel wide at every zoom level.</p>
     */
    private static void paint(MapItemSavedData data, ServerLevel level,
            ChunkPos centre, ServerPlayer viewer, int pixelsPerChunk) {
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);
        Optional<FactionStore.Faction> mine = store.of(viewer.getUUID());

        int chunksAcross = SIZE / pixelsPerChunk;
        int originX = centre.x() - chunksAcross / 2;
        int originZ = centre.z() - chunksAcross / 2;

        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                int cx = originX + px / pixelsPerChunk;
                int cz = originZ + pz / pixelsPerChunk;
                Optional<String> owner = store.ownerOf(dim, cx, cz);

                MapColor colour;
                MapColor.Brightness brightness;
                if (owner.isEmpty()) {
                    colour = MapColor.COLOR_BLACK;
                    brightness = MapColor.Brightness.LOWEST;
                } else {
                    colour = colourFor(store, mine, owner.get());
                    int ox = px % pixelsPerChunk;
                    int oz = pz % pixelsPerChunk;
                    int last = pixelsPerChunk - 1;
                    boolean edge =
                            (ox == 0 && !owner.equals(store.ownerOf(dim, cx - 1, cz)))
                            || (ox == last && !owner.equals(store.ownerOf(dim, cx + 1, cz)))
                            || (oz == 0 && !owner.equals(store.ownerOf(dim, cx, cz - 1)))
                            || (oz == last && !owner.equals(store.ownerOf(dim, cx, cz + 1)));
                    brightness = edge ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                }
                data.setColor(px, pz, colour.getPackedId(brightness));
            }
        }
    }

    private static MapColor colourFor(FactionStore store,
            Optional<FactionStore.Faction> mine, String ownerId) {
        if (mine.isEmpty()) {
            return MapColor.SNOW;
        }
        if (mine.get().id().equals(ownerId)) {
            return MapColor.COLOR_GREEN;
        }
        return switch (store.relation(mine.get().id(), ownerId)) {
            case ALLY -> MapColor.COLOR_LIGHT_BLUE;
            case ENEMY -> MapColor.COLOR_RED;
            case NEUTRAL -> MapColor.SNOW;
        };
    }

    /**
     * The same picture, in chat, for when you have no hands free.
     *
     * <p>The classic Factions text map, and still the fastest way to answer "whose is this" — it
     * needs no item, no inventory slot and no walking. Small on purpose: a 9×9 grid of chunks
     * around you, which is about as much as reads clearly in a chat window.</p>
     */
    public static List<String> ascii(ServerPlayer player, ServerLevel level, int radius) {
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);
        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        ChunkPos centre = ChunkPos.containing(player.blockPosition());

        List<String> rows = new java.util.ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            StringBuilder row = new StringBuilder();
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centre.x() + dx;
                int cz = centre.z() + dz;
                boolean you = dx == 0 && dz == 0;
                Optional<String> owner = store.ownerOf(dim, cx, cz);
                if (you) {
                    // Vanilla has no "you are here" colour, so the marker is the shape.
                    row.append("&e+");
                    continue;
                }
                if (owner.isEmpty()) {
                    row.append("&8-");
                    continue;
                }
                row.append(chatColourFor(store, mine, owner.get())).append('#');
            }
            rows.add(row.toString());
        }
        return rows;
    }

    private static String chatColourFor(FactionStore store,
            Optional<FactionStore.Faction> mine, String ownerId) {
        if (mine.isEmpty()) {
            return "&f";
        }
        if (mine.get().id().equals(ownerId)) {
            return "&a";
        }
        return switch (store.relation(mine.get().id(), ownerId)) {
            case ALLY -> "&b";
            case ENEMY -> "&c";
            case NEUTRAL -> "&f";
        };
    }

    private FactionMap() {}
}

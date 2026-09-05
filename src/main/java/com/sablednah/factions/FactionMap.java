package com.sablednah.factions;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
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
        return create(player, level, pixelsPerChunk, false);
    }

    /**
     * @param terrain draw the real landscape under the claims instead of a flat field
     */
    public static Optional<ItemStack> create(ServerPlayer player, ServerLevel level,
            int pixelsPerChunk, boolean terrain) {
        int ppc = Integer.highestOneBit(Math.max(1, Math.min(16, pixelsPerChunk)));
        // Built directly rather than through MapItem.create, which would allocate a map id we
        // then throw away when locking — one orphaned map file per atlas, on a server where
        // people ask for these often. createFresh does the centre-snapping arithmetic for us,
        // which is the only part worth borrowing.
        MapItemSavedData data = MapItemSavedData.createFresh(
                player.getX(), player.getZ(), scaleFor(ppc), false, false, level.dimension());

        // Paint before locking: locked() copies, and a locked map refuses to be drawn on.
        if (terrain) {
            paintTerrain(data, level, player, ppc);
        } else {
            paint(data, level, ChunkPos.containing(player.blockPosition()), player, ppc);
        }

        MapId id = level.getFreeMapId();
        level.setMapData(id, data.locked());
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        stack.set(DataComponents.CUSTOM_NAME,
                com.sablednah.standards.neoforge.Feedback.colored(
                        com.sablednah.standards.neoforge.Lang.get(terrain
                                ? "msg.factions.map_title_terrain" : "msg.factions.map_title")));
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

    /**
     * How much of the claim colour is washed over the terrain, out of 100.
     *
     * <p>Enough to read the owner at a glance, little enough to still see the river you are trying
     * to put a wall on. Below about a third the tint disappears into dark forest; above about two
     * thirds the terrain stops being worth drawing and you may as well have the flat atlas.</p>
     */
    private static final int TINT = 45;

    /**
     * Paint the landscape, then wash the claims over it.
     *
     * <h3>Why this is a second mode rather than the map</h3>
     *
     * <p>The atlas is one chunk per pixel, so it spans 2048 blocks — <b>16,384 chunks</b>. Claims
     * come out of {@link FactionStore} and are known whether or not the world is loaded, which is
     * exactly why that map can be complete. Terrain cannot: drawing it there would mean loading or
     * <em>generating</em> sixteen thousand chunks, which is a server stall, a great deal of disk,
     * and a free map of country the player has never walked.</p>
     *
     * <p>So this mode zooms in until the area fits inside what is already loaded — at two blocks a
     * pixel the whole map is 256 blocks across, comfortably inside any view distance — and reads
     * only chunks that are <b>already in memory</b>. A chunk that is not loaded falls back to the
     * flat claim colour rather than being fetched, so the picture degrades at the edges instead of
     * costing anything.</p>
     *
     * <h3>There is no transparency in a map</h3>
     *
     * <p>A map pixel is not a colour, it is an index: 64 {@link MapColor} slots by four brightness
     * levels. So the "wash" is done in software — the terrain's RGB is mixed with the claim's, and
     * the result snapped to the nearest thing the palette can actually say.</p>
     */
    private static void paintTerrain(MapItemSavedData data, ServerLevel level,
            ServerPlayer viewer, int pixelsPerChunk) {
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);
        Optional<FactionStore.Faction> mine = store.of(viewer.getUUID());

        int blocks = 1 << data.scale;
        // From the map's OWN centre, not the player's chunk. Vanilla positions the player arrow
        // from these, so terrain drawn against anything else would slide under the marker.
        int originX = data.centerX - (SIZE / 2) * blocks;
        int originZ = data.centerZ - (SIZE / 2) * blocks;

        int[] heights = new int[SIZE * SIZE];
        MapColor[] ground = new MapColor[SIZE * SIZE];
        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                sample(level, originX + px * blocks, originZ + pz * blocks,
                        heights, ground, px * SIZE + pz);
            }
        }

        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                int i = px * SIZE + pz;
                int wx = originX + px * blocks;
                int wz = originZ + pz * blocks;
                Optional<String> owner = store.ownerOf(dim, wx >> 4, wz >> 4);
                MapColor tint = owner.map(id -> colourFor(store, mine, id)).orElse(null);

                if (ground[i] == null) {
                    // Not loaded. Say what we know — who owns it — rather than inventing land.
                    data.setColor(px, pz, tint == null
                            ? MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST)
                            : tint.getPackedId(MapColor.Brightness.LOW));
                    continue;
                }

                // Vanilla's own relief shading: compare this column with the one north of it, so
                // slopes catch the light and flat ground does not. Without it the landscape reads
                // as flat colour blobs and the map is no easier to navigate by than the atlas.
                int north = pz > 0 ? heights[px * SIZE + (pz - 1)] : heights[i];
                double slope = (heights[i] - north) * 4.0D / (blocks + 4) + (((px + pz) & 1) - 0.5D) * 0.4D;
                MapColor.Brightness shade = slope > 0.6D ? MapColor.Brightness.HIGH
                        : slope < -0.6D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;

                int rgb = ground[i].calculateARGBColor(shade);
                if (tint != null) {
                    rgb = mix(rgb, tint.calculateARGBColor(MapColor.Brightness.HIGH), TINT);
                    // The border still wants to be a line. Brightening the whole edge pixel would
                    // fight the relief shading, so the edge is drawn as MORE tint rather than more
                    // light — which survives whatever the ground underneath is doing.
                    if (onBorder(store, dim, wx, wz, blocks, owner.get())) {
                        rgb = mix(rgb, tint.calculateARGBColor(MapColor.Brightness.HIGH), 55);
                    }
                }
                data.setColor(px, pz, nearest(rgb));
            }
        }
    }

    /** The top block at this column, if its chunk is already in memory. */
    private static void sample(ServerLevel level, int wx, int wz,
            int[] heights, MapColor[] ground, int i) {
        // getChunkNow, never getChunk: this must read what is loaded and never cause a load. A
        // 128x128 map that generated terrain would hand out country nobody has walked, and stall
        // the server doing it.
        net.minecraft.world.level.chunk.LevelChunk chunk =
                level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
        if (chunk == null) {
            return;
        }
        int y = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                wx & 15, wz & 15);
        BlockPos pos = new BlockPos(wx, y, wz);
        MapColor colour = chunk.getBlockState(pos).getMapColor(level, pos);
        // A column of air over the void answers NONE, which would paint a hole. Treat it as
        // unsampled and let the claim colour speak instead.
        if (colour == MapColor.NONE) {
            return;
        }
        heights[i] = y;
        ground[i] = colour;
    }

    /** Whether this pixel sits against a chunk owned by somebody else. */
    private static boolean onBorder(FactionStore store, String dim, int wx, int wz,
            int blocks, String owner) {
        int cx = wx >> 4;
        int cz = wz >> 4;
        boolean first = (wx & 15) < blocks;
        boolean last = (wx & 15) >= 16 - blocks;
        boolean top = (wz & 15) < blocks;
        boolean bottom = (wz & 15) >= 16 - blocks;
        return (first && !store.ownerOf(dim, cx - 1, cz).map(owner::equals).orElse(false))
                || (last && !store.ownerOf(dim, cx + 1, cz).map(owner::equals).orElse(false))
                || (top && !store.ownerOf(dim, cx, cz - 1).map(owner::equals).orElse(false))
                || (bottom && !store.ownerOf(dim, cx, cz + 1).map(owner::equals).orElse(false));
    }

    /** Mix two ARGB colours, {@code percent} of the second. */
    static int mix(int base, int over, int percent) {
        int r = (((base >> 16) & 0xFF) * (100 - percent) + ((over >> 16) & 0xFF) * percent) / 100;
        int g = (((base >> 8) & 0xFF) * (100 - percent) + ((over >> 8) & 0xFF) * percent) / 100;
        int b = ((base & 0xFF) * (100 - percent) + (over & 0xFF) * percent) / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * The closest thing the map palette can say to this colour.
     *
     * <p>Sixty-four colours by four brightnesses, so about two hundred and forty real entries —
     * enough that a wash over grass or water lands somewhere convincing. Plain squared distance in
     * RGB; a perceptual metric would be more correct and is not worth the arithmetic for a
     * 128-pixel image of a hillside.</p>
     */
    static byte nearest(int rgb) {
        int wantR = (rgb >> 16) & 0xFF;
        int wantG = (rgb >> 8) & 0xFF;
        int wantB = rgb & 0xFF;
        byte best = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOW);
        int bestDistance = Integer.MAX_VALUE;
        for (int id = 1; id < 64; id++) {
            MapColor candidate = MapColor.byId(id);
            if (candidate == MapColor.NONE) {
                continue;
            }
            for (MapColor.Brightness b : MapColor.Brightness.values()) {
                int c = candidate.calculateARGBColor(b);
                int dr = ((c >> 16) & 0xFF) - wantR;
                int dg = ((c >> 8) & 0xFF) - wantG;
                int db = (c & 0xFF) - wantB;
                int d = dr * dr + dg * dg + db * db;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate.getPackedId(b);
                }
            }
        }
        return best;
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

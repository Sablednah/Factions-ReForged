package com.sablednah.factions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Showing people where the line is.
 *
 * <h2>Only the edges that are edges</h2>
 *
 * <p>The obvious implementation outlines every chunk, which produces a grid — technically correct
 * and useless, because a grid tells you where chunks are and you already knew that. What a player
 * wants is the <em>shape of the territory</em>: the outside of a faction's land, drawn where
 * ownership actually changes.</p>
 *
 * <p>So each of a chunk's four sides is drawn only when the chunk beyond it has a different owner.
 * Interior boundaries between two chunks of the same faction vanish, and what is left is an
 * outline you can follow round a base.</p>
 *
 * <h2>Coloured by relation, not by faction</h2>
 *
 * <p>Green for your own, blue for an ally, red for an enemy, white for a stranger. Colouring by
 * faction identity would need a legend and a good memory; colouring by <em>what it means to you</em>
 * needs neither, and is the only question you are actually asking when you walk up to a border.</p>
 *
 * <h2>Two ways to see it</h2>
 *
 * <p>A toggle for when you are surveying, and a <b>held item</b> for when you are not. The held
 * item is the better of the two: you pick the tool up, see what you are doing, and put it down —
 * exactly how vanilla's own debug stick behaves, and it means nobody leaves the display on and
 * forgets why their screen is full of dust.</p>
 */
public final class FactionBorders {

    /** Players who have asked to see borders. Not persisted: a display, not a setting. */
    private static final Set<UUID> SHOWING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Where each player last stood, so entering territory can be announced once rather than every tick. */
    private static final Map<UUID, String> LAST_CHUNK = new HashMap<>();

    // Packed RGB — 1.21.11's DustParticleOptions takes an int rather than a vector. These are
    // vanilla's own chat colours, so a border matches the text that names the faction.
    private static final int OWN = 0x55FF55;
    private static final int ALLY = 0x55FFFF;
    private static final int ENEMY = 0xFF5555;
    private static final int OTHER = 0xFFFFFF;

    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (SHOWING.remove(id)) {
            return false;
        }
        SHOWING.add(id);
        return true;
    }

    public static boolean isShowing(ServerPlayer player) {
        return SHOWING.contains(player.getUUID());
    }

    public static void forget(UUID player) {
        SHOWING.remove(player);
        LAST_CHUNK.remove(player);
    }

    /** Whether this player should see borders right now — toggled on, or holding the tool. */
    private static boolean wants(ServerPlayer player) {
        if (SHOWING.contains(player.getUUID())) {
            return true;
        }
        String wanted = FactionsConfig.BORDER_ITEM.get();
        if (wanted == null || wanted.isBlank()) {
            return false;
        }
        Identifier id = Identifier.tryParse(wanted);
        if (id == null) {
            return false;
        }
        return heldIs(player, id);
    }

    private static boolean heldIs(ServerPlayer player, Identifier id) {
        return matches(player.getMainHandItem(), id) || matches(player.getOffhandItem(), id);
    }

    private static boolean matches(net.minecraft.world.item.ItemStack stack, Identifier id) {
        return !stack.isEmpty()
                && net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).equals(id);
    }

    /**
     * Called every server tick; does work only on the configured interval and only for players who
     * asked. On a server where nobody is surveying this costs one modulo and a set lookup.
     */
    public static void tick(MinecraftServer server) {
        int every = FactionsConfig.BORDER_PARTICLE_TICKS.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            announceTerritory(player);
        }
        if (every <= 0 || server.getTickCount() % every != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (wants(player)) {
                draw(player);
            }
        }
    }

    /**
     * Tell a player whose land they have just walked into, on the action bar.
     *
     * <p>The action bar rather than chat, for the same reason the teleport countdown lives there:
     * it is already the transient-status line, and crossing a border twenty times while building a
     * wall should not bury a conversation.</p>
     */
    private static void announceTerritory(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos here = new ChunkPos(player.blockPosition());
        String dim = FactionBridge.dimensionOf(level);
        String key = FactionStore.key(dim, here.x, here.z);
        String previous = LAST_CHUNK.put(player.getUUID(), key);
        if (key.equals(previous)) {
            return;
        }

        FactionStore store = FactionStore.get(player.level().getServer());
        Optional<FactionStore.Faction> now = store.factionAt(dim, here);
        // Only speak when the OWNER changed, not on every chunk boundary. Walking across a large
        // territory should say its name once, on the way in.
        if (previous != null) {
            String[] bits = previous.split("\\|");
            Optional<FactionStore.Faction> was = store.factionAt(bits[0],
                    new ChunkPos(Integer.parseInt(bits[1]), Integer.parseInt(bits[2])));
            if (was.map(FactionStore.Faction::id).equals(now.map(FactionStore.Faction::id))) {
                return;
            }
        }

        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        String message = now.isEmpty()
                ? com.sablednah.standards.neoforge.Lang.get("msg.factions.entered_wild")
                : com.sablednah.standards.neoforge.Lang.fmt("msg.factions.entered",
                        "colour", relationColour(store, mine, now),
                        "name", now.get().name());
        player.displayClientMessage(
                com.sablednah.standards.neoforge.Feedback.colored(message), true);
    }

    private static String relationColour(FactionStore store,
            Optional<FactionStore.Faction> mine, Optional<FactionStore.Faction> theirs) {
        if (theirs.isEmpty()) {
            return "&7";
        }
        if (mine.isEmpty()) {
            return "&f";
        }
        return switch (store.relation(mine.get().id(), theirs.get().id())) {
            case ALLY -> "&b";
            case ENEMY -> "&c";
            case NEUTRAL -> "&f";
        };
    }

    /** Draw the boundary lines around this player, for this player only. */
    private static void draw(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);
        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        int radius = FactionsConfig.BORDER_RADIUS_CHUNKS.get();
        ChunkPos centre = new ChunkPos(player.blockPosition());
        double y = player.getY() + 0.1D;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centre.x + dx;
                int cz = centre.z + dz;
                Optional<String> owner = store.ownerOf(dim, cx, cz);
                if (owner.isEmpty()) {
                    continue; // wilderness has no edge of its own; its neighbours draw theirs
                }
                int colour = colourFor(store, mine, owner.get());
                // North and west only, plus the far side when the neighbour differs — every
                // boundary belongs to exactly one chunk, so nothing is drawn twice.
                if (!owner.equals(store.ownerOf(dim, cx, cz - 1))) {
                    line(player, level, cx << 4, cz << 4, 1, 0, y, colour);
                }
                if (!owner.equals(store.ownerOf(dim, cx, cz + 1))) {
                    line(player, level, cx << 4, (cz << 4) + 16, 1, 0, y, colour);
                }
                if (!owner.equals(store.ownerOf(dim, cx - 1, cz))) {
                    line(player, level, cx << 4, cz << 4, 0, 1, y, colour);
                }
                if (!owner.equals(store.ownerOf(dim, cx + 1, cz))) {
                    line(player, level, (cx << 4) + 16, cz << 4, 0, 1, y, colour);
                }
            }
        }
    }

    private static int colourFor(FactionStore store,
            Optional<FactionStore.Faction> mine, String ownerId) {
        if (mine.isEmpty()) {
            return OTHER;
        }
        if (mine.get().id().equals(ownerId)) {
            return OWN;
        }
        return switch (store.relation(mine.get().id(), ownerId)) {
            case ALLY -> ALLY;
            case ENEMY -> ENEMY;
            case NEUTRAL -> OTHER;
        };
    }

    /**
     * Sixteen blocks of dust along one chunk edge, sent to one player.
     *
     * <h3>Dense along the floor, sparse up the wall</h3>
     *
     * <p>The two rows are doing different jobs. The floor row is the <em>line</em> — the thing you
     * are trying to stand on the right side of, read at a glance and at a shallow angle, where a
     * gap every other block is a dashed line rather than a border. The upper row only has to say
     * "this is a wall, not a stripe on the ground", and reads fine at half the density. Doubling
     * both would double the packets to buy nothing.</p>
     *
     * <h3>It stands on the ground, not on you</h3>
     *
     * <p>Drawn at a flat height, the wall is buried in the first hill it crosses and floating over
     * the first valley — and being underground is worst precisely when you are walking over the
     * border, which is the one moment the display exists for. So each column starts at the surface
     * beneath it.</p>
     *
     * <p>Clamped to a window around the player because a border running off a cliff would
     * otherwise spend its particles forty blocks below, out of sight and still costing packets.
     * Only sampled where the chunk is already loaded: a heightmap lookup on an absent chunk would
     * generate it, and drawing a decoration is not a reason to generate terrain.</p>
     */
    private static void line(ServerPlayer player, ServerLevel level,
            int startX, int startZ, int stepX, int stepZ, double y, int colour) {
        DustParticleOptions dust = new DustParticleOptions(colour, 1.0F);
        boolean follow = FactionsConfig.BORDER_FOLLOW_GROUND.get();
        for (int i = 0; i <= 16; i++) {
            double x = startX + stepX * i + 0.5D;
            double z = startZ + stepZ * i + 0.5D;
            double base = follow ? groundAt(level, x, z, y) : y;
            level.sendParticles(player, dust, true, false, x, base, z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            // The wall, at half the density — it only has to say "wall".
            if (i % 2 == 0) {
                level.sendParticles(player, dust, true, false, x, base + 2.0D, z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** How far above or below the player a border column may be drawn, in blocks. */
    private static final double GROUND_WINDOW = 24.0D;

    private static double groundAt(ServerLevel level, double x, double z, double fallback) {
        int bx = net.minecraft.util.Mth.floor(x);
        int bz = net.minecraft.util.Mth.floor(z);
        if (!level.hasChunkAt(new net.minecraft.core.BlockPos(bx, level.getMinY(), bz))) {
            return fallback;
        }
        double surface = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                bx, bz) + 0.1D;
        return net.minecraft.util.Mth.clamp(surface, fallback - GROUND_WINDOW,
                fallback + GROUND_WINDOW);
    }

    private FactionBorders() {}
}

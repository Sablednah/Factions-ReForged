package com.sablednah.factions.api;

import java.util.Optional;
import java.util.UUID;

import com.sablednah.factions.Factions;
import com.sablednah.factions.FactionStore;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The surface another mod may compile against to set factions up in code.
 *
 * <p>{@link FactionStore} is the storage, not a promise: its shape moves whenever a feature needs
 * it to. This is the small, stable part — built for a modpack's glue mod creating an NPC-run
 * faction and its land when it builds a structure, and so shaped around being <b>called again on
 * every start</b> without doing anything twice.</p>
 *
 * <p>Two rules it keeps that the store does not:</p>
 * <ul>
 *   <li><b>Idempotent.</b> {@link #ensureFaction} finds before it creates, and re-asserts the tag
 *       and peaceful flag, so a camp rebuilt after an admin edit comes back as it was built.</li>
 *   <li><b>Never takes a player's land.</b> {@link #claim} refuses a chunk somebody else holds,
 *       rather than silently moving it. A structure generated on top of a base is a conflict for
 *       a person to resolve, and a player who logs in to find their land reassigned by a mod
 *       they did not know was running would be right to call it a bug.</li>
 *   <li>{@link #unclaim} is the same rule turned round: it releases only land the named faction
 *       holds, so a pack cannot use it to clear a player's chunk either.</li>
 * </ul>
 *
 * <p>There is no colour: a faction has none of its own. Its colour is its planted standard's, and
 * white without one.</p>
 */
public final class FactionsApi {

    /**
     * The faction by this name, creating it if there is none.
     *
     * <p>Empty if the name belongs to a faction led by somebody else — a player got there first,
     * and it is not ours to take over — or if {@code leader} already leads a different faction.
     * Both are logged, since the caller is usually running at start-up with nobody watching.</p>
     *
     * @param tag      shown in chat and on the map; empty for none. Ignored if another faction
     *                 already holds it.
     * @param leader   for an NPC faction, a stable invented UUID — for instance
     *                 {@code UUID.nameUUIDFromBytes("OfflinePlayer:Okafor".getBytes(UTF_8))}
     * @param peaceful out of every war, raid and overclaim, both directions. What an NPC faction
     *                 almost certainly wants: see {@code FactionPower} for what else it spares.
     * @return the faction's id, which is what {@link #claim} takes
     */
    public static Optional<String> ensureFaction(MinecraftServer server, String name, String tag,
            UUID leader, boolean peaceful) {
        FactionStore store = FactionStore.get(server);
        Optional<FactionStore.Faction> existing = store.byName(name);
        FactionStore.Faction f;
        if (existing.isPresent()) {
            if (!existing.get().leader().equals(leader)) {
                Factions.LOGGER.warn("FactionsApi: a faction called '{}' already exists under "
                        + "another leader; leaving it alone", name);
                return Optional.empty();
            }
            f = existing.get();
        } else {
            Optional<FactionStore.Faction> made = store.create(name, leader);
            if (made.isEmpty()) {
                Factions.LOGGER.warn("FactionsApi: could not create '{}' — its leader {} is "
                        + "already in another faction", name, leader);
                return Optional.empty();
            }
            f = made.get();
        }
        if (tag != null && !tag.equals(f.tag()) && !store.setTag(f.id(), tag)) {
            Factions.LOGGER.warn("FactionsApi: tag '{}' for '{}' is taken; left as '{}'",
                    tag, name, f.tag());
        }
        if (f.peaceful() != peaceful) {
            store.setPeaceful(f.id(), peaceful);
        }
        return Optional.of(f.id());
    }

    /** A faction's id from its name or tag. */
    public static Optional<String> idOf(MinecraftServer server, String nameOrTag) {
        return FactionStore.get(server).lookup(nameOrTag).map(FactionStore.Faction::id);
    }

    /**
     * Give a chunk to a faction.
     *
     * <p>Bypasses every player-facing rule — cost, connectivity, the per-member limit — because
     * a structure's footprint is the pack author's decision. It does not bypass ownership.</p>
     *
     * @return true if the chunk is now the faction's, including when it already was; false if the
     *         faction does not exist or somebody else holds the chunk
     */
    public static boolean claim(MinecraftServer server, ResourceKey<Level> dimension, int chunkX,
            int chunkZ, String factionId) {
        FactionStore store = FactionStore.get(server);
        if (store.byId(factionId).isEmpty()) {
            return false;
        }
        String dim = dimension.identifier().toString();
        Optional<String> owner = store.ownerOf(dim, chunkX, chunkZ);
        if (owner.isPresent()) {
            if (owner.get().equals(factionId)) {
                return true;
            }
            Factions.LOGGER.warn("FactionsApi: chunk {},{} in {} belongs to another faction; "
                    + "not taking it", chunkX, chunkZ, dim);
            return false;
        }
        store.claim(dim, chunkX, chunkZ, factionId);
        return true;
    }

    /**
     * Take a chunk back from a faction — the other half of {@link #claim}, for a pack that opens
     * ground it built, such as a vault entrance handed over when a quest starts.
     *
     * <p>No refund and no message: the land was never bought. A captured flag standing in the
     * chunk comes down with it, as it does for every other route land changes hands by. The chunk
     * need not be loaded.</p>
     *
     * <p>⚠ It does <b>not</b> lift the chunk under the faction's own planted standard. That rule
     * already had one second door closed ({@code unclaimAll}), and an API that walked through it
     * would leave a flag standing in wilderness. A faction made by {@link #ensureFaction} plants
     * none unless somebody plants one for it.</p>
     *
     * @return true only if the chunk was this faction's and now is not; false if somebody else
     *         holds it, nobody does, the faction does not exist, or its own standard pins it.
     *         Calling it twice is safe: the second answers false.
     */
    public static boolean unclaim(MinecraftServer server, ResourceKey<Level> dimension, int chunkX,
            int chunkZ, String factionId) {
        FactionStore store = FactionStore.get(server);
        if (store.byId(factionId).isEmpty()) {
            return false;
        }
        String dim = dimension.identifier().toString();
        if (!store.ownerOf(dim, chunkX, chunkZ).map(factionId::equals).orElse(false)) {
            return false;
        }
        if (store.ownStandardInChunk(factionId, dim, chunkX, chunkZ)) {
            Factions.LOGGER.warn("FactionsApi: chunk {},{} in {} holds the faction's own standard; "
                    + "not releasing it", chunkX, chunkZ, dim);
            return false;
        }
        return store.unclaim(dim, chunkX, chunkZ);
    }

    // --- reading ---
    //
    // Cheap enough to poll once a second per player: every one of these is a map lookup or a
    // single pass over the claims, and none of them writes.

    /** The faction this player belongs to, by id. */
    public static Optional<String> factionOf(MinecraftServer server, UUID player) {
        return FactionStore.get(server).of(player).map(FactionStore.Faction::id);
    }

    /** Who holds this chunk, by id. Empty is wilderness. */
    public static Optional<String> ownerOf(MinecraftServer server, ResourceKey<Level> dimension,
            int chunkX, int chunkZ) {
        return FactionStore.get(server).ownerOf(dimension.identifier().toString(), chunkX, chunkZ);
    }

    /** A faction's display name. Empty if there is no such faction. */
    public static Optional<String> nameOf(MinecraftServer server, String factionId) {
        return FactionStore.get(server).byId(factionId).map(FactionStore.Faction::name);
    }

    /** Whether it has set a {@code /f home}. */
    public static boolean hasHome(MinecraftServer server, String factionId) {
        return FactionStore.get(server).byId(factionId).map(f -> f.home().isPresent())
                .orElse(false);
    }

    /**
     * Whether its <b>own</b> standard is planted, anywhere. A captured enemy flag does not count:
     * that is somebody else's standard it happens to be flying.
     */
    public static boolean hasStandard(MinecraftServer server, String factionId) {
        return FactionStore.get(server).hasStandard(factionId);
    }

    /** How many chunks it holds, across every dimension. */
    public static int claimCount(MinecraftServer server, String factionId) {
        return FactionStore.get(server).claimCount(factionId);
    }

    /**
     * Its allies, by id — <b>mutual only</b>. An offer nobody has returned is not an alliance, and
     * a quest that counted one would be rewarding a player for asking.
     */
    public static java.util.List<String> alliesOf(MinecraftServer server, String factionId) {
        FactionStore store = FactionStore.get(server);
        return store.byId(factionId)
                .map(f -> f.allies().stream()
                        .filter(other -> store.relation(factionId, other)
                                == FactionStore.Relation.ALLY)
                        .sorted()
                        .toList())
                .orElse(java.util.List.of());
    }

    // --- for maps ---
    //
    // What a map that draws every faction needs, built for SableCraft Diorama's web map. All of it
    // is what Factions' own JourneyMap layer already shows every player — territory, colour, name,
    // tag, peaceful — and none of it is a faction's home or where its standard stands, which would
    // hand a raider their target.

    /** Every faction, by id, sorted. */
    public static java.util.List<String> factions(MinecraftServer server) {
        return FactionStore.get(server).all().stream().map(FactionStore.Faction::id).sorted().toList();
    }

    /**
     * Every claim in one dimension, grouped by the faction holding it — in <b>one pass</b> over the
     * claims, so a map can afford to ask for all of them on every refresh. Factions holding nothing
     * here are absent.
     */
    public static java.util.Map<String, java.util.List<net.minecraft.world.level.ChunkPos>> claimsIn(
            MinecraftServer server, ResourceKey<Level> dimension) {
        return FactionStore.get(server).claimsByFaction(dimension.identifier().toString());
    }

    /** Its tag, or empty for none (and for no such faction). */
    public static String tagOf(MinecraftServer server, String factionId) {
        return FactionStore.get(server).byId(factionId).map(FactionStore.Faction::tag).orElse("");
    }

    /** Whether it is out of every war, raid and overclaim. */
    public static boolean isPeaceful(MinecraftServer server, String factionId) {
        return FactionStore.get(server).byId(factionId).map(FactionStore.Faction::peaceful).orElse(false);
    }

    /**
     * Its colour as {@code 0xRRGGBB}: its own planted standard's, white without one — the same
     * colour its land is filled with on JourneyMap. A captured flag never lends its colour.
     */
    public static int colourOf(MinecraftServer server, String factionId) {
        return FactionStore.get(server).colourOf(factionId).getTextureDiffuseColor() & 0xFFFFFF;
    }

    private FactionsApi() {}
}

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

    private FactionsApi() {}
}

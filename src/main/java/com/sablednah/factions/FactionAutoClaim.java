package com.sablednah.factions;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

/**
 * Claiming by walking.
 *
 * <p>Marking out a base one {@code /f claim} at a time is the tedious part of owning land: you
 * already know the shape you want, you are already walking it, and the command adds nothing but
 * typing. Turn this on and the walk <em>is</em> the claim.</p>
 *
 * <h2>It knows when to stop, and that is the whole feature</h2>
 *
 * <p>Autoclaim has a reputation, and it is deserved by every implementation that keeps trying.
 * Hitting the claim limit and then reporting it once per chunk for the rest of the afternoon is
 * how a convenience becomes a nuisance, so:</p>
 *
 * <ul>
 *   <li><b>Running out of land turns it off.</b> The limit is not a chunk you happened to be
 *       standing on, it is a state that will refuse the next chunk too. Anything else is a
 *       machine repeating itself.</li>
 *   <li><b>Walking through somebody else's territory says nothing.</b> That is not a failed claim,
 *       it is a journey. The action bar already names whose land it is.</li>
 *   <li><b>A reason is given once, not once per chunk.</b> Leaving your own territory with
 *       connected claims required will refuse every chunk out there for the same reason; you need
 *       to hear it when it changes, not while it stays true.</li>
 *   <li><b>It turns itself off when you log out</b> — like the border display, it is a thing you
 *       are doing, not a setting you have.</li>
 * </ul>
 */
public final class FactionAutoClaim {

    private static final Set<UUID> ON = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Where each claimer last stood, so a chunk is attempted once rather than every tick. */
    private static final Map<UUID, String> LAST_CHUNK = new java.util.concurrent.ConcurrentHashMap<>();

    /** The last thing we told them, so a standing reason is not repeated every sixteen blocks. */
    private static final Map<UUID, FactionClaims.Result> LAST_REASON =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (ON.remove(id)) {
            forgetTrail(id);
            return false;
        }
        ON.add(id);
        // Not seeded with the current chunk: turning it on while standing somewhere unclaimed
        // should take that chunk, which is what you meant by standing there.
        forgetTrail(id);
        return true;
    }

    public static boolean isOn(ServerPlayer player) {
        return ON.contains(player.getUUID());
    }

    public static void forget(UUID player) {
        ON.remove(player);
        forgetTrail(player);
    }

    private static void forgetTrail(UUID player) {
        LAST_CHUNK.remove(player);
        LAST_REASON.remove(player);
    }

    /** Called every tick. Costs an empty-set check on a server where nobody is claiming. */
    public static void tick(MinecraftServer server) {
        if (ON.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ON.contains(player.getUUID())) {
                step(player);
            }
        }
    }

    private static void step(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        String dim = FactionBridge.dimensionOf(level);
        ChunkPos here = new ChunkPos(player.blockPosition());
        String key = FactionStore.key(dim, here.x, here.z);
        if (key.equals(LAST_CHUNK.put(player.getUUID(), key))) {
            return;
        }

        FactionStore store = FactionStore.get(level.getServer());
        Optional<FactionStore.Faction> f = store.of(player.getUUID());
        // Re-checked every chunk rather than only at the toggle. Being demoted — or kicked —
        // while walking a border must stop the claiming, and it is not worth a second event to
        // hear about it.
        if (f.isEmpty() || !isOfficer(f.get(), player.getUUID())) {
            stop(player, Lang.get("msg.factions.autoclaim_lost_rank"));
            return;
        }

        FactionClaims.Result result = FactionClaims.attempt(store, dim, here, f.get());
        switch (result) {
            case CLAIMED -> {
                LAST_REASON.remove(player.getUUID());
                int limit = FactionClaims.limitFor(f.get());
                Feedback.chat(player, Lang.fmt("msg.factions.claimed",
                        "x", here.x, "z", here.z, "held", store.claimCount(f.get().id()),
                        "limit", limit < 0 ? Lang.get("msg.factions.no_limit")
                                : String.valueOf(limit)));
            }
            // A hard stop, not a chunk you happened to be on. Saying so once and switching off
            // beats saying so for the rest of the walk.
            case LIMIT -> stop(player, Lang.fmt("msg.factions.autoclaim_full",
                    "held", store.claimCount(f.get().id()),
                    "limit", String.valueOf(FactionClaims.limitFor(f.get()))));
            case DISCONNECTED -> once(player, result, Lang.get("msg.factions.must_connect"));
            // Walking across land — yours or anybody's — is not a failed claim.
            case ALREADY_YOURS, OWNED -> LAST_REASON.put(player.getUUID(), result);
        }
    }

    private static boolean isOfficer(FactionStore.Faction f, UUID player) {
        FactionStore.Rank rank = f.rankOf(player);
        return rank != null && rank.atLeast(FactionStore.Rank.OFFICER);
    }

    /** Say it only when it is news. */
    private static void once(ServerPlayer player, FactionClaims.Result result, String message) {
        if (LAST_REASON.put(player.getUUID(), result) != result) {
            Feedback.chat(player, message);
        }
    }

    private static void stop(ServerPlayer player, String why) {
        forget(player.getUUID());
        Feedback.chat(player, why);
        Feedback.chat(player, Lang.get("msg.factions.autoclaim_off"));
    }

    private FactionAutoClaim() {}
}

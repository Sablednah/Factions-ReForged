package com.sablednah.factions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import com.sablednah.standards.neoforge.StandardsData;

/**
 * Neighbours to have relations with.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Two people cannot test a relation system. Allied, hostile, offered-but-not-returned, and
 * peaceful are four different states, plus neutral, and each needs a counterparty that is not you.
 * Reaching them for real means five accounts sitting still while somebody declares war on them in
 * turn, which is not a test plan — so the states that only appear on a busy server were the states
 * never being looked at.</p>
 *
 * <p>These are not mocks. They are ordinary factions, with ordinary claims, held by ordinary
 * offline players — the UUIDs are derived exactly as an offline-mode server derives them, from
 * {@code OfflinePlayer:<name>}, so somebody could log in as one and find their faction waiting.
 * Every command treats them as real because they <em>are</em> real; the only thing invented is
 * that nobody has ever played them.</p>
 *
 * <h2>Off unless asked for</h2>
 *
 * <p>Behind a config flag that unregisters the command rather than refusing it, the same way
 * every optional command in Standards works. An op on a live server who tab-completes their way
 * into inventing eight factions has been failed by us, not by their fingers.</p>
 */
public final class FactionFixtures {

    /**
     * The neighbours, and how each stands towards whoever runs the command.
     *
     * <p>Deliberately covering every branch: a mutual alliance reached from both sides, an offer
     * in each direction so the two halves of the status split can be told apart, a war they
     * started, a war you would have to start, and two who cannot be fought at all.</p>
     */
    private enum Stance {
        /** Allied with you, both having declared. */
        ALLY,
        /** They have offered; you have not answered. Your status should say "offered you". */
        OFFERS_YOU,
        /** You have offered; they have not answered. Their side of the same coin. */
        AWAITS_YOU,
        /** They declared on you, one-sided. */
        HOSTILE,
        /** Nothing declared either way. */
        NEUTRAL,
        /** Peaceful — cannot be declared upon, cannot declare. */
        PEACEFUL
    }

    private record Seed(String faction, String tag, String leader, Stance stance) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("Ashfell", "ASH", "Corvin", Stance.HOSTILE),
            new Seed("Marrowgate", "MAR", "Delya", Stance.HOSTILE),
            new Seed("Thornhold", "THN", "Bracken", Stance.ALLY),
            new Seed("Saltmere", "SLT", "Iva", Stance.OFFERS_YOU),
            new Seed("Greyhollow", "GRY", "Ottoline", Stance.OFFERS_YOU),
            new Seed("Quillrest", "QLL", "Fenner", Stance.AWAITS_YOU),
            new Seed("Deepmarch", "DPM", "Rook", Stance.NEUTRAL),
            new Seed("Lantern Vale", "LTV", "Sepha", Stance.PEACEFUL),
            new Seed("Stillwater", "STW", "Mabry", Stance.PEACEFUL));

    /** Exactly how an offline-mode server derives a UUID from a name. */
    private static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Build them around the player.
     *
     * @param chunksEach how much land each neighbour takes
     * @return a line per faction, describing what it became
     */
    public static List<String> seed(ServerPlayer player, int chunksEach) {
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        FactionStore store = FactionStore.get(server);
        StandardsData names = StandardsData.get(server);
        String dim = FactionBridge.dimensionOf(level);
        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        ChunkPos centre = ChunkPos.containing(player.blockPosition());

        List<String> report = new ArrayList<>();
        int ring = 3;
        for (int i = 0; i < SEEDS.size(); i++) {
            Seed seed = SEEDS.get(i);
            if (store.lookup(seed.faction()).isPresent()) {
                report.add(seed.faction() + ": already here");
                continue;
            }
            UUID leader = offlineId(seed.leader());
            // Named in Standards' cache, or every /f who row would read as eight hex characters
            // and the fixtures would be less useful than no fixtures.
            names.rememberName(leader, seed.leader());

            Optional<FactionStore.Faction> made = store.create(seed.faction(), leader);
            if (made.isEmpty()) {
                report.add(seed.faction() + ": refused");
                continue;
            }
            String id = made.get().id();
            store.setTag(id, seed.tag());

            // Spread around the player at a distance, each faction in its own direction, so the
            // borders are walkable and the map has something with a shape on it.
            double angle = 2 * Math.PI * i / SEEDS.size();
            int baseX = centre.x() + (int) Math.round(Math.cos(angle) * ring);
            int baseZ = centre.z() + (int) Math.round(Math.sin(angle) * ring);
            int took = 0;
            for (int n = 0; n < chunksEach * 3 && took < chunksEach; n++) {
                int cx = baseX + (n % 3);
                int cz = baseZ + (n / 3);
                // Never over somebody who already holds it — least of all the tester.
                if (store.ownerOf(dim, cx, cz).isPresent()) {
                    continue;
                }
                store.claim(dim, cx, cz, id);
                took++;
            }

            String stance = mine.map(m -> apply(store, id, m.id(), seed.stance()))
                    .orElse("no faction of yours to relate to");
            report.add(seed.faction() + " [" + seed.tag() + "] — " + took + " chunks, " + stance);
        }
        return report;
    }

    /** Put the declared relation in place, from whichever side owns it. */
    private static String apply(FactionStore store, String theirs, String yours, Stance stance) {
        switch (stance) {
            case ALLY -> {
                store.declare(theirs, yours, FactionStore.Relation.ALLY);
                store.declare(yours, theirs, FactionStore.Relation.ALLY);
                return "allied";
            }
            case OFFERS_YOU -> {
                store.declare(theirs, yours, FactionStore.Relation.ALLY);
                return "has offered you an alliance";
            }
            case AWAITS_YOU -> {
                store.declare(yours, theirs, FactionStore.Relation.ALLY);
                return "waiting on your offer";
            }
            case HOSTILE -> {
                store.declare(theirs, yours, FactionStore.Relation.ENEMY);
                return "has declared on you";
            }
            case PEACEFUL -> {
                store.setPeaceful(theirs, true);
                return "peaceful";
            }
            case NEUTRAL -> {
                return "neutral";
            }
        }
        return "";
    }

    /** Take them all away again, land and all. */
    public static int clear(MinecraftServer server) {
        FactionStore store = FactionStore.get(server);
        int gone = 0;
        for (Seed seed : SEEDS) {
            Optional<FactionStore.Faction> f = store.lookup(seed.faction());
            // Matched on the leader as well as the name, so a real faction that happens to be
            // called Ashfell is not swept away by somebody tidying up.
            if (f.isPresent() && f.get().leader().equals(offlineId(seed.leader()))) {
                FactionInvites.forgetFaction(f.get().id());
                FactionRequests.forgetFaction(f.get().id());
                store.disband(f.get().id());
                gone++;
            }
        }
        return gone;
    }

    private FactionFixtures() {}
}

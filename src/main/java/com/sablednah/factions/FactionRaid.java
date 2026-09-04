package com.sablednah.factions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A declared attack: announced, time-boxed, and visible to the whole server while it runs.
 *
 * <p>Being at war is a standing relation. A <b>raid</b> is an event — it starts, everybody is told,
 * everyone involved lights up by side, and it ends in a way somebody won. The design is settled in
 * {@code POWER.md} §5; the reasoning for each rule lives there and only its consequences are
 * repeated here.</p>
 *
 * <h2>It is not persisted, deliberately</h2>
 *
 * <p>Held in memory and gone on stop, like {@link FactionBypass} and Standards' teleport warmups. A
 * raid is a fight between people who are present; one that survived a restart could expire while
 * nobody was online to defend, which is a defeat nobody was there for. The same reasoning ends a
 * raid when every attacker logs off.</p>
 *
 * <h2>Three ways out, and only one of them is the clock</h2>
 *
 * <ul>
 * <li><b>The standard is taken</b> — the attackers win. The flag already existed and already had a
 *     carrier glow; making it the objective is what turns a raid from a period of time into
 *     something with a point.</li>
 * <li><b>Every attacker is dead or gone</b> — the defenders win. "We repelled them" has to be a
 *     real outcome or defending is just waiting.</li>
 * <li><b>The timer expires</b> — the defenders held. A backstop, not the mechanism.</li>
 * </ul>
 */
public final class FactionRaid {

    /**
     * One raid in flight.
     *
     * @param attackerId the faction that declared it
     * @param defenderId the faction being raided
     * @param endsAtMillis when the clock runs out, if nothing settles it first
     * @param defenderHadStandard whether they were flying one when this was declared. Recorded at
     *        the start because the objective is that it <em>falls</em>, and a faction that never
     *        had one would otherwise satisfy "their standard is gone" the instant the raid began
     */
    public record Raid(String attackerId, String defenderId, long startedAtMillis,
            long endsAtMillis, boolean defenderHadStandard) {

        public boolean expired(long now) {
            return now >= endsAtMillis;
        }

        public long secondsLeft(long now) {
            return Math.max(0L, (endsAtMillis - now) / 1000L);
        }

        /** Whether this faction is on either side. */
        public boolean involves(String factionId) {
            return attackerId.equals(factionId) || defenderId.equals(factionId);
        }
    }

    /** How a raid finished, so the announcement can say who won and why. */
    public enum Outcome {
        /**
         * The attackers planted the defenders' standard on their own land. The classic capture the
         * flag: taking it is the hard part, carrying it home is the dangerous part, and only the
         * second one ends the raid.
         */
        STANDARD_PLANTED,
        /**
         * The attackers took land from a faction that flies no standard.
         *
         * <p>The answer to "what can winning mean against somebody with no flag to take" — asked
         * the first time a raid was declared on a flagless faction and found to be unwinnable by
         * any sequence of moves at all. Taking their ground is the only thing left that costs
         * them something, so it is the win.</p>
         *
         * <p>It deliberately does <b>not</b> apply once they fly one: there the land is a bonus and
         * the flag is the objective, so a raid that has already taken a chunk keeps running and
         * the attackers can go for the standard as well.</p>
         */
        LAND_TAKEN,
        /** Every attacker died or logged off. */
        ATTACKERS_GONE,
        /** The clock ran out with the defenders still holding. */
        HELD
    }

    /**
     * Attackers who have planted their target's standard at home.
     *
     * <p>A latch rather than a live query, because the win is the <em>act</em> of planting: an
     * enemy who sprints in and takes the trophy back thirty seconds later does not un-win the raid
     * that already ended.</p>
     */
    private static final java.util.Set<String> PLANTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Record that {@code attackerId} planted a standard captured from {@code victimId}.
     *
     * <p>Called from the planting path rather than polled, and it checks the pairing itself so the
     * caller does not have to know whether a raid is running. Planting a trophy taken from
     * somebody you are <em>not</em> currently raiding is just decorating your base.</p>
     */
    public static void plantedStandard(String attackerId, String victimId) {
        if (attacking(attackerId).map(r -> r.defenderId().equals(victimId)).orElse(false)) {
            PLANTED.add(attackerId);
        }
    }

    /** Whether this attacker has planted their target's standard. */
    public static boolean hasPlanted(String attackerId) {
        return PLANTED.contains(attackerId);
    }

    /** Live raids, keyed by the attacking faction — one faction raids one target at a time. */
    private static final Map<String, Raid> ACTIVE = new LinkedHashMap<>();

    /**
     * {@code "attacker|defender"} to when their last raid ended.
     *
     * <p>Per <b>pair</b> rather than per faction. Stopping a faction raiding anybody for six hours
     * punishes a busy server; stopping them raiding the same victim again is precisely the "declare
     * a raid every ten minutes forever" grief the cooldown exists for.</p>
     */
    private static final Map<String, Long> COOLDOWNS = new LinkedHashMap<>();

    private static String pair(String attacker, String defender) {
        return attacker + "|" + defender;
    }

    // --- reading ---

    /** Every raid in flight. */
    public static List<Raid> active() {
        synchronized (ACTIVE) {
            return List.copyOf(ACTIVE.values());
        }
    }

    /** The raid this faction is currently attacking in, if any. */
    public static Optional<Raid> attacking(String factionId) {
        synchronized (ACTIVE) {
            return Optional.ofNullable(ACTIVE.get(factionId));
        }
    }

    /** The raid this faction is currently defending, if any. */
    public static Optional<Raid> defending(String factionId) {
        synchronized (ACTIVE) {
            return ACTIVE.values().stream()
                    .filter(r -> r.defenderId().equals(factionId))
                    .findFirst();
        }
    }

    /** Whether these two are currently in a raid together, either way round. */
    public static boolean between(String a, String b) {
        synchronized (ACTIVE) {
            return ACTIVE.values().stream().anyMatch(r ->
                    (r.attackerId().equals(a) && r.defenderId().equals(b))
                            || (r.attackerId().equals(b) && r.defenderId().equals(a)));
        }
    }

    /** Whether this faction is on either side of any live raid. */
    public static boolean involved(String factionId) {
        synchronized (ACTIVE) {
            return ACTIVE.values().stream().anyMatch(r -> r.involves(factionId));
        }
    }

    /** Seconds left on the cooldown for this pair, or 0 if they may raid now. */
    public static long cooldownLeft(String attacker, String defender, long now) {
        Long endedAt;
        synchronized (COOLDOWNS) {
            endedAt = COOLDOWNS.get(pair(attacker, defender));
        }
        if (endedAt == null) {
            return 0L;
        }
        long wait = FactionsConfig.RAID_COOLDOWN_MINUTES.get() * 60L * 1000L;
        return Math.max(0L, (endedAt + wait - now) / 1000L);
    }

    // --- writing ---

    /** Begin one. The caller has already checked it is allowed; this only records it. */
    public static Raid begin(String attackerId, String defenderId, long now,
            boolean defenderHadStandard) {
        long length = FactionsConfig.RAID_MINUTES.get() * 60L * 1000L;
        Raid raid = new Raid(attackerId, defenderId, now, now + length, defenderHadStandard);
        synchronized (ACTIVE) {
            ACTIVE.put(attackerId, raid);
        }
        return raid;
    }

    /**
     * End one, and start the cooldown.
     *
     * <p>The cooldown runs from the <em>end</em> rather than the start, so a long raid does not
     * consume the quiet time that is supposed to follow it.</p>
     */
    public static void end(Raid raid, long now) {
        synchronized (ACTIVE) {
            ACTIVE.remove(raid.attackerId(), raid);
        }
        SAW_STANDARD.remove(raid.attackerId());
        PLANTED.remove(raid.attackerId());
        synchronized (CLAIMED_IN_RAID) {
            CLAIMED_IN_RAID.remove(raid.attackerId());
        }
        synchronized (COOLDOWNS) {
            COOLDOWNS.put(pair(raid.attackerId(), raid.defenderId()), now);
        }
    }

    /**
     * Attacker ids whose target has been seen flying a standard at some point during the raid.
     *
     * <p><b>Watched continuously rather than recorded once.</b> The first version noted whether the
     * defenders were flying one at declaration, which stops a flagless faction losing the instant
     * it is raided — and also meant a flag <em>planted during</em> the raid could be taken with no
     * effect at all. That happened the first time somebody tried it: the defenders replanted, the
     * attacker took it, and the raid still expired as "held".</p>
     *
     * <p>So the objective is "their standard was up, and now it is not", asked every tick. A
     * faction that never plants one still cannot lose that way; one that plants a flag mid-raid has
     * given the attackers something to take, which is exactly right.</p>
     */
    private static final java.util.Set<String> SAW_STANDARD =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Note that this raid's defenders are flying a standard right now. */
    public static void sawStandard(String attackerId) {
        SAW_STANDARD.add(attackerId);
    }

    /** Whether this raid has ever seen the defenders flying one. */
    public static boolean hasSeenStandard(String attackerId) {
        return SAW_STANDARD.contains(attackerId);
    }

    /**
     * Chunks taken during each live raid, keyed by attacker.
     *
     * <p>Reset when the raid ends, so the allowance is per raid rather than per faction. With
     * {@code raidClaimLimit} at its default of 1 this is the anti-bullying rule: a large faction
     * cannot strip a small one in a single sitting, and every further chunk costs another raid and
     * another cooldown.</p>
     */
    private static final Map<String, Integer> CLAIMED_IN_RAID = new LinkedHashMap<>();

    /** How many chunks this raid has taken so far. */
    public static int claimsTaken(String attackerId) {
        synchronized (CLAIMED_IN_RAID) {
            return CLAIMED_IN_RAID.getOrDefault(attackerId, 0);
        }
    }

    /** Whether this raid may take another. {@code raidClaimLimit} of 0 means no limit. */
    public static boolean mayTakeLand(String attackerId) {
        int limit = FactionsConfig.RAID_CLAIM_LIMIT.get();
        return limit <= 0 || claimsTaken(attackerId) < limit;
    }

    /** Count one. Called only when a chunk has actually changed hands. */
    public static void tookLand(String attackerId) {
        synchronized (CLAIMED_IN_RAID) {
            CLAIMED_IN_RAID.merge(attackerId, 1, Integer::sum);
        }
    }

    /** Everything gone — for the self-test, and for a server stopping. */
    public static void clear() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
        synchronized (COOLDOWNS) {
            COOLDOWNS.clear();
        }
        SAW_STANDARD.clear();
        PLANTED.clear();
        synchronized (CLAIMED_IN_RAID) {
            CLAIMED_IN_RAID.clear();
        }
    }

    // --- the online-members question, which several rules turn on ---

    /**
     * The members of a faction who are online right now.
     *
     * <p>Used by the declare check ("are there defenders to fight?") and by the end check ("are
     * there attackers left?"), which are the same question asked of the two sides.</p>
     */
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, String factionId) {
        FactionStore store = FactionStore.get(server);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            store.of(player.getUUID())
                    .filter(f -> f.id().equals(factionId))
                    .ifPresent(f -> out.add(player));
        }
        return out;
    }

    /** Whether a UUID is on the attacking side of a live raid. For the glow. */
    public static Optional<Raid> raidFor(MinecraftServer server, ServerPlayer player) {
        Optional<FactionStore.Faction> mine = FactionStore.get(server).of(player.getUUID());
        if (mine.isEmpty()) {
            return Optional.empty();
        }
        String id = mine.get().id();
        synchronized (ACTIVE) {
            // The one ending SOONEST, not the first one the map happens to hold.
            //
            // A faction can be in two raids at once — attacking one target while somebody else
            // attacks them — and there is one action bar and one glow colour between them. Taking
            // whichever came out of the map first meant the other raid's clock simply never
            // appeared, with nothing to say a second raid existed at all.
            //
            // Soonest-ending is the useful answer rather than merely a deterministic one: the raid
            // about to resolve is the one worth being told about, and as each settles the next
            // takes the bar.
            return ACTIVE.values().stream().filter(r -> r.involves(id))
                    .min(java.util.Comparator.comparingLong(Raid::endsAtMillis));
        }
    }

    private FactionRaid() {}
}

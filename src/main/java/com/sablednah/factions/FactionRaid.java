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
     */
    public record Raid(String attackerId, String defenderId, long startedAtMillis,
            long endsAtMillis) {

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
        /** The standard fell. The attackers took it. */
        STANDARD_TAKEN,
        /** Every attacker died or logged off. */
        ATTACKERS_GONE,
        /** The clock ran out with the defenders still holding. */
        HELD
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
    public static Raid begin(String attackerId, String defenderId, long now) {
        long length = FactionsConfig.RAID_MINUTES.get() * 60L * 1000L;
        Raid raid = new Raid(attackerId, defenderId, now, now + length);
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
        synchronized (COOLDOWNS) {
            COOLDOWNS.put(pair(raid.attackerId(), raid.defenderId()), now);
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
            return ACTIVE.values().stream().filter(r -> r.involves(id)).findFirst();
        }
    }

    private FactionRaid() {}
}

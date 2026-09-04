package com.sablednah.factions;

import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

/**
 * Runs the raids: ends them when they are settled, and lights up who is in one.
 *
 * <p>Driven from {@link FactionPowerEvents#tick}, on the same one-second beat as the carrier glow,
 * because the two do the same kind of work and one schedule is easier to keep honest than two.</p>
 */
public final class FactionRaidEvents {

    /** Attackers, and defenders. Two teams because vanilla colours a glow only by team. */
    private static final String ATTACK_TEAM = "factions_raid_attack";
    private static final String DEFEND_TEAM = "factions_raid_defend";

    private static final java.util.Set<java.util.UUID> MARKED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Settle anything that has finished, then repaint.
     *
     * <p>Checked in the order the outcomes matter. A win beats the clock: a raid whose flag was
     * planted on the last tick was won, not held.</p>
     */
    public static void tick(MinecraftServer server) {
        if (!FactionsConfig.ENABLE_RAIDS.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        FactionStore store = FactionStore.get(server);

        for (FactionRaid.Raid raid : FactionRaid.active()) {
            // A faction that disbanded mid-raid takes the raid with it — there is nobody left for
            // either outcome to mean anything to.
            if (store.byId(raid.attackerId()).isEmpty() || store.byId(raid.defenderId()).isEmpty()) {
                FactionRaid.end(raid, now);
                continue;
            }
            if (FactionRaid.hasPlanted(raid.attackerId())) {
                finish(server, store, raid, FactionRaid.Outcome.STANDARD_PLANTED, now);
            } else if (landIsTheWin(store, raid)) {
                finish(server, store, raid, FactionRaid.Outcome.LAND_TAKEN, now);
            } else if (FactionRaid.onlineMembers(server, raid.attackerId()).isEmpty()) {
                finish(server, store, raid, FactionRaid.Outcome.ATTACKERS_GONE, now);
            } else if (raid.expired(now)) {
                finish(server, store, raid, FactionRaid.Outcome.HELD, now);
            }
        }
        countdown(server, now);
        glow(server);
    }

    /**
     * Whether taking ground is this raid's win condition, and has happened.
     *
     * <p>Only against a faction that flies <b>no standard</b>, and has flown none at any point
     * during the raid. That case needed an answer of its own: raiding a flagless faction was
     * literally unwinnable — no sequence of taking their land, planting a flag for them, or
     * handing one back could complete it — which is how it was found. Their ground is the only
     * thing left that taking costs them, so taking it is the win.</p>
     *
     * <p>Once they do fly one the rule switches off, and deliberately: there the flag is the
     * objective and a chunk is a bonus, so a raid that has already taken land keeps running and
     * the attackers can go after the standard as well. That is the shape the owner asked for —
     * "if they have a standard then let the raid continue so they can take that too".</p>
     *
     * <p>Watched every tick rather than sampled at declaration, for the same reason the standard
     * check is: the first version snapshotted at the start, so a flag planted mid-raid could be
     * taken with no effect at all.</p>
     */
    private static boolean landIsTheWin(FactionStore store, FactionRaid.Raid raid) {
        if (store.hasStandard(raid.defenderId())) {
            // Up right now. Remember it, so this raid can never revert to being about land — and
            // so taking it later still counts even though they planted it after the raid began.
            FactionRaid.sawStandard(raid.attackerId());
            return false;
        }
        if (raid.defenderHadStandard() || FactionRaid.hasSeenStandard(raid.attackerId())) {
            return false;
        }
        return FactionRaid.claimsTaken(raid.attackerId()) > 0;
    }

    /**
     * Factions whose raid has just ended, and until when to say so.
     *
     * <p>Needed because the raid is gone from {@code ACTIVE} the instant it settles, so there is
     * nothing left to read a final line off. Three seconds, then the bar goes quiet on its own.</p>
     */
    private static final java.util.Map<String, Long> OVER_UNTIL =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The clock, on the action bar, for everybody in the fight.
     *
     * <p><b>The action bar rather than chat</b>, for the same reason the teleport warmup uses it —
     * it is already the transient-status line, and a raid that posted a countdown to chat would
     * bury the fight under two hundred identical messages. Standards' decision 8, applied to the
     * other thing on this server with a clock on it.</p>
     *
     * <p>Both sides see it. A defender knowing how long they have to hold is exactly as useful as
     * an attacker knowing how long they have to win, and a raid whose end surprises one side is
     * the same failure as a teleport that lands with nobody told.</p>
     *
     * <p>It tightens as it runs — minutes, then seconds, then a bare urgent number under ten —
     * because a countdown that reads the same at four minutes and at four seconds has told you
     * nothing about which one you are in.</p>
     *
     * <p>⚠ It shares the action bar with Standards' teleport warmup. Running <code>/f home</code>
     * mid-raid will flicker between the two for five seconds; last writer wins, both recover.
     * Worth knowing rather than worth engineering around.</p>
     */
    private static void countdown(MinecraftServer server, long now) {
        if (!FactionsConfig.RAID_COUNTDOWN.get()) {
            return;
        }
        OVER_UNTIL.values().removeIf(until -> until <= now);
        FactionStore store = FactionStore.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<FactionRaid.Raid> raid = FactionRaid.raidFor(server, player);
            if (raid.isPresent()) {
                long left = Math.max(0L, raid.get().endsAtMillis() - now);
                long secs = (left + 999L) / 1000L;
                Feedback.actionBar(player, Lang.fmt(secs <= 30
                                ? "msg.factions.raid_bar_urgent" : "msg.factions.raid_bar",
                        "time", clock(secs)));
                continue;
            }
            // Not in one — but their faction may have been in one a moment ago.
            store.of(player.getUUID()).map(FactionStore.Faction::id)
                    .filter(OVER_UNTIL::containsKey)
                    .ifPresent(id -> Feedback.actionBar(player,
                            Lang.get("msg.factions.raid_bar_over")));
        }
    }

    /**
     * Seconds as something worth reading at a glance.
     *
     * <p>Three shapes on purpose: {@code 4m 12s} while there is time to plan, {@code 45s} once
     * there is not, and a bare {@code 9} in the last ten — where the number IS the message and a
     * unit beside it is noise.</p>
     */
    private static String clock(long secs) {
        if (secs > 60L) {
            return (secs / 60L) + "m " + (secs % 60L) + "s";
        }
        return secs > 10L ? secs + "s" : String.valueOf(secs);
    }

    /** End it, tell everybody, and start the cooldown. */
    private static void finish(MinecraftServer server, FactionStore store, FactionRaid.Raid raid,
            FactionRaid.Outcome outcome, long now) {
        FactionRaid.end(raid, now);
        // Both sides get a final line on the bar, because the raid is out of ACTIVE from here and
        // the countdown would otherwise simply stop mid-number.
        OVER_UNTIL.put(raid.attackerId(), now + 3_000L);
        OVER_UNTIL.put(raid.defenderId(), now + 3_000L);
        // Both sides, from the one outcome. HELD and ATTACKERS_GONE are defender wins; the two
        // ways of taking something are attacker wins.
        store.recordRaid(raid.attackerId(), raid.defenderId(),
                outcome == FactionRaid.Outcome.STANDARD_PLANTED
                        || outcome == FactionRaid.Outcome.LAND_TAKEN);
        String attacker = store.byId(raid.attackerId())
                .map(FactionStore.Faction::name).orElse("?");
        String defender = store.byId(raid.defenderId())
                .map(FactionStore.Faction::name).orElse("?");
        String key = switch (outcome) {
            case STANDARD_PLANTED -> "msg.factions.raid_over_planted";
            case LAND_TAKEN -> "msg.factions.raid_over_land";
            case ATTACKERS_GONE -> "msg.factions.raid_over_repelled";
            case HELD -> "msg.factions.raid_over_held";
        };
        announce(server, Lang.fmt(key, "attacker", attacker, "defender", defender));
    }

    /**
     * Say it to the whole server.
     *
     * <p>A raid being <em>public</em> is half of what makes it an event rather than a discovery.
     * The victim already learns after the fact when land changes hands; this is the part that lets
     * everybody else turn up.</p>
     */
    public static void announce(MinecraftServer server, String message) {
        Component line = Feedback.colored(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
        }
        Factions.LOGGER.info("[raid] {}", message.replaceAll("[&§].", ""));
    }

    /**
     * Light everyone up by side.
     *
     * <p>Same shape as the carrier glow next door: a short, refreshed effect so it lapses on its
     * own the moment a raid ends, and a scoreboard team purely to colour the outline. A player
     * already on a team is left alone rather than pulled off it — including the standard carrier,
     * whose red says something more specific than which side they are on.</p>
     */
    private static void glow(MinecraftServer server) {
        if (!FactionsConfig.RAID_GLOW.get()) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam attack = team(scoreboard, ATTACK_TEAM,
                net.minecraft.world.scores.TeamColor.GOLD);
        PlayerTeam defend = team(scoreboard, DEFEND_TEAM,
                net.minecraft.world.scores.TeamColor.AQUA);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<FactionRaid.Raid> raid = FactionRaid.raidFor(server, player);
            if (raid.isEmpty()) {
                if (MARKED.remove(player.getUUID())) {
                    // ONLY the team they are actually on. Asking to remove them from the other
                    // one throws IllegalStateException — "either on another team or not on any
                    // team" — which killed the whole tick the first time a raid ended, and took
                    // the server down behind it. A player is on one team or none, never both.
                    PlayerTeam on = scoreboard.getPlayersTeam(player.getScoreboardName());
                    if (on == attack || on == defend) {
                        scoreboard.removePlayerFromTeam(player.getScoreboardName(), on);
                    }
                    player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                }
                continue;
            }
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 40, 0, false, false));
            if (scoreboard.getPlayersTeam(player.getScoreboardName()) == null) {
                boolean attacking = FactionStore.get(server).of(player.getUUID())
                        .map(f -> f.id().equals(raid.get().attackerId())).orElse(false);
                scoreboard.addPlayerToTeam(player.getScoreboardName(),
                        attacking ? attack : defend);
                MARKED.add(player.getUUID());
            }
        }
    }

    private static PlayerTeam team(Scoreboard scoreboard, String name,
            net.minecraft.world.scores.TeamColor colour) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            // The 26.2 form: setColor takes an Optional<TeamColor> rather than a ChatFormatting.
            // Same divergence FactionStandards carries, and the reason both are noted there.
            team.setColor(java.util.Optional.of(colour));
            team.setSeeFriendlyInvisibles(false);
        }
        return team;
    }

    /** Drop everything on stop, so a restart never resumes a raid. See {@link FactionRaid}. */
    public static void forgetAll() {
        FactionRaid.clear();
        MARKED.clear();
    }

    private FactionRaidEvents() {}
}

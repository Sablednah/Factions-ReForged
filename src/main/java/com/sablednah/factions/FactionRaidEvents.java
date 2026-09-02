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
     * <p>Checked in the order the outcomes matter. The standard falling beats the clock: a raid
     * whose flag was taken on the last tick was won, not held.</p>
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
            if (standardTaken(store, raid)) {
                finish(server, store, raid, FactionRaid.Outcome.STANDARD_TAKEN, now);
            } else if (FactionRaid.onlineMembers(server, raid.attackerId()).isEmpty()) {
                finish(server, store, raid, FactionRaid.Outcome.ATTACKERS_GONE, now);
            } else if (raid.expired(now)) {
                finish(server, store, raid, FactionRaid.Outcome.HELD, now);
            }
        }
        glow(server);
    }

    /**
     * Whether the defenders' standard has fallen.
     *
     * <p><b>The objective is that it comes down, not that the attacker plants it.</b> The first
     * version asked whether the attackers were <em>flying</em> it as a trophy, which reads
     * naturally and is unreachable in practice: a faction may fly exactly one standard, so anybody
     * who already has their own — which is every established faction — cannot plant a captured one
     * and could never win. Found the first time a raid was played, when the flag was taken and the
     * raid still expired as "held".</p>
     *
     * <p>Their flag falling is also the moment the <em>defenders</em> lose something, which is what
     * a raid is supposed to decide. Getting it home stays worth doing — it is the trophy and the
     * power bonus — but it is the reward rather than the win condition, so the journey home is
     * still dangerous without being the thing that ends the fight.</p>
     *
     * <p>Only counts if they had one to begin with, or a faction flying no standard would lose the
     * instant a raid was declared on them.</p>
     */
    private static boolean standardTaken(FactionStore store, FactionRaid.Raid raid) {
        return raid.defenderHadStandard() && !store.hasStandard(raid.defenderId());
    }

    /** End it, tell everybody, and start the cooldown. */
    private static void finish(MinecraftServer server, FactionStore store, FactionRaid.Raid raid,
            FactionRaid.Outcome outcome, long now) {
        FactionRaid.end(raid, now);
        String attacker = store.byId(raid.attackerId())
                .map(FactionStore.Faction::name).orElse("?");
        String defender = store.byId(raid.defenderId())
                .map(FactionStore.Faction::name).orElse("?");
        String key = switch (outcome) {
            case STANDARD_TAKEN -> "msg.factions.raid_over_taken";
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

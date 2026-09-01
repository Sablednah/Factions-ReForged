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
     * Whether the attackers hold the defenders' standard.
     *
     * <p>The objective. {@code standardCapturedFrom} is the existing bookkeeping for a captured
     * flag, so this reads the mechanic that was already there rather than inventing a second
     * notion of "taken".</p>
     */
    private static boolean standardTaken(FactionStore store, FactionRaid.Raid raid) {
        return store.byId(raid.attackerId())
                .flatMap(f -> store.standardCapturedFrom(f.id()))
                .filter(from -> from.equals(raid.defenderId()))
                .isPresent();
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
                    scoreboard.removePlayerFromTeam(player.getScoreboardName(), attack);
                    scoreboard.removePlayerFromTeam(player.getScoreboardName(), defend);
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

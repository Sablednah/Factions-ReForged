package com.sablednah.factions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.api.combat.Combat;

/**
 * What drains power, and what puts it back.
 *
 * <h2>Attribution comes from Standards, not from here</h2>
 *
 * <p>"Was a player behind this kill" is exactly the question the combat API already answers, and it
 * answers it through arrows and pets. A second implementation would eventually disagree with the
 * first, and the disagreement would depend on whether an arrow or a wolf was involved — which is a
 * bug nobody can reproduce.</p>
 *
 * <h2>Environmental deaths never cost power</h2>
 *
 * <p>Falling in your own lava is not a raid. Nobody decided it, nobody gains from it, and taking a
 * faction's land because one of its members misjudged a jump is the same mistake as tagging
 * somebody for freezing to death — the original made it deliberately, and it was wrong.</p>
 */
public final class FactionPowerEvents {

    /** Who may not regain power yet, and until when. */
    private static final Map<UUID, Long> FROZEN = new ConcurrentHashMap<>();

    /** Fractional power owed from time online, kept until it amounts to something worth storing. */
    private static final Map<UUID, Double> PENDING = new ConcurrentHashMap<>();

    private static int tickCounter;

    /** A death. Drains, if the mode says this kind of death counts. */
    public static void onDeath(ServerPlayer player, net.minecraft.world.damagesource.DamageSource cause) {
        FactionPower.Mode mode = FactionPower.Mode.of(FactionsConfig.POWER_MODE.get());
        if (!mode.active()) {
            return;
        }
        // Nobody behind it: fall, drowning, lava, the void. Not combat, so not a loss.
        if (!Combat.hasAttacker(cause)) {
            return;
        }
        boolean byPlayer = Combat.playerBehind(cause).isPresent();
        if (!mode.drainsOn(byPlayer)) {
            return;
        }

        double loss = FactionsConfig.POWER_PER_DEATH.get();
        if (loss <= 0) {
            return;
        }
        FactionStore store = FactionStore.get(player.level().getServer());
        double now = store.adjustPower(player.getUUID(), -loss);

        int freeze = FactionsConfig.POWER_FREEZE_SECONDS.get();
        if (freeze > 0) {
            // Reset rather than extended, so a run of deaths does not accumulate into a punishment
            // nobody can recover from.
            FROZEN.put(player.getUUID(), System.currentTimeMillis() + freeze * 1000L);
        }
        PENDING.remove(player.getUUID());

        told(player, store, now, "msg.factions.power_lost", loss);
    }

    /**
     * A mob died and dropped experience. Restores, in proportion.
     *
     * <p>The drop value is already Minecraft's own opinion of how hard the thing was to kill,
     * maintained by Mojang and extended for free by every mod on the server — so a tank outweighs
     * a walker without anybody maintaining a table of mob ids to be wrong about.</p>
     *
     * <p>The mob's drop, never the player's balance. Otherwise smelting is a land claim, and
     * enchanting a sword costs your faction territory.</p>
     */
    public static void onExperience(ServerPlayer killer, int experience) {
        FactionPower.Mode mode = FactionPower.Mode.of(FactionsConfig.POWER_MODE.get());
        if (!mode.active() || experience <= 0) {
            return;
        }
        double perXp = FactionsConfig.POWER_PER_XP.get();
        if (perXp <= 0 || frozen(killer.getUUID())) {
            return;
        }
        FactionStore store = FactionStore.get(killer.level().getServer());
        store.adjustPower(killer.getUUID(), experience * perXp);
    }

    /**
     * Time online, paid a little at a time.
     *
     * <p>Online only, because power that recovers while you are asleep is a clock rather than a
     * consequence. Accumulated as a fraction and only written when it amounts to something, so a
     * busy server is not marking its save file dirty every five seconds for a hundredth of a
     * point.</p>
     */
    public static void tick(MinecraftServer server) {
        FactionPower.Mode mode = FactionPower.Mode.of(FactionsConfig.POWER_MODE.get());
        if (!mode.active()) {
            return;
        }
        // Once a second: fast enough that the glow lapses within a moment of a flag going into a
        // chest, cheap enough not to matter — online players, two stacks each.
        if (++tickCounter % 20 == 0) {
            FactionStandards.markCarriers(server);
        }
        // Every tick, because revalidate keeps its OWN timer. Nesting it behind the power gate
        // below multiplied the two counters together and made a ten-second sweep run every
        // sixteen minutes — a flag could be roofed over and still paying for a quarter of an hour.
        FactionStandards.revalidate(server);
        // Every five seconds. Power moves on a scale of minutes; asking more often than that is
        // work nobody can perceive the result of.
        if (tickCounter % 100 != 0) {
            return;
        }
        double perMinute = FactionsConfig.POWER_PER_MINUTE.get();
        if (perMinute <= 0) {
            return;
        }
        double baseShare = perMinute / 12.0D; // five seconds of a minute
        double max = FactionsConfig.POWER_MAX.get();
        FactionStore store = FactionStore.get(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (frozen(id) || store.powerOf(id) >= max) {
                continue;
            }
            double owed = PENDING.merge(id, baseShare * standardMultiplier(store, id), Double::sum);
            if (owed >= 0.05D) {
                PENDING.remove(id);
                store.adjustPower(id, owed);
            }
        }
    }

    /**
     * How fast this player's faction recovers, given what it is flying.
     *
     * <p>A flag is worth having and worth taking, and those are the same number seen from two
     * sides. The captured bonus only counts while the trophy is <b>planted</b> — a standard in a
     * chest pays nobody, because the mechanic rewards use rather than possession and a flag in a
     * box is a flag out of the game.</p>
     */
    /**
     * How fast this faction recovers, and why — for showing a player rather than for arithmetic.
     *
     * @return points per minute, and the reason in a message key
     */
    public static double regenPerMinute(net.minecraft.server.MinecraftServer server,
            FactionStore store, String factionId) {
        // Checked now rather than trusted from the last sweep. Somebody asking what their regen
        // rate is has almost always just changed something — roofed the flag over, taken the roof
        // off — and being told the answer from ten seconds ago is being told the wrong one.
        FactionStandards.refresh(server, store, factionId);
        return FactionsConfig.POWER_PER_MINUTE.get() * multiplierFor(store, factionId);
    }

    /** Which of the three standard states this faction is in, as a message key. */
    public static String standardState(net.minecraft.server.MinecraftServer server,
            FactionStore store, String factionId) {
        FactionStandards.refresh(server, store, factionId);
        return standardState(store, factionId);
    }

    private static String standardState(FactionStore store, String factionId) {
        if (!store.hasStandard(factionId)) {
            // "No standard" is only the whole truth when nobody took yours. Somebody who has been
            // raided is not missing a flag, they are missing THEIR flag, and the two call for
            // different reactions.
            boolean somebodyHasIt = store.all().stream()
                    .anyMatch(other -> store.standardCapturedFrom(other.id())
                            .map(factionId::equals).orElse(false));
            return somebodyHasIt
                    ? "msg.factions.standard_state_stolen" : "msg.factions.standard_state_none";
        }
        if (!FactionStandards.flying(factionId)) {
            return "msg.factions.standard_state_covered";
        }
        return store.standardCapturedFrom(factionId).isPresent()
                ? "msg.factions.standard_state_trophy" : "msg.factions.standard_state_flying";
    }

    private static double multiplierFor(FactionStore store, String id) {
        boolean captured = store.standardCapturedFrom(id).isPresent();
        boolean up = store.hasStandard(id) && FactionStandards.flying(id);
        double own = up && !captured
                ? FactionsConfig.REGEN_WITH_STANDARD.get()
                : FactionsConfig.REGEN_WITHOUT_STANDARD.get();
        return own + (captured && up ? FactionsConfig.REGEN_WITH_CAPTURED.get() : 0.0D);
    }

    private static double standardMultiplier(FactionStore store, UUID player) {
        Optional<FactionStore.Faction> mine = store.of(player);
        if (mine.isEmpty()) {
            return FactionsConfig.REGEN_WITHOUT_STANDARD.get();
        }
        // Not merely planted — actually flying. A flag somebody has roofed over is not visible,
        // and the bonus is paid for a visible flag.
        return multiplierFor(store, mine.get().id());
    }

    private static boolean frozen(UUID player) {
        Long until = FROZEN.get(player);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            FROZEN.remove(player);
            return false;
        }
        return true;
    }

    /**
     * Tell them what it cost, and what it cost the faction.
     *
     * <p>Land quietly becoming takeable without anybody being told is indistinguishable from a
     * bug — and worse, the first they would learn of it is somebody else standing in their base.</p>
     */
    private static void told(ServerPlayer player, FactionStore store, double now, String key,
            double lost) {
        com.sablednah.standards.neoforge.Feedback.chat(player,
                com.sablednah.standards.neoforge.Lang.fmt(key,
                        "lost", trim(lost), "power", trim(now),
                        "max", trim(FactionsConfig.POWER_MAX.get())));

        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        if (mine.isEmpty()) {
            return;
        }
        int held = store.claimCount(mine.get().id());
        int entitled = FactionPower.entitlement(mine.get().members().size(),
                store.powerOf(mine.get()), FactionsConfig.POWER_MAX.get(),
                FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get());
        int over = FactionPower.overreach(held, entitled);
        if (over > 0) {
            // Everybody, not just whoever died: it is the faction's land at stake and the person
            // who can do something about it may not be the person who lost the power.
            String warning = com.sablednah.standards.neoforge.Lang.fmt(
                    "msg.factions.power_exposed", "over", over, "held", held, "entitled", entitled);
            for (UUID member : mine.get().memberIds()) {
                ServerPlayer online = player.level().getServer().getPlayerList().getPlayer(member);
                if (online != null) {
                    com.sablednah.standards.neoforge.Feedback.chat(online, warning);
                }
            }
        }
    }

    /** One decimal place, and no trailing ".0" — power reads as a quantity, not a measurement. */
    public static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public static void forget(UUID player) {
        FROZEN.remove(player);
        PENDING.remove(player);
    }

    private FactionPowerEvents() {}
}

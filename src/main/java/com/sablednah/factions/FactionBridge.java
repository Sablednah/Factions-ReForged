package com.sablednah.factions;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.api.groups.ClaimProvider;
import com.sablednah.standards.api.groups.Claims;
import com.sablednah.standards.api.groups.Group;
import com.sablednah.standards.api.groups.GroupKind;
import com.sablednah.standards.api.groups.GroupProvider;
import com.sablednah.standards.api.groups.Groups;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Where Factions meets Standards: one class, both seams.
 *
 * <p>Everything this mod knows that anybody else might want is published here, and nothing else
 * reaches across. Two consequences worth having in mind:</p>
 *
 * <ul>
 *   <li><b>Chat tags cost nothing.</b> Registering as a group kind means faction tags render
 *       through the decorator Standards already has — no chat code in this mod at all.</li>
 *   <li><b>Grief checks stop being ours alone.</b> ZombieMod and CityWorld can ask "may this be
 *       broken here" without either of them knowing Factions exists, and the day a pack swaps to
 *       a different land mod, they carry on working.</li>
 * </ul>
 */
public final class FactionBridge implements GroupProvider, ClaimProvider {

    /**
     * The kind factions register as.
     *
     * <p>{@code displayName()} resolves through Standards' catalogue on every call, so a server
     * that re-skins "faction" to "clan" or "house" in {@code messages.yml} is followed here and in
     * every chat tag, with no restart and no code change.</p>
     */
    public static final GroupKind KIND = new GroupKind() {
        @Override
        public String id() {
            return "factions:faction";
        }

        @Override
        public String displayName() {
            return Lang.get("term.faction");
        }

        @Override
        public boolean exclusive() {
            return true;
        }
    };

    private final MinecraftServer server;

    private FactionBridge(MinecraftServer server) {
        this.server = server;
    }

    public static void install(MinecraftServer server) {
        // Who may fight whom now goes through Standards, so a hostile SKILL is refused for the
        // same reasons a sword is. Cancelling damage ourselves only ever stopped swords.
        com.sablednah.standards.api.combat.Harm.register(new Pvp());
        FactionBridge bridge = new FactionBridge(server);
        Groups.register(bridge);
        Claims.register(bridge);
    }

    public static void uninstall() {
        Groups.unregister(KIND);
        Claims.clear();
    }

    // --- groups ---

    @Override
    public GroupKind kind() {
        return KIND;
    }

    @Override
    public Collection<Group> groupsOf(ServerPlayer player) {
        return store().of(player.getUUID())
                .<Collection<Group>>map(f -> List.of(wrap(f)))
                .orElseGet(List::of);
    }

    @Override
    public Optional<Group> byName(String name) {
        return store().lookup(name).map(FactionBridge::wrap);
    }

    @Override
    public Collection<Group> all() {
        return store().all().stream().map(FactionBridge::wrap).map(g -> (Group) g).toList();
    }

    // --- claims ---

    @Override
    public String id() {
        return "factions:claims";
    }

    @Override
    public int priority() {
        // Above a bridge to somebody else's claims, below nothing in particular. A server running
        // both this and an FTB Chunks bridge has a configuration problem rather than a tie to
        // break, but somebody has to win and the mod that owns the land model should.
        return 100;
    }

    @Override
    public Optional<Group> owner(ServerLevel level, ChunkPos chunk) {
        return store().factionAt(dimensionOf(level), chunk).map(FactionBridge::wrap);
    }

    /**
     * Whether this player may change this block.
     *
     * <p>The hot path — block break, block place, block interact — so it does the cheapest thing
     * that can answer: one map lookup keyed on the chunk, and an early exit for wilderness, which
     * is most of the world on most servers.</p>
     *
     * <p>Order matters. Wilderness first because it is the common case; then own faction; then
     * allies, who are trusted with land by definition. Everything else is refused.</p>
     */
    @Override
    public boolean mayModify(ServerPlayer player, ServerLevel level, BlockPos pos) {
        // One answer, in one place. This is the seam ZombieMod and CityWorld will ask through,
        // and our own listeners ask the same method, so the two can never drift into disagreeing
        // about who may touch what.
        return FactionProtection.mayBuild(player, level, pos);
    }

    /**
     * Mobs may not chew through claimed land.
     *
     * <p>Answered here rather than left to every mob mod to derive from {@code owner()}, so the
     * rule is ours to change: a war zone letting mobs grief while a home claim does not is a
     * setting this mod should own, not one each consumer invents.</p>
     *
     * <p>ZombieMod's session raised this: it had no player to pass to {@code mayModify} and was
     * having to treat "claimed by anybody" as "leave it alone", which is the right answer today
     * and would have been the wrong place to decide it.</p>
     */
    @Override
    public boolean griefAllowed(ServerLevel level, BlockPos pos) {
        return store().ownerOf(dimensionOf(level), pos.getX() >> 4, pos.getZ() >> 4).isEmpty();
    }

    // --- shared ---

    public static String dimensionOf(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private FactionStore store() {
        return FactionStore.get(server);
    }

    /** A faction seen through the API's eyes. */
    static Group wrap(FactionStore.Faction faction) {
        return new Group() {
            @Override
            public GroupKind kind() {
                return KIND;
            }

            @Override
            public String id() {
                return faction.id();
            }

            @Override
            public String name() {
                return faction.name();
            }

            @Override
            public Optional<String> tag() {
                return faction.tag().isEmpty() ? Optional.empty() : Optional.of(faction.tag());
            }

            @Override
            public boolean contains(UUID player) {
                return faction.contains(player);
            }

            @Override
            public Collection<UUID> members() {
                return faction.memberIds();
            }
        };
    }

    /**
     * Who may fight whom, answered through Standards rather than by cancelling damage ourselves.
     *
     * <h3>Why it moved</h3>
     *
     * <p>Cancelling {@code LivingIncomingDamageEvent} stops swords and stops nothing else. A
     * hostile <b>skill</b> — a curse, a snare, a summon aimed at somebody — is not a damage event,
     * so a faction that declared itself peaceful was peaceful against arrows and defenceless
     * against spells. That is not what {@code /f peaceful} promises, and no amount of care in this
     * file could have fixed it: the mod casting the spell has to be able to ask.</p>
     */
    public static final class Pvp implements com.sablednah.standards.api.combat.HarmProvider {

        @Override
        public String id() {
            return "factions:pvp";
        }

        @Override
        public Optional<net.minecraft.network.chat.Component> forbids(ServerPlayer attacker,
                ServerPlayer victim) {
            FactionStore store = FactionStore.get(victim.level().getServer());
            Optional<FactionStore.Faction> mine = store.of(attacker.getUUID());
            Optional<FactionStore.Faction> theirs = store.of(victim.getUUID());

            String key = null;
            // Peaceful first, and in both directions, because it is a promise rather than a
            // preference: a faction that has opted out must not be draggable back in by somebody
            // else's declaration.
            if (mine.map(FactionStore.Faction::peaceful).orElse(false)
                    || theirs.map(FactionStore.Faction::peaceful).orElse(false)) {
                key = "msg.factions.pvp_peaceful";
            } else if (mine.isPresent() && theirs.isPresent()
                    && mine.get().id().equals(theirs.get().id())) {
                if (!FactionsConfig.PVP_IN_OWN_LAND.get()) {
                    key = "msg.factions.pvp_same_faction";
                }
            } else if (mine.isPresent() && theirs.isPresent()
                    && store.relation(mine.get().id(), theirs.get().id())
                            == FactionStore.Relation.ALLY
                    && !FactionsConfig.PVP_BETWEEN_ALLIES.get()) {
                // Allies. Missing until 1.1, which meant an alliance stopped you being overclaimed
                // and did nothing whatever to stop you being shot — friendly fire is off between
                // allies in every version of this game anybody remembers.
                key = "msg.factions.pvp_ally";
            } else if (!FactionsConfig.PVP_BETWEEN_FACTIONS.get()) {
                key = "msg.factions.pvp_disabled";
            }
            return key == null ? Optional.empty()
                    : Optional.of(Feedback.colored(Lang.get(key)));
        }
    }

    /** The level a stored dimension id refers to, if it still exists. */
    public static Optional<ServerLevel> levelFor(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (dimensionOf(level).equals(dimension)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}

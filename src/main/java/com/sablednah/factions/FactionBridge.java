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
}

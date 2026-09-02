package com.sablednah.factions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/**
 * Everything a faction is, and every chunk one holds.
 *
 * <h2>Relations: allies agree, enemies do not</h2>
 *
 * <p>Declaring an ally is a <em>wish</em> — two factions are allied only when both have said so.
 * Declaring an enemy takes effect immediately and alone. That asymmetry is the one rule the whole
 * relation system rests on, and it is the right way round: you cannot conscript a friend, and you
 * cannot refuse to be someone's target by declining the paperwork.</p>
 *
 * <h2>Peaceful is a relation, not a config flag</h2>
 *
 * <p>A faction can declare itself peaceful and opt out of fighting entirely. It is per-faction
 * state that other factions can see, rather than a server-wide switch — so a co-operative corner
 * can exist on a server that otherwise fights, and "everyone is peaceful and cannot change it" is
 * a configuration of the same model rather than a separate code path.</p>
 */
public final class FactionStore extends net.minecraft.world.level.saveddata.SavedData {

    /** Who may do what. Deliberately three: any more and nobody remembers which is which. */
    public enum Rank {
        MEMBER, OFFICER, LEADER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean atLeast(Rank other) {
            return ordinal() >= other.ordinal();
        }
    }

    /** How one faction regards another. Stored one-way; {@link #relation} resolves the pair. */
    public enum Relation {
        ENEMY, NEUTRAL, ALLY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Member(UUID player, Rank rank) {
        static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Member::player),
                Codec.STRING.xmap(s -> Rank.valueOf(s.toUpperCase(Locale.ROOT)), Rank::key)
                        .optionalFieldOf("rank", Rank.MEMBER).forGetter(Member::rank))
                .apply(i, Member::new));
    }

    /**
     * @param id       opaque and permanent; never derived from the name, so a rename orphans
     *                 nothing and a recycled name cannot inherit somebody else's land
     * @param allies   factions this one has <em>offered</em> alliance to
     * @param enemies  factions this one has declared against, effective alone
     * @param peaceful opted out of fighting altogether
     */
    public record Faction(String id, String name, String tag, UUID leader, List<Member> members,
            Set<String> allies, Set<String> enemies, boolean peaceful, Optional<Waypoint> home) {

        static final Codec<Faction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Faction::id),
                Codec.STRING.fieldOf("name").forGetter(Faction::name),
                Codec.STRING.optionalFieldOf("tag", "").forGetter(Faction::tag),
                UUIDUtil.STRING_CODEC.fieldOf("leader").forGetter(Faction::leader),
                Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Faction::members),
                // Lists in the codec, sets in the record. Sorting that out in the constructor
                // below is duller than an xmap and considerably easier to read.
                Codec.STRING.listOf().optionalFieldOf("allies", List.of())
                        .forGetter(f -> List.copyOf(f.allies())),
                Codec.STRING.listOf().optionalFieldOf("enemies", List.of())
                        .forGetter(f -> List.copyOf(f.enemies())),
                Codec.BOOL.optionalFieldOf("peaceful", false).forGetter(Faction::peaceful),
                Waypoint.CODEC.optionalFieldOf("home").forGetter(Faction::home))
                .apply(i, (id, name, tag, leader, members, allies, enemies, peaceful, home) ->
                        new Faction(id, name, tag, leader, members,
                                Set.copyOf(allies), Set.copyOf(enemies), peaceful, home)));

        public boolean contains(UUID player) {
            for (Member m : members) {
                if (m.player().equals(player)) {
                    return true;
                }
            }
            return false;
        }

        public Rank rankOf(UUID player) {
            for (Member m : members) {
                if (m.player().equals(player)) {
                    return m.rank();
                }
            }
            return null;
        }

        public List<UUID> memberIds() {
            return members.stream().map(Member::player).toList();
        }
    }

    private record Claim(String dimension, int x, int z, String faction) {
        static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Claim::dimension),
                Codec.INT.fieldOf("x").forGetter(Claim::x),
                Codec.INT.fieldOf("z").forGetter(Claim::z),
                Codec.STRING.fieldOf("faction").forGetter(Claim::faction))
                .apply(i, Claim::new));
    }

    /** One faction's bank balance. A row rather than a field on Faction, so adding it did not
     * have to touch every place a Faction is rebuilt — the same reasoning that keeps claims out
     * of the record. */
    private record Bank(String faction, double balance) {
        static final Codec<Bank> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("faction").forGetter(Bank::faction),
                Codec.DOUBLE.fieldOf("balance").forGetter(Bank::balance))
                .apply(i, Bank::new));
    }

    /**
     * One player's power.
     *
     * <p>Kept per <b>player</b> and not per faction, because that is what it is: leaving a faction
     * does not restore the power you lost dying, and joining one does not lend you somebody
     * else's. A faction's power is the sum of the people currently in it, which is also what makes
     * recruiting worth something and losing members cost something.</p>
     *
     * <p>Stored for everyone, including players in no faction — they may join one tomorrow, and a
     * player who could wipe their losses by leaving for an afternoon would.</p>
     */
    private record Power(UUID player, double power) {
        static final Codec<Power> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Power::player),
                Codec.DOUBLE.fieldOf("power").forGetter(Power::power))
                .apply(i, Power::new));
    }

    /**
     * A faction's standard: a real banner, standing somewhere, that an enemy can come and take.
     *
     * <p>The <b>colour and pattern are stored, not just the position</b>, which is what makes it a
     * flag rather than a marker. A faction designs its own banner in a loom and that design becomes
     * its identity — the colour it wears in chat, and the thing an enemy carries home.</p>
     *
     * @param faction   whose it is
     * @param dimension where it stands
     * @param capturedFrom the faction it was taken from, if this is somebody else's flag being
     *                     flown as a trophy. Empty for your own.
     */
    private record Standard(String faction, String dimension, int x, int y, int z,
            net.minecraft.world.item.DyeColor colour,
            net.minecraft.world.level.block.entity.BannerPatternLayers patterns,
            java.util.Optional<String> capturedFrom) {
        static final Codec<Standard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("faction").forGetter(Standard::faction),
                Codec.STRING.fieldOf("dimension").forGetter(Standard::dimension),
                Codec.INT.fieldOf("x").forGetter(Standard::x),
                Codec.INT.fieldOf("y").forGetter(Standard::y),
                Codec.INT.fieldOf("z").forGetter(Standard::z),
                net.minecraft.world.item.DyeColor.CODEC.optionalFieldOf("colour",
                        net.minecraft.world.item.DyeColor.WHITE).forGetter(Standard::colour),
                net.minecraft.world.level.block.entity.BannerPatternLayers.CODEC
                        .optionalFieldOf("patterns",
                                net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)
                        .forGetter(Standard::patterns),
                Codec.STRING.optionalFieldOf("capturedFrom").forGetter(Standard::capturedFrom))
                .apply(i, Standard::new));
    }

    private record Snapshot(List<Faction> factions, List<Claim> claims, List<Bank> banks,
            List<Power> power, List<Standard> standards) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Faction.CODEC.listOf().optionalFieldOf("factions", List.of()).forGetter(Snapshot::factions),
                Claim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(Snapshot::claims),
                Bank.CODEC.listOf().optionalFieldOf("banks", List.of()).forGetter(Snapshot::banks),
                // Optional with an empty default, so a world saved before power existed loads
                // with everybody at full rather than failing to decode and taking the factions
                // with it.
                Power.CODEC.listOf().optionalFieldOf("power", List.of()).forGetter(Snapshot::power),
                Standard.CODEC.listOf().optionalFieldOf("standards", List.of())
                        .forGetter(Snapshot::standards))
                .apply(i, Snapshot::new));
    }

    private static final Codec<FactionStore> CODEC =
            Snapshot.CODEC.xmap(FactionStore::new, FactionStore::snapshot);

    public static final net.minecraft.world.level.saveddata.SavedDataType<FactionStore> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    "factions", FactionStore::new, CODEC, null);

    private final Map<String, Faction> factions = new LinkedHashMap<>();

    /**
     * "dim|x|z" → faction id.
     *
     * <p>A flat map rather than anything cleverer, because the hot question is "who owns this one
     * chunk" on every block break — and a hash lookup on a small key is hard to beat. Iterating it
     * to find a faction's land is the rare direction and can afford to be the slow one.</p>
     */
    private final Map<String, String> claims = new LinkedHashMap<>();

    private FactionStore() {}

    /** faction id → what it is holding. Absent means zero; no row is written for an empty bank. */
    private final Map<String, Double> banks = new LinkedHashMap<>();

    /**
     * player → current power. Absent means <b>full</b>, not zero.
     *
     * <p>Which is the opposite of the original, and deliberate: it starts new players at maximum
     * rather than at nothing. MassiveCraft began everybody at zero, so a new faction could claim
     * nothing for the best part of an hour — defensible on a server where that is the ritual, and
     * simply baffling on one where somebody has installed this expecting it to behave like the
     * claim limit it replaces. A server that wants the old feel sets {@code startAtZero}.</p>
     */
    private final Map<UUID, Double> power = new LinkedHashMap<>();

    /** faction id → the flag it is flying. Its own, or one it has taken. */
    /** Flyer to the flags they have planted: at most one of their own, plus trophies. */
    private final Map<String, List<Standard>> standards = new LinkedHashMap<>();

    private FactionStore(Snapshot snapshot) {
        snapshot.factions().forEach(f -> factions.put(f.id(), f));
        snapshot.claims().forEach(c -> claims.put(key(c.dimension(), c.x(), c.z()), c.faction()));
        snapshot.banks().forEach(b -> banks.put(b.faction(), b.balance()));
        snapshot.power().forEach(row -> power.put(row.player(), row.power()));
        snapshot.standards().forEach(st -> standards
                .computeIfAbsent(st.faction(), k -> new ArrayList<>()).add(st));
    }

    private Snapshot snapshot() {
        List<Claim> out = new ArrayList<>();
        claims.forEach((k, faction) -> {
            String[] bits = k.split("\\|");
            out.add(new Claim(bits[0], Integer.parseInt(bits[1]), Integer.parseInt(bits[2]), faction));
        });
        List<Bank> money = new ArrayList<>();
        banks.forEach((id, balance) -> {
            if (balance != 0.0D) {
                money.add(new Bank(id, balance));
            }
        });
        List<Power> powers = new ArrayList<>();
        power.forEach((id, value) -> powers.add(new Power(id, value)));
        List<Standard> flags = new ArrayList<>();
        standards.values().forEach(flags::addAll);
        return new Snapshot(List.copyOf(factions.values()), out, money, powers,
                List.copyOf(flags));
    }

    public static FactionStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static String key(String dimension, int x, int z) {
        return dimension + "|" + x + "|" + z;
    }

    // --- factions ---

    public Optional<Faction> byId(String id) {
        return Optional.ofNullable(factions.get(id));
    }

    public Optional<Faction> byName(String name) {
        return factions.values().stream().filter(f -> f.name().equalsIgnoreCase(name)).findFirst();
    }

    /** By name or by tag, since players use whichever is shorter to type. */
    public Optional<Faction> lookup(String nameOrTag) {
        Optional<Faction> byName = byName(nameOrTag);
        if (byName.isPresent()) {
            return byName;
        }
        return factions.values().stream()
                .filter(f -> !f.tag().isEmpty() && f.tag().equalsIgnoreCase(nameOrTag))
                .findFirst();
    }

    public Optional<Faction> of(UUID player) {
        return factions.values().stream().filter(f -> f.contains(player)).findFirst();
    }

    public List<Faction> all() {
        return List.copyOf(factions.values());
    }

    public Optional<Faction> create(String name, UUID leader) {
        if (byName(name).isPresent() || of(leader).isPresent()) {
            return Optional.empty();
        }
        Faction f = new Faction(UUID.randomUUID().toString().substring(0, 8), name, "", leader,
                List.of(new Member(leader, Rank.LEADER)), Set.of(), Set.of(), false,
                Optional.empty());
        factions.put(f.id(), f);
        setDirty();
        return Optional.of(f);
    }

    /** Replace a faction wholesale. Every mutation below goes through here. */
    private void put(Faction f) {
        factions.put(f.id(), f);
        setDirty();
    }

    public boolean addMember(String id, UUID player, Rank rank) {
        Faction f = factions.get(id);
        if (f == null || f.contains(player) || of(player).isPresent()) {
            return false;
        }
        List<Member> members = new ArrayList<>(f.members());
        members.add(new Member(player, rank));
        put(new Faction(f.id(), f.name(), f.tag(), f.leader(), List.copyOf(members),
                f.allies(), f.enemies(), f.peaceful(), f.home()));
        return true;
    }

    public boolean removeMember(String id, UUID player) {
        Faction f = factions.get(id);
        if (f == null || !f.contains(player)) {
            return false;
        }
        if (f.leader().equals(player)) {
            return disband(id);
        }
        List<Member> members = new ArrayList<>(f.members());
        members.removeIf(m -> m.player().equals(player));
        put(new Faction(f.id(), f.name(), f.tag(), f.leader(), List.copyOf(members),
                f.allies(), f.enemies(), f.peaceful(), f.home()));
        return true;
    }

    public boolean setRank(String id, UUID player, Rank rank) {
        Faction f = factions.get(id);
        if (f == null || !f.contains(player) || f.leader().equals(player)) {
            return false;
        }
        List<Member> members = new ArrayList<>(f.members());
        members.replaceAll(m -> m.player().equals(player) ? new Member(player, rank) : m);
        put(new Faction(f.id(), f.name(), f.tag(), f.leader(), List.copyOf(members),
                f.allies(), f.enemies(), f.peaceful(), f.home()));
        return true;
    }

    public boolean rename(String id, String newName) {
        Faction f = factions.get(id);
        if (f == null) {
            return false;
        }
        Optional<Faction> clash = byName(newName);
        if (clash.isPresent() && !clash.get().id().equals(id)) {
            return false;
        }
        put(new Faction(f.id(), newName, f.tag(), f.leader(), f.members(),
                f.allies(), f.enemies(), f.peaceful(), f.home()));
        return true;
    }

    public boolean setTag(String id, String tag) {
        Faction f = factions.get(id);
        if (f == null) {
            return false;
        }
        boolean taken = factions.values().stream()
                .anyMatch(o -> !o.id().equals(id) && !tag.isEmpty() && o.tag().equalsIgnoreCase(tag));
        if (taken) {
            return false;
        }
        put(new Faction(f.id(), f.name(), tag, f.leader(), f.members(),
                f.allies(), f.enemies(), f.peaceful(), f.home()));
        return true;
    }

    public void setHome(String id, Waypoint where) {
        Faction f = factions.get(id);
        if (f != null) {
            put(new Faction(f.id(), f.name(), f.tag(), f.leader(), f.members(),
                    f.allies(), f.enemies(), f.peaceful(), Optional.of(where)));
        }
    }

    public void setPeaceful(String id, boolean peaceful) {
        Faction f = factions.get(id);
        if (f != null) {
            put(new Faction(f.id(), f.name(), f.tag(), f.leader(), f.members(),
                    f.allies(), f.enemies(), peaceful, f.home()));
        }
    }

    public boolean disband(String id) {
        if (factions.remove(id) == null) {
            return false;
        }
        // The land goes with it. Orphaned claims would keep a dead faction's fences standing.
        claims.values().removeIf(id::equals);
        // The bank too. Money in a disbanded faction's account is money nobody can ever reach,
        // and a recycled id inheriting it would be worse.
        banks.remove(id);
        // Power is NOT cleared: it belongs to the players, not to the faction they were in, and
        // disbanding to wipe your losses would be the first thing anybody tried.
        standards.remove(id);
        // And anybody flying THIS faction's captured flag stops flying it — the trophy was a
        // trophy because its owner existed to want it back. Only that one comes down; a flyer
        // keeps its own flag and any other trophies it holds.
        for (Map.Entry<String, List<Standard>> e : new LinkedHashMap<>(standards).entrySet()) {
            List<Standard> left = e.getValue().stream()
                    .filter(st -> !st.capturedFrom().map(id::equals).orElse(false))
                    .toList();
            if (left.size() != e.getValue().size()) {
                if (left.isEmpty()) {
                    standards.remove(e.getKey());
                } else {
                    standards.put(e.getKey(), left);
                }
            }
        }
        // And so do other factions' opinions about it, or a recycled name inherits old grudges.
        List<Faction> touched = factions.values().stream()
                .filter(f -> f.allies().contains(id) || f.enemies().contains(id))
                .toList();
        for (Faction f : touched) {
            Set<String> allies = new LinkedHashSet<>(f.allies());
            Set<String> enemies = new LinkedHashSet<>(f.enemies());
            allies.remove(id);
            enemies.remove(id);
            factions.put(f.id(), new Faction(f.id(), f.name(), f.tag(), f.leader(), f.members(),
                    Set.copyOf(allies), Set.copyOf(enemies), f.peaceful(), f.home()));
        }
        setDirty();
        return true;
    }

    /**
     * Whether this chunk is on the edge of that faction's territory.
     *
     * <p>A chunk they own with at least one orthogonal neighbour they do not. Used by overclaiming
     * so a raid has to start at the outside and work in, rather than reaching over a wall for the
     * one chunk somebody keeps their things in.</p>
     */
    public boolean isBorderOf(String dimension, int x, int z, String factionId) {
        if (!ownerOf(dimension, x, z).map(factionId::equals).orElse(false)) {
            return false;
        }
        return !ownerOf(dimension, x + 1, z).map(factionId::equals).orElse(false)
                || !ownerOf(dimension, x - 1, z).map(factionId::equals).orElse(false)
                || !ownerOf(dimension, x, z + 1).map(factionId::equals).orElse(false)
                || !ownerOf(dimension, x, z - 1).map(factionId::equals).orElse(false);
    }

    // --- power ---

    /** Somebody's current power. Absent means they have not lost any. */
    public double powerOf(UUID player) {
        return power.getOrDefault(player, FactionsConfig.POWER_MAX.get());
    }

    /**
     * Move somebody's power, clamped to the configured range.
     *
     * @return what it ended up at
     */
    public double adjustPower(UUID player, double delta) {
        double min = FactionsConfig.POWER_MIN.get();
        double max = FactionsConfig.POWER_MAX.get();
        double now = Math.max(min, Math.min(max, powerOf(player) + delta));
        power.put(player, now);
        setDirty();
        return now;
    }

    /**
     * A faction's power: the sum of the people currently in it.
     *
     * <p>Offline members count. Their power is a fact about them rather than about their presence,
     * and a faction that became raidable every time its members went to work would teach people to
     * log in at 3am rather than to play well.</p>
     */
    public double powerOf(Faction f) {
        double total = 0;
        for (UUID member : f.memberIds()) {
            total += powerOf(member);
        }
        return total;
    }

    // --- the standard ---

    /**
     * A faction flies <b>one of its own</b> and any number of captured trophies.
     *
     * <p>One-per-faction used to be structural rather than a rule — the map was keyed by faction —
     * which is how a raid's original win condition came to be unreachable: an attacker already
     * flying their own flag could never plant a captured one. See {@code POWER.md} §6.</p>
     *
     * <p>The saved format did not change: {@link Snapshot} always held a <em>list</em>, and only
     * the in-memory index is different.</p>
     */
    private List<Standard> flagsOf(String factionId) {
        return standards.getOrDefault(factionId, List.of());
    }

    private Optional<Standard> ownFlag(String factionId) {
        return flagsOf(factionId).stream().filter(st -> st.capturedFrom().isEmpty()).findFirst();
    }

    /** Where this faction's OWN flag stands, if it has planted one. */
    public Optional<net.minecraft.core.BlockPos> standardPos(String factionId) {
        return ownFlag(factionId)
                .map(st -> new net.minecraft.core.BlockPos(st.x(), st.y(), st.z()));
    }

    public Optional<String> standardDimension(String factionId) {
        return ownFlag(factionId).map(Standard::dimension);
    }

    /** Every flag this faction flies, own and captured, as dimension-and-position pairs. */
    public List<Placed> standardsOf(String factionId) {
        return flagsOf(factionId).stream()
                .map(st -> new Placed(st.dimension(),
                        new net.minecraft.core.BlockPos(st.x(), st.y(), st.z()),
                        st.capturedFrom()))
                .toList();
    }

    /** One planted flag, for callers that need to tell them apart. */
    public record Placed(String dimension, net.minecraft.core.BlockPos pos,
            Optional<String> capturedFrom) {}

    /** The colour a faction wears, taken from its own banner. White until it plants one. */
    public net.minecraft.world.item.DyeColor colourOf(String factionId) {
        // A captured flag is somebody else's identity, so it never becomes yours. You are flying
        // it, not wearing it — which is why this reads the OWN flag rather than any of them.
        return ownFlag(factionId).map(Standard::colour)
                .orElse(net.minecraft.world.item.DyeColor.WHITE);
    }

    /** Whether this faction has planted its <b>own</b> flag — the one that can be taken from it. */
    public boolean hasStandard(String factionId) {
        return ownFlag(factionId).isPresent();
    }

    /** Whether it flies anything at all, its own or somebody else's. */
    public boolean hasAnyStandard(String factionId) {
        return !flagsOf(factionId).isEmpty();
    }

    /** Everyone whose flag this faction is flying as a trophy. */
    public List<String> capturedStandards(String factionId) {
        return flagsOf(factionId).stream()
                .map(Standard::capturedFrom)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Whether the flag this faction flies was taken from somebody.
     *
     * <p>Kept for callers that only care whether <em>any</em> trophy is flown. Where several may
     * matter, use {@link #capturedStandards}.</p>
     */
    public Optional<String> standardCapturedFrom(String factionId) {
        return capturedStandards(factionId).stream().findFirst();
    }

    /** Whoever is flying a standard at this exact spot, if anybody. */
    public Optional<String> standardAt(String dimension, net.minecraft.core.BlockPos pos) {
        for (List<Standard> flags : standards.values()) {
            for (Standard st : flags) {
                if (st.dimension().equals(dimension) && st.x() == pos.getX()
                        && st.y() == pos.getY() && st.z() == pos.getZ()) {
                    return Optional.of(st.faction());
                }
            }
        }
        return Optional.empty();
    }

    /** Which faction's flag stands at this spot — the original owner, not the flyer. */
    public Optional<String> standardOwnerAt(String dimension, net.minecraft.core.BlockPos pos) {
        for (List<Standard> flags : standards.values()) {
            for (Standard st : flags) {
                if (st.dimension().equals(dimension) && st.x() == pos.getX()
                        && st.y() == pos.getY() && st.z() == pos.getZ()) {
                    return Optional.of(st.capturedFrom().orElse(st.faction()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Plant one. Your own <b>replaces</b> any own flag you had; a trophy is added alongside.
     *
     * <p>The replace half keeps the invariant that exactly one flag is yours — it is the thing an
     * enemy can take from you, and two of them would mean two different answers to "where is your
     * standard".</p>
     */
    public void setStandard(String factionId, String dimension, net.minecraft.core.BlockPos pos,
            net.minecraft.world.item.DyeColor colour,
            net.minecraft.world.level.block.entity.BannerPatternLayers patterns,
            Optional<String> capturedFrom) {
        List<Standard> flags = new ArrayList<>(flagsOf(factionId));
        if (capturedFrom.isEmpty()) {
            flags.removeIf(st -> st.capturedFrom().isEmpty());
        }
        flags.add(new Standard(factionId, dimension, pos.getX(), pos.getY(), pos.getZ(),
                colour, patterns, capturedFrom));
        standards.put(factionId, List.copyOf(flags));
        setDirty();
    }

    /** Take down one particular flag, wherever it stands. */
    public void clearStandardAt(String dimension, net.minecraft.core.BlockPos pos) {
        for (Map.Entry<String, List<Standard>> e : new LinkedHashMap<>(standards).entrySet()) {
            List<Standard> left = e.getValue().stream()
                    .filter(st -> !(st.dimension().equals(dimension) && st.x() == pos.getX()
                            && st.y() == pos.getY() && st.z() == pos.getZ()))
                    .toList();
            if (left.size() != e.getValue().size()) {
                if (left.isEmpty()) {
                    standards.remove(e.getKey());
                } else {
                    standards.put(e.getKey(), left);
                }
                setDirty();
                return;
            }
        }
    }

    // --- the bank ---

    public double balanceOf(String id) {
        return banks.getOrDefault(id, 0.0D);
    }

    /**
     * Move money into or out of a faction's bank.
     *
     * <p>Refuses to go negative rather than clamping. A bank that silently absorbs an
     * overdraft has spent money the faction did not have, and the caller — which has usually
     * already taken it off a player — would never learn that the two halves disagreed.</p>
     *
     * @return false if there was not enough, in which case nothing moved
     */
    public boolean adjustBank(String id, double delta) {
        double now = balanceOf(id);
        if (now + delta < 0.0D) {
            return false;
        }
        banks.put(id, now + delta);
        setDirty();
        return true;
    }

    // --- relations ---

    /**
     * Declare how this faction regards another.
     *
     * <p>{@link Relation#NEUTRAL} clears both lists — there is no separate "cancel" to remember.</p>
     */
    public void declare(String id, String otherId, Relation relation) {
        Faction f = factions.get(id);
        if (f == null || id.equals(otherId)) {
            return;
        }
        Set<String> allies = new LinkedHashSet<>(f.allies());
        Set<String> enemies = new LinkedHashSet<>(f.enemies());
        allies.remove(otherId);
        enemies.remove(otherId);
        if (relation == Relation.ALLY) {
            allies.add(otherId);
        } else if (relation == Relation.ENEMY) {
            enemies.add(otherId);
        }
        put(new Faction(f.id(), f.name(), f.tag(), f.leader(), f.members(),
                Set.copyOf(allies), Set.copyOf(enemies), f.peaceful(), f.home()));
    }

    /**
     * How two factions actually stand, resolving both sides' declarations.
     *
     * <p><b>Enemy wins, and one side is enough.</b> Ally requires both to have offered. That
     * asymmetry is the point: you cannot conscript a friend, and you cannot decline to be
     * somebody's target.</p>
     */
    public Relation relation(String a, String b) {
        if (a == null || b == null) {
            return Relation.NEUTRAL;
        }
        if (a.equals(b)) {
            return Relation.ALLY;
        }
        Faction fa = factions.get(a);
        Faction fb = factions.get(b);
        if (fa == null || fb == null) {
            return Relation.NEUTRAL;
        }
        if (fa.enemies().contains(b) || fb.enemies().contains(a)) {
            return Relation.ENEMY;
        }
        if (fa.allies().contains(b) && fb.allies().contains(a)) {
            return Relation.ALLY;
        }
        return Relation.NEUTRAL;
    }

    /** Whether an alliance has been offered but not yet returned — worth telling both sides. */
    public boolean allianceOffered(String from, String to) {
        Faction f = factions.get(from);
        return f != null && f.allies().contains(to) && relation(from, to) != Relation.ALLY;
    }

    // --- land ---

    public Optional<String> ownerOf(String dimension, int x, int z) {
        return Optional.ofNullable(claims.get(key(dimension, x, z)));
    }

    public Optional<Faction> factionAt(String dimension, ChunkPos chunk) {
        return ownerOf(dimension, chunk.x, chunk.z).flatMap(this::byId);
    }

    public int claimCount(String factionId) {
        return (int) claims.values().stream().filter(factionId::equals).count();
    }

    public List<ChunkPos> claimsOf(String factionId, String dimension) {
        String prefix = dimension + "|";
        List<ChunkPos> out = new ArrayList<>();
        claims.forEach((k, id) -> {
            if (id.equals(factionId) && k.startsWith(prefix)) {
                String[] bits = k.split("\\|");
                out.add(new ChunkPos(Integer.parseInt(bits[1]), Integer.parseInt(bits[2])));
            }
        });
        return out;
    }

    public void claim(String dimension, int x, int z, String factionId) {
        claims.put(key(dimension, x, z), factionId);
        setDirty();
    }

    public boolean unclaim(String dimension, int x, int z) {
        if (claims.remove(key(dimension, x, z)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public int unclaimAll(String factionId) {
        int before = claims.size();
        claims.values().removeIf(factionId::equals);
        int removed = before - claims.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Whether this chunk touches land the faction already holds, orthogonally. */
    public boolean touchesOwnLand(String dimension, int x, int z, String factionId) {
        return factionId.equals(claims.get(key(dimension, x + 1, z)))
                || factionId.equals(claims.get(key(dimension, x - 1, z)))
                || factionId.equals(claims.get(key(dimension, x, z + 1)))
                || factionId.equals(claims.get(key(dimension, x, z - 1)));
    }
}

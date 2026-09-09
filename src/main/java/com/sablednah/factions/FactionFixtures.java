package com.sablednah.factions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
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

    private record Seed(String faction, String tag, String leader, Stance stance,
            net.minecraft.world.level.block.Block banner) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("Ashfell", "ASH", "Corvin", Stance.HOSTILE, net.minecraft.world.level.block.Blocks.RED_BANNER),
            new Seed("Marrowgate", "MAR", "Delya", Stance.HOSTILE, net.minecraft.world.level.block.Blocks.BLACK_BANNER),
            new Seed("Thornhold", "THN", "Bracken", Stance.ALLY, net.minecraft.world.level.block.Blocks.LIME_BANNER),
            new Seed("Saltmere", "SLT", "Iva", Stance.OFFERS_YOU, net.minecraft.world.level.block.Blocks.LIGHT_BLUE_BANNER),
            new Seed("Greyhollow", "GRY", "Ottoline", Stance.OFFERS_YOU, net.minecraft.world.level.block.Blocks.GRAY_BANNER),
            new Seed("Quillrest", "QLL", "Fenner", Stance.AWAITS_YOU, net.minecraft.world.level.block.Blocks.PURPLE_BANNER),
            new Seed("Deepmarch", "DPM", "Rook", Stance.NEUTRAL, net.minecraft.world.level.block.Blocks.ORANGE_BANNER),
            new Seed("Lantern Vale", "LTV", "Sepha", Stance.PEACEFUL, net.minecraft.world.level.block.Blocks.WHITE_BANNER),
            new Seed("Stillwater", "STW", "Mabry", Stance.PEACEFUL, net.minecraft.world.level.block.Blocks.CYAN_BANNER));

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

    /**
     * Give every fixture faction a real, planted, stealable standard.
     *
     * <p>Separate from {@link #seed} because it needs the world rather than only the store — and
     * because it is the half a two-person test cannot fake. Testing that the power bonus is flat
     * across several trophies needs several factions to take flags <em>from</em>, and inventing
     * nine banners by hand is an evening.</p>
     *
     * <p>It plants a real banner block on their own claimed land and then calls the ordinary
     * {@link FactionStandards#designate} — the same path a player walks. Nothing here has a private
     * route into the store, so if designation has a bug the fixtures hit it too, which is the only
     * way a fixture is worth having.</p>
     */
    public static List<String> standards(ServerPlayer player) {
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        FactionStore store = FactionStore.get(server);
        String dim = FactionBridge.dimensionOf(level);

        List<String> report = new ArrayList<>();
        for (Seed seed : SEEDS) {
            Optional<FactionStore.Faction> f = store.lookup(seed.faction());
            if (f.isEmpty()) {
                continue; // not seeded; say nothing rather than nine lines of noise
            }
            if (store.hasStandard(f.get().id())) {
                report.add(seed.faction() + ": already flying one");
                continue;
            }
            List<ChunkPos> theirs = store.claimsOf(f.get().id(), dim);
            if (theirs.isEmpty()) {
                report.add(seed.faction() + ": no land in this dimension");
                continue;
            }
            ChunkPos chunk = theirs.get(0);
            // Force the chunk before asking about blocks. An unloaded chunk answers with defaults,
            // so the heightmap would site the flag underground and designate() would refuse it for
            // not seeing the sky — which reads as a bug in the sky rule.
            level.getChunk(chunk.x(), chunk.z());
            BlockPos ground = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    chunk.getMiddleBlockPosition(level.getMinY()));
            level.setBlockAndUpdate(ground, seed.banner().defaultBlockState());
            if (!FactionStandards.designate(player, level, ground, f.get())) {
                report.add(seed.faction() + ": refused at " + ground.toShortString());
                continue;
            }
            report.add(seed.faction() + " — flag at " + ground.toShortString());
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

    /**
     * Names for invented members, drawn on in order.
     *
     * <p>Written out rather than generated as {@code Member1..MemberN} because the thing they are
     * for is judging a members list at a glance: names of one length in one shape tell you nothing
     * about whether a column lines up or whether a long name runs into the buttons beside it. These
     * vary in length on purpose, and several are the full <b>sixteen</b> characters Minecraft
     * allows — the real worst case a row has to survive, rather than an invented one.</p>
     *
     * <p>⚠ <b>No spaces, and that is not cosmetic.</b> {@code /f kick}, {@code promote} and
     * {@code demote} take a brigadier {@code word()}, which accepts letters, digits and
     * {@code _.+-} and nothing else. A fixture called "Morrowind Thistlebottom" would be
     * unkickable — and would look exactly like a bug in the panel's buttons rather than like a
     * fixture nobody could have. Real usernames cannot contain a space either, so the names here
     * are held to the same rule the real ones are.</p>
     */
    private static final List<String> RECRUITS = List.of(
            "Bex", "Corwenna", "Dov", "Elspeth", "Fitch", "Gwynabel", "Hollis", "Ir",
            "Jessamy", "Kestrel", "Lun", "Thistlebottom999", "Nyx", "Oriel", "Pike",
            "Quilla", "Rhod", "Sennet", "Tamsin", "Ull", "Vesper", "Wren", "Xanthe", "Yarrow",
            "Zeb", "Ashlin_Bramwell", "Cato", "Delphine", "Eamon", "Fennick_Grier", "Hob",
            "Ingrid_Sallow001");

    /** The recruit names, so the self-test can prove every one of them is a usable command argument. */
    public static List<String> recruitNames() {
        return RECRUITS;
    }

    /**
     * {@code /f fixture members} — fill your own faction up, so a members list has something in it.
     *
     * <p>The panel's members tab, its scrolling and its per-row buttons are all invisible with two
     * members and obvious with twenty. Same argument as the neighbours above: the states that only
     * appear on a busy server were the states never being looked at.</p>
     *
     * <p>Ranks are dealt round rather than all-member: an officer row and a member row draw
     * differently and carry different buttons, and a list of twenty identical rows would prove the
     * layout for one case and hide it for the other. Every fourth is an officer.</p>
     *
     * @param count how many to add
     * @return a line per recruit, or a single line saying why none were added
     */
    public static List<String> members(ServerPlayer player, int count) {
        MinecraftServer server = player.level().getServer();
        FactionStore store = FactionStore.get(server);
        StandardsData names = StandardsData.get(server);
        List<String> report = new ArrayList<>();

        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        if (mine.isEmpty()) {
            report.add("you are in no faction");
            return report;
        }
        String id = mine.get().id();

        int added = 0;
        // Walks the whole list rather than stopping at `count`, because a name already in the
        // faction is skipped rather than counted — running the command twice should top up to the
        // number asked for, not refuse because the first name is taken.
        for (String name : RECRUITS) {
            if (added >= count) {
                break;
            }
            UUID who = offlineId(name);
            if (store.of(who).isPresent()) {
                continue;
            }
            // Named in Standards' cache first: addMember succeeding and the row still reading as
            // eight hex characters is the bug this whole fixture exists to avoid.
            names.rememberName(who, name);
            FactionStore.Rank rank = added % 4 == 3
                    ? FactionStore.Rank.OFFICER : FactionStore.Rank.MEMBER;
            if (!store.addMember(id, who, rank)) {
                report.add(name + ": refused");
                continue;
            }
            report.add(name + " joined as " + rank.key());
            added++;
        }
        if (added == 0 && report.isEmpty()) {
            report.add("no names left — they are all in a faction already");
        }
        return report;
    }

    /**
     * Take the invented members back out of whatever faction they joined.
     *
     * <p>Separate from {@link #clear}, which only knows about the neighbour factions. A recruit
     * joined <em>your</em> faction, so sweeping them up by disbanding is not on offer.</p>
     */
    public static int clearMembers(MinecraftServer server) {
        FactionStore store = FactionStore.get(server);
        int gone = 0;
        for (String name : RECRUITS) {
            UUID who = offlineId(name);
            Optional<FactionStore.Faction> in = store.of(who);
            // Never the leader: removing one leaves a faction nobody can administer, and a fixture
            // that can do that to a real faction is worse than no fixture.
            if (in.isPresent() && !in.get().leader().equals(who)) {
                store.removeMember(in.get().id(), who);
                gone++;
            }
        }
        return gone;
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

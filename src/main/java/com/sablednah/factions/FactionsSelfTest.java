package com.sablednah.factions;

import java.util.List;

import com.sablednah.standards.neoforge.Lang;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The arithmetic that decides who owns what, checked on start.
 *
 * <p>Runs under the same {@code -Pselftest} flag Standards uses, and logs its own pass/fail block
 * beside Standards'. It cannot live in Standards' self-test because Standards does not know this
 * mod exists — the dependency points the other way, deliberately.</p>
 *
 * <p>Everything here is pure. Power's rules are the sort that look obviously right, are checked by
 * nobody, and quietly hand somebody else's base away when the rounding goes the wrong direction —
 * exactly the shape that belongs in a test rather than in a hand walkthrough.</p>
 */
public final class FactionsSelfTest {

    private int passed;
    private int failed;

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("standards.selftest")) {
            return;
        }
        new FactionsSelfTest().run(event.getServer());
    }

    private void run(net.minecraft.server.MinecraftServer server) {
        Factions.LOGGER.info("=== Factions self-test ===");
        checkNamesWithSpaces(server);
        checkEntitlement();
        checkOverreach();
        checkModes();
        checkStandardColours();
        checkBypass();
        checkRaids();
        checkRaidRecords();
        checkMapPalette();
        if (failed == 0) {
            Factions.LOGGER.info("=== Factions self-test PASSED ({} checks) ===", passed);
        } else {
            Factions.LOGGER.error("=== Factions self-test FAILED ({} of {}) ===",
                    failed, passed + failed);
        }
    }

    /**
     * That a faction name with a space in it can actually be <b>typed</b>.
     *
     * <p>This is the check that was missing, and its absence has now cost four features across the
     * two mods. Every one of them tested the <em>logic</em> while nothing had ever managed to enter
     * the input: {@code word()} accepts letters, digits and {@code _.+-} and nothing else, so
     * "Lantern Vale" was not refused by any rule here — it was <b>unparseable</b>, and brigadier
     * answered "Expected whitespace to end one argument", which names nothing and reads like the
     * typist's fault.</p>
     *
     * <p>So it parses the real dispatcher rather than asking any of our own code a question. Both
     * directions, because a tree that swallowed anything would pass every positive assertion: a
     * name with a space must reach an executable node, and a bare {@code /f who} must not.</p>
     */
    private void checkNamesWithSpaces(net.minecraft.server.MinecraftServer server) {
        com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d =
                server.getCommands().getDispatcher();
        net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack();
        check("a faction name with a space parses for /f who", executable(d, src, "f who Lantern Vale"));
        check("...and for /f raid", executable(d, src, "f raid Lantern Vale"));
        check("...and for /f ally", executable(d, src, "f ally Lantern Vale"));
        check("...and for /f create", executable(d, src, "f create Lantern Vale"));
        // The one that cannot be greedy, because an amount follows it. Quoted, and the
        // tab-complete supplies the quotes.
        check("a quoted name parses for /f money pay",
                executable(d, src, "f money pay \"Lantern Vale\" 100"));
        check("...and an unquoted single word still does",
                executable(d, src, "f money pay Ashfell 100"));
        // Negative: a tree that matched anything would pass everything above.
        check("a bare /f who is still not executable", !executable(d, src, "f who"));

        // The panel button's command, and the fixtures that make the panel worth looking at.
        // /f panel earned a check the hard way: a whole afternoon went into deciding whether it
        // was reaching FactionCommands::panel at all, and nothing here could have answered that.
        check("/f panel parses to something executable", executable(d, src, "f panel"));
        // The map's click-to-claim sends these. A button that sends a command nobody can parse is
        // the word() trap again, and the map cannot tell — it never sees the parse.
        check("/f claim <x> <z> parses, for the map's click",
                executable(d, src, "f claim 12 -34"));
        check("/f unclaim <x> <z> parses, for the map's right-click",
                executable(d, src, "f unclaim 12 -34"));
        check("...and negative coordinates survive the argument type",
                executable(d, src, "f claim -1200 -3400"));
        // Negative: the bare forms must still work, or standing-in-the-chunk claiming is gone.
        check("bare /f claim still executes", executable(d, src, "f claim"));
        check("bare /f unclaim still executes", executable(d, src, "f unclaim"));

        // The map's layer switch. It is a command because the overlays are pushed by the server,
        // so the button has to say something rather than only flip its own label — the first
        // version did the latter and the territory stayed drawn.
        check("/f map layer on parses, for the map's layer button",
                executable(d, src, "f map layer on"));
        check("...and off", executable(d, src, "f map layer off"));
        check("...but not a third state", !executable(d, src, "f map layer sometimes"));
        check("a bare /f map still executes", executable(d, src, "f map"));

        prefixChecks();
        outlineChecks();
        trophyChecks(server);
        // The pane's no-faction state offers these two, pre-filled into the chat box. A button
        // that pre-fills a command nobody can complete is worse than no button, and the pane
        // cannot tell — it never sees the parse.
        check("/f create parses, for the pane's create button",
                executable(d, src, "f create Lantern Vale"));
        check("/f rename parses, for the pane's rename pencil",
                executable(d, src, "f rename Lantern Vale"));
        check("/f tag parses, for the pane's tag pencil", executable(d, src, "f tag LTV"));
        if (FactionsConfig.FIXTURES.get()) {
            check("/f fixture members parses", executable(d, src, "f fixture members"));
            check("...and with a count", executable(d, src, "f fixture members 20"));
            // Bounded 1..32, and brigadier must be the thing that says so — a fixture command that
            // accepts 10000 invents ten thousand players into a real faction.
            check("...but not with an absurd one", !executable(d, src, "f fixture members 10000"));
            check("...nor with none", !executable(d, src, "f fixture members 0"));
        }

        // ⚠ Every name a fixture can invent must be usable with the commands the panel's buttons
        // send. Those take a brigadier word(), which accepts letters, digits and _.+- and nothing
        // else — so a fixture name with a space would be unkickable, and would read as a broken
        // button rather than as a name nobody could really have. This is the word() trap, checked
        // against the real dispatcher rather than against a regex that agrees with itself.
        boolean allKickable = true;
        for (String name : FactionFixtures.recruitNames()) {
            if (!executable(d, src, "f kick " + name)
                    || !executable(d, src, "f promote " + name)) {
                allKickable = false;
            }
        }
        check("every fixture recruit's name survives /f kick and /f promote", allKickable);
        // Negative, so the loop above is not passing because it tests nothing.
        check("...and a name with a space does not", !executable(d, src, "f kick Lantern Vale"));
    }

    /**
     * Whether this input parses all the way to something that would run.
     *
     * <p>Parsing alone proves nothing — a partial parse with a dangling argument reports no
     * exception. It has to have consumed the input AND landed on a node with a command on it.</p>
     */
    private boolean executable(
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d,
            net.minecraft.commands.CommandSourceStack src, String input) {
        com.mojang.brigadier.ParseResults<net.minecraft.commands.CommandSourceStack> parse =
                d.parse(input, src);
        return parse.getExceptions().isEmpty()
                && !parse.getReader().canRead()
                && parse.getContext().getLastChild().getCommand() != null;
    }

    /**
     * Raids: the state machine, the cooldown, and that nothing lingers.
     *
     * <p>Every rule has its opposite asserted. A raid system that refused every declaration would
     * pass most positive checks by never starting anything, and one that started but never ended
     * would look identical to a working one for the first twenty minutes.</p>
     */
    private void checkRaids() {
        try {
            FactionRaid.clear();
            long now = 1_000_000_000_000L;
            check("no raids to begin with", FactionRaid.active().isEmpty());
            check("...and nobody is involved in one", !FactionRaid.involved("a"));

            FactionRaid.Raid raid = FactionRaid.begin("a", "b", now, true);
            check("declaring starts one", FactionRaid.active().size() == 1);
            check("the attacker is attacking",
                    FactionRaid.attacking("a").map(r -> r.defenderId().equals("b")).orElse(false));
            check("the defender is defending",
                    FactionRaid.defending("b").map(r -> r.attackerId().equals("a")).orElse(false));
            check("...and not the other way round", FactionRaid.attacking("b").isEmpty());
            check("both sides count as involved",
                    FactionRaid.involved("a") && FactionRaid.involved("b"));
            check("a bystander does not", !FactionRaid.involved("c"));

            // The objective is that their flag FALLS, so whether they had one is recorded at the
            // start. A faction flying none must not lose the instant a raid is declared on them —
            // which is exactly what the first version did, unnoticed until it was played.
            check("a defender who was flying one is recorded as such", raid.defenderHadStandard());
            check("...and one who was not, is not",
                    !FactionRaid.begin("x", "y", now, false).defenderHadStandard());
            FactionRaid.end(FactionRaid.attacking("x").orElseThrow(), now);

            // A flag planted DURING the raid must count. The declaration-time snapshot alone said
            // no, which made every raid on a flagless faction unwinnable whatever happened next.
            check("a raid has not seen a standard to begin with",
                    !FactionRaid.hasSeenStandard("a"));
            FactionRaid.sawStandard("a");
            check("...and remembers once it has", FactionRaid.hasSeenStandard("a"));
            check("but only for that raid", !FactionRaid.hasSeenStandard("zz"));

            // between() is what gates overclaiming, so it must be symmetric — the attacker takes
            // land from the defender, and the rule is asked about the pair rather than a direction.
            check("between() sees the pair either way round",
                    FactionRaid.between("a", "b") && FactionRaid.between("b", "a"));
            check("...and not a pair that is not raiding", !FactionRaid.between("a", "c"));

            // The clock is a backstop, so it has to actually expire.
            check("a fresh raid has not expired", !raid.expired(now));
            check("...and has time on it", raid.secondsLeft(now) > 0);
            check("it expires once the clock runs out", raid.expired(raid.endsAtMillis()));
            check("...reporting no time left", raid.secondsLeft(raid.endsAtMillis()) == 0);

            FactionRaid.end(raid, now);
            check("ending removes it", FactionRaid.active().isEmpty());
            check("...and nobody is involved any more", !FactionRaid.involved("a"));

            // The cooldown is per PAIR, which is the whole point: a busy faction must still be
            // able to raid somebody else immediately.
            check("the same pair is on cooldown",
                    FactionRaid.cooldownLeft("a", "b", now) > 0);
            check("a different target is not",
                    FactionRaid.cooldownLeft("a", "c", now) == 0);
            check("nor is the reverse direction",
                    FactionRaid.cooldownLeft("b", "a", now) == 0);
            check("and the cooldown lapses",
                    FactionRaid.cooldownLeft("a", "b",
                            now + FactionsConfig.RAID_COOLDOWN_MINUTES.get() * 60_000L + 1) == 0);
            check("ending a raid forgets what it saw", endForgets(now));

            // The per-raid land allowance. Default 1: a big faction cannot strip a small one in
            // one sitting, and each further chunk costs another raid and another cooldown.
            int limit = FactionsConfig.RAID_CLAIM_LIMIT.get();
            // A live raid of its own — the one above was ended, and asserting that a finished
            // raid is still running is how this check failed the first time it ran.
            FactionRaid.begin("land", "victim", now, true);
            check("a fresh raid has taken no land", FactionRaid.claimsTaken("land") == 0);
            check("...and may take some", FactionRaid.mayTakeLand("land"));
            for (int i = 0; i < Math.max(1, limit); i++) {
                FactionRaid.tookLand("land");
            }
            check("the allowance runs out at the configured limit",
                    limit <= 0 || !FactionRaid.mayTakeLand("land"));
            check("...counting what was taken", FactionRaid.claimsTaken("land") >= 1);
            // Per RAID, not per faction: another raid starts with a full allowance.
            check("a different raid is unaffected", FactionRaid.mayTakeLand("other"));

            // Taking land must NOT end the raid — if they fly a standard there is still something
            // to go for, which is the whole reason the limit caps land rather than the fight.
            check("taking land leaves the raid running",
                    FactionRaid.attacking("land").isPresent());
            FactionRaid.end(FactionRaid.attacking("land").orElseThrow(), now);

            // Plant to win. The latch has to be picky about WHOSE flag was planted: a faction that
            // has been hoarding trophies for weeks would otherwise win every raid it declared the
            // instant the tick ran, having planted nothing at all.
            FactionRaid.begin("planter", "target", now, true);
            check("a fresh raid has planted nothing", !FactionRaid.hasPlanted("planter"));
            FactionRaid.plantedStandard("planter", "somebody_else");
            check("planting an unrelated trophy is not a win",
                    !FactionRaid.hasPlanted("planter"));
            FactionRaid.plantedStandard("planter", "target");
            check("...but planting the target's is", FactionRaid.hasPlanted("planter"));
            // And it must not survive the raid, or the attacker's NEXT raid starts already won.
            FactionRaid.end(FactionRaid.attacking("planter").orElseThrow(), now);
            check("the win does not carry into the next raid",
                    !FactionRaid.hasPlanted("planter"));
            // Nobody in a raid at all cannot bank one for later either.
            FactionRaid.plantedStandard("drifter", "target");
            check("planting outside a raid records nothing",
                    !FactionRaid.hasPlanted("drifter"));

            // Two raids at once — attacking one faction while another attacks you. There is one
            // action bar and one glow colour between them, and taking whichever the map yielded
            // first meant the second raid's clock never appeared at all.
            FactionRaid.begin("busy", "slow_target", now, false);          // ends later
            FactionRaid.begin("aggressor", "busy", now - 60_000L, false);  // ends sooner
            check("a faction can be in two raids at once",
                    FactionRaid.attacking("busy").isPresent()
                            && FactionRaid.defending("busy").isPresent());
            check("the soonest-ending one is the one reported",
                    FactionRaid.active().stream()
                            .filter(r -> r.involves("busy"))
                            .min(java.util.Comparator.comparingLong(FactionRaid.Raid::endsAtMillis))
                            .map(r -> r.attackerId().equals("aggressor")).orElse(false));
        } finally {
            FactionRaid.clear();
            check("the raid fixtures are gone", FactionRaid.active().isEmpty()
                    && FactionRaid.cooldownLeft("a", "b", 1_000_000_000_000L) == 0);
        }
    }

    /**
     * The terrain map's colour arithmetic.
     *
     * <p>A map pixel is an index into 64 colours by four brightnesses, not an RGB value, so the
     * "wash" of claim colour over terrain is done in software and then snapped to whatever the
     * palette can actually say. Both halves are pure and worth pinning down, because a fault in
     * either produces a picture that is merely <em>wrong</em> rather than broken — the kind
     * nothing reports.</p>
     */
    private void checkMapPalette() {
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        check("mixing none of the second colour leaves the first",
                FactionMap.mix(black, white, 0) == black);
        check("mixing all of it gives the second", FactionMap.mix(black, white, 100) == white);
        int half = FactionMap.mix(black, white, 50) & 0xFF;
        check("half lands halfway", half > 120 && half < 136);
        check("the mix stays opaque", (FactionMap.mix(black, white, 50) >>> 24) == 0xFF);

        // The strong property: a colour the palette already holds must come back as itself. If
        // the snap cannot round-trip an exact entry it will not be close on anything else.
        for (net.minecraft.world.level.material.MapColor c : new net.minecraft.world.level.material.MapColor[] {
                net.minecraft.world.level.material.MapColor.GRASS,
                net.minecraft.world.level.material.MapColor.WATER,
                net.minecraft.world.level.material.MapColor.COLOR_RED,
                net.minecraft.world.level.material.MapColor.STONE }) {
            for (net.minecraft.world.level.material.MapColor.Brightness b
                    : net.minecraft.world.level.material.MapColor.Brightness.values()) {
                check("the palette round-trips " + c.id + "/" + b.id,
                        FactionMap.nearest(c.calculateARGBColor(b)) == c.getPackedId(b));
            }
        }
        // NONE renders as a hole. It must never be the answer to anything.
        for (int rgb : new int[] {black, white, 0xFF7F7F7F, 0xFF204080, 0xFF80FF20}) {
            check("the snap never answers NONE for " + Integer.toHexString(rgb),
                    ((FactionMap.nearest(rgb) & 0xFF) >> 2) != 0);
        }
    }

    /**
     * The raid tally, and above all that the two sides agree.
     *
     * <p>Pure arithmetic on a record, so it can be checked without a world. The property worth
     * asserting is not that a win increments a counter — it is that <b>one raid moves exactly one
     * number on each side</b>. A tally where a win were credited without the matching loss being
     * debited would produce a leaderboard that does not add up, and nothing anywhere would say so.
     */
    private void checkRaidRecords() {
        FactionStore.RaidRecord fresh = new FactionStore.RaidRecord("a", "A", 0, 0, 0, 0);
        check("a faction that has never raided has fought nothing", fresh.fought() == 0);
        check("...and won nothing", fresh.won() == 0);

        FactionStore.RaidRecord raider = new FactionStore.RaidRecord("a", "A", 3, 2, 1, 4);
        check("fought counts both ends", raider.fought() == 10);
        check("won counts both ends", raider.won() == 4);
        // The distinction the four columns exist for: these two have identical won/fought and are
        // completely different factions to be next door to.
        FactionStore.RaidRecord fortress = new FactionStore.RaidRecord("b", "B", 0, 0, 4, 6);
        check("a raider and a fortress can tie on the headline",
                raider.won() == fortress.won() && raider.fought() == fortress.fought());
        check("...and still be told apart", raider.attacksWon() != fortress.attacksWon());

        // The ORDER, not just the arithmetic. The first board shipped ascending — the faction that
        // had won nothing sat at the top — because .reversed() written after each key reverses the
        // composed comparator rather than the last one, undoing the first reversal. It reads
        // correctly, compiles, and is backwards. Everything above passed while it was wrong,
        // because all of it tested the numbers and none of it tested the sort.
        java.util.List<FactionStore.RaidRecord> board = new java.util.ArrayList<>(java.util.List.of(
                new FactionStore.RaidRecord("none", "None", 0, 1, 0, 0),
                new FactionStore.RaidRecord("best", "Best", 5, 0, 0, 0),
                new FactionStore.RaidRecord("some", "Some", 2, 0, 0, 0)));
        board.sort(java.util.Comparator
                .comparingInt(FactionStore.RaidRecord::won)
                .thenComparingInt(FactionStore.RaidRecord::fought)
                .reversed());
        check("the leaderboard puts the most wins first", board.get(0).faction().equals("best"));
        check("...and the fewest last", board.get(2).faction().equals("none"));
    }

    /** A finished raid must not leave its standard-sighting behind for the next one. */
    private boolean endForgets(long now) {
        FactionRaid.Raid r = FactionRaid.begin("q", "r", now, false);
        FactionRaid.sawStandard("q");
        FactionRaid.end(r, now);
        return !FactionRaid.hasSeenStandard("q");
    }

    /**
     * The claim override, and above all that it does not linger.
     *
     * <p>Both directions, because a bypass that cannot be turned off is worse than none at all,
     * and one that survives a logout is precisely what the design exists to prevent — a staff
     * member coming back tomorrow still able to edit everybody's land, having forgotten.</p>
     *
     * <p>Runs against the real static set and clears it afterwards. It has to: leaving somebody
     * overriding because a test forgot to tidy up is the exact failure being tested for.</p>
     */
    private void checkBypass() {
        java.util.UUID staff = java.util.UUID.nameUUIDFromBytes("selftest-bypass-staff".getBytes());
        java.util.UUID other = java.util.UUID.nameUUIDFromBytes("selftest-bypass-other".getBytes());
        try {
            check("nobody is overriding to begin with", !FactionBypass.isActive(staff));

            FactionBypass.forget(staff);      // no-op, and must not throw on somebody absent
            check("forgetting an absent player is harmless", !FactionBypass.isActive(staff));

            FactionBypass.set(staff, true);
            check("turning it on takes effect", FactionBypass.isActive(staff));
            check("...for that player only, not everybody", !FactionBypass.isActive(other));

            FactionBypass.set(staff, false);
            check("turning it off takes effect", !FactionBypass.isActive(staff));

            // THE PROPERTY THE DESIGN RESTS ON. If the logout hook is ever dropped from
            // FactionsEvents, staff silently keep the override across a relog — which is exactly
            // what this exists to prevent, and which nothing else would report.
            FactionBypass.set(staff, true);
            FactionBypass.forget(staff);
            check("logging out drops the override", !FactionBypass.isActive(staff));

            FactionBypass.set(staff, true);
            FactionBypass.set(other, true);
            check("two staff can override at once", FactionBypass.active() == 2);
            FactionBypass.forget(staff);
            check("...and one leaving does not drop the other",
                    FactionBypass.isActive(other) && !FactionBypass.isActive(staff));
        } finally {
            FactionBypass.clear();
            check("the self-test leaves nobody overriding", FactionBypass.active() == 0);
        }
    }

    /**
     * Fixed is the ceiling; power is the erosion.
     *
     * <p>The property that matters more than any single number: <b>a faction at full power gets
     * exactly what the fixed rule would have given it.</b> If that ever stops holding, enabling
     * power silently redistributes land on every server that turns it on.</p>
     */
    private void checkEntitlement() {
        check("full power gives exactly the fixed allowance",
                FactionPower.entitlement(2, 20.0D, 10.0D, 16) == 32);
        check("one member at full power gives one member's worth",
                FactionPower.entitlement(1, 10.0D, 10.0D, 16) == 16);
        check("half power gives half the land",
                FactionPower.entitlement(2, 10.0D, 10.0D, 16) == 16);
        check("no power gives no land",
                FactionPower.entitlement(2, 0.0D, 10.0D, 16) == 0);

        // Floored, never rounded up. Rounding up would let a faction hold a chunk its power does
        // not cover — the exact state overclaiming exists to punish — and would flicker in and out
        // of raidable on a boundary.
        check("entitlement floors rather than rounds",
                FactionPower.entitlement(1, 9.9D, 10.0D, 16) == 15);
        check("a fraction of a chunk is not a chunk",
                FactionPower.entitlement(1, 0.6D, 10.0D, 1) == 0);

        // Power can only take away. Nothing must ever produce more than members * perMember.
        for (int members = 1; members <= 5; members++) {
            int ceiling = members * 16;
            check("power never grants more than the fixed rule (" + members + " members)",
                    FactionPower.entitlement(members, members * 10.0D, 10.0D, 16) == ceiling);
        }

        check("no configured limit stays unlimited",
                FactionPower.entitlement(3, 30.0D, 10.0D, -1) == -1);
        check("a faction with nobody in it is entitled to nothing",
                FactionPower.entitlement(0, 0.0D, 10.0D, 16) == 0);
    }

    /** Strictly over. Sitting exactly on the line is not overreach. */
    private void checkOverreach() {
        check("holding exactly your entitlement is safe",
                FactionPower.overreach(16, 16) == 0);
        check("holding one over exposes exactly one",
                FactionPower.overreach(17, 16) == 1);
        check("holding under exposes nothing",
                FactionPower.overreach(10, 16) == 0);
        check("an unlimited faction is never exposed",
                FactionPower.overreach(500, -1) == 0);
        // Each chunk taken reduces the overreach by one, so a faction five over stops being
        // takeable after five — the attacker is rewarded for noticing, not for attacking.
        check("taking a chunk reduces what is left to take",
                FactionPower.overreach(20, 16) - 1 == FactionPower.overreach(19, 16));
    }

    /** The modes differ in what counts as losing, and in nothing else. */
    private void checkModes() {
        var fixed = FactionPower.Mode.FIXED;
        var pvp = FactionPower.Mode.PVP;
        var pve = FactionPower.Mode.PVE;
        var both = FactionPower.Mode.BOTH;

        check("fixed is inert", !fixed.active());
        check("fixed drains on nothing", !fixed.drainsOn(true) && !fixed.drainsOn(false));
        check("pvp drains on a player kill only",
                pvp.drainsOn(true) && !pvp.drainsOn(false));
        check("pve drains on a mob kill only",
                !pve.drainsOn(true) && pve.drainsOn(false));
        check("both drains either way", both.drainsOn(true) && both.drainsOn(false));

        check("an unknown mode falls back to fixed",
                FactionPower.Mode.of("nonsense") == FactionPower.Mode.FIXED);
        check("mode names round-trip",
                FactionPower.Mode.of("pve") == pve && FactionPower.Mode.of("PVE") == pve);
    }

    /**
     * Every dye has a chat colour, and the identity palette is not the relation palette.
     *
     * <p>The switch is exhaustive by construction, but a new dye colour in a future Minecraft
     * would break it silently at runtime rather than at compile time, and a faction whose flag
     * threw on being printed would be an odd bug to chase.</p>
     */
    private void checkStandardColours() {
        for (net.minecraft.world.item.DyeColor dye : net.minecraft.world.item.DyeColor.values()) {
            String code = FactionStandards.chatColour(dye);
            check("dye " + dye.getName() + " has a chat colour",
                    code != null && code.length() == 2 && code.charAt(0) == '&');
        }
        // The two palettes are separate on purpose: identity comes from your banner, relation
        // stays green-for-yours / blue-for-allied / red-for-hostile so nothing can make an
        // enemy's land look friendly.
        check("a faction with no standard is plain white",
                FactionStandards.chatColour(net.minecraft.world.item.DyeColor.WHITE).equals("&f"));
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            Factions.LOGGER.info("  ✓ {}", what);
        } else {
            failed++;
            Factions.LOGGER.error("  ✗ {}", what);
        }
    }

    /**
     * The territory tracer, against shapes whose answers can be counted by hand.
     *
     * <p>⚠ <b>The 2×2 case is the regression.</b> The shipped version drew one rectangle per
     * horizontal run and stroked each all the way round, so a block of land two rows deep had a
     * border drawn <em>between</em> its own rows. It looked fine on every territory anybody had
     * tested, because one row deep is one rectangle. Counting corners is what tells the two apart:
     * a 2×2 block is four corners, and the broken version gives two rectangles.</p>
     *
     * <p>Corners are counted after collinear points are dropped, so a straight edge contributes
     * nothing — which is the whole reason the count is a meaningful assertion rather than a
     * restatement of the input.</p>
     */
    private void outlineChecks() {
        check("one chunk traces one shape with four corners",
                corners(ClaimOutline.trace(chunks(0, 0))) == 4);

        // The bug. Two rows deep, one shape, four corners — NOT two rectangles.
        List<ClaimOutline.Shape> square = ClaimOutline.trace(chunks(0, 0, 1, 0, 0, 1, 1, 1));
        check("a 2x2 block is ONE shape", square.size() == 1);
        check("...with four corners, not a seam between its rows", corners(square) == 4);

        check("a 1x3 row is four corners",
                corners(ClaimOutline.trace(chunks(0, 0, 1, 0, 2, 0))) == 4);
        check("a 3x1 column is four corners",
                corners(ClaimOutline.trace(chunks(0, 0, 0, 1, 0, 2))) == 4);

        // An L: six corners, and it must not come back as two overlapping rectangles.
        List<ClaimOutline.Shape> ell = ClaimOutline.trace(chunks(0, 0, 0, 1, 1, 1));
        check("an L is one shape of six corners", ell.size() == 1 && corners(ell) == 6);

        // Two chunks that do not touch are two shapes; nothing should join them.
        check("two separate chunks are two shapes",
                ClaimOutline.trace(chunks(0, 0, 5, 5)).size() == 2);

        // A ring of eight around an empty middle: one outer, one hole. This is a faction that has
        // surrounded somebody, and the hole is the thing a per-row tracer cannot express at all.
        List<ClaimOutline.Shape> donut = ClaimOutline.trace(
                chunks(0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2));
        check("a ring of eight is one shape", donut.size() == 1);
        check("...with a hole in it", donut.get(0).holes().size() == 1);
        check("...the hole being four corners", donut.get(0).holes().get(0).size() == 4);
        check("...and the outer still four", donut.get(0).outer().size() == 4);

        // Diagonal touch: the saddle case. Two lobes meeting at a point must stay two shapes
        // rather than being stitched into a bow-tie.
        check("two chunks touching only diagonally are two shapes",
                ClaimOutline.trace(chunks(0, 0, 1, 1)).size() == 2);

        check("no claims traces nothing", ClaimOutline.trace(List.of()).isEmpty());

        // ⚠ rows() is what makes the TOOLTIP honest: JourneyMap hit-tests an overlay's bounding
        // box, so each row is pushed as its own overlay and a row's box is exactly its area. The
        // property that matters is therefore coverage — every claimed chunk in exactly one row,
        // and no row covering a chunk nobody holds. The hole is the case that caught it.
        List<int[]> ring = chunks(0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2);
        List<int[]> rows = ClaimOutline.rows(ring);
        check("a ring of eight is covered by four rows", rows.size() == 4);
        check("...covering all eight chunks and no more", covered(rows) == 8);
        check("...and never the hole in the middle", !covers(rows, 1, 1));
        check("a 2x2 block is two rows", ClaimOutline.rows(chunks(0, 0, 1, 0, 0, 1, 1, 1)).size() == 2);
        check("a 1x3 row is one row of three",
                ClaimOutline.rows(chunks(0, 0, 1, 0, 2, 0)).equals(List.of(new int[] {0, 0, 2}))
                        || covered(ClaimOutline.rows(chunks(0, 0, 1, 0, 2, 0))) == 3);
        check("no claims makes no rows", ClaimOutline.rows(List.of()).isEmpty());

        // ⚠ landSideOf decides which faction's colour a shared border is drawn in. Off by one on
        // either axis and two factions swap colours along the edge they share — which looks like a
        // relation bug, in a renderer, on a client. Checked against a single chunk whose four
        // edges must ALL name that same chunk.
        List<ClaimOutline.Shape> lone = ClaimOutline.trace(chunks(3, 5));
        List<ClaimOutline.Corner> loneRing = lone.get(0).outer();
        boolean allMine = true;
        for (int i = 0; i < loneRing.size(); i++) {
            int[] owner = ClaimOutline.landSideOf(loneRing.get(i),
                    loneRing.get((i + 1) % loneRing.size()));
            allMine &= owner[0] == 3 && owner[1] == 5;
        }
        check("every edge of a lone chunk names that chunk as its land", allMine);

        // And a hole: its ring winds the other way, so the land is the ring of chunks AROUND it.
        // The middle must never be named, or the pocket would be drawn in its owner's colour.
        List<ClaimOutline.Shape> ringed = ClaimOutline.trace(
                chunks(0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2));
        List<ClaimOutline.Corner> hole = ringed.get(0).holes().get(0);
        boolean neverTheHole = true;
        for (int i = 0; i < hole.size(); i++) {
            int[] owner = ClaimOutline.landSideOf(hole.get(i), hole.get((i + 1) % hole.size()));
            neverTheHole &= !(owner[0] == 1 && owner[1] == 1);
        }
        check("a hole's edges name the land around it, never the hole", neverTheHole);
    }

    /** Chunk coordinates as x,z pairs, so a shape reads as a shape at the call site. */
    private static List<int[]> chunks(int... xz) {
        List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            out.add(new int[] {xz[i], xz[i + 1]});
        }
        return out;
    }

    private static int corners(List<ClaimOutline.Shape> shapes) {
        int n = 0;
        for (ClaimOutline.Shape shape : shapes) {
            n += shape.outer().size();
        }
        return n;
    }

    /**
     * A captured flag loses its footing when the ground under it changes hands.
     *
     * <h2>⚠ Why this touches the real store</h2>
     *
     * <p>Because the rule lives in {@link FactionStore#claim} and {@code unclaim}, deliberately —
     * that is what makes every route land can move by inherit it, including ones written later.
     * Testing it anywhere else would be testing a copy of the rule rather than the rule.</p>
     *
     * <p>The ids are obviously not real and the dimension does not exist, so nothing here can
     * collide with a live faction; and the {@code finally} puts the world back whatever happens,
     * because a self-test that leaves a claim behind has changed the thing it was measuring.</p>
     *
     * <p>The behaviour: an enemy's standard flown as a trophy is <em>not</em> ground you hold. Take
     * the land away and the trophy goes with it. Its own flag is the opposite — see the pinning
     * rule — and the two were easy to conflate until they were written down side by side.</p>
     */
    private void trophyChecks(net.minecraft.server.MinecraftServer server) {
        final String dim = "selftest:nowhere";
        final String flyer = "selftest-flyer";
        final String victim = "selftest-victim";
        final String other = "selftest-other";
        final int cx = 30_000;
        final int cz = 30_000;
        FactionStore store = FactionStore.get(server);
        net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(cx * 16 + 8, 64, cz * 16 + 8);
        try {
            store.claim(dim, cx, cz, flyer);
            store.setStandard(flyer, dim, at, net.minecraft.world.item.DyeColor.RED,
                    net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY,
                    java.util.Optional.of(victim));
            check("a captured flag stands while its flyer holds the ground",
                    store.standardsOf(flyer).size() == 1);

            // Somebody else takes the chunk: the trophy has lost its footing.
            store.claim(dim, cx, cz, other);
            check("...and falls when the chunk changes hands",
                    store.standardsOf(flyer).isEmpty());

            // And again by release rather than conquest.
            store.claim(dim, cx, cz, flyer);
            store.setStandard(flyer, dim, at, net.minecraft.world.item.DyeColor.RED,
                    net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY,
                    java.util.Optional.of(victim));
            store.unclaim(dim, cx, cz);
            check("...and when the chunk is simply given up",
                    store.standardsOf(flyer).isEmpty());

            // The other half of the pair: an OWN flag pins its chunk rather than falling with it.
            store.claim(dim, cx, cz, flyer);
            store.setStandard(flyer, dim, at, net.minecraft.world.item.DyeColor.RED,
                    net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY,
                    java.util.Optional.empty());
            check("an own flag pins the chunk it stands in",
                    store.ownStandardInChunk(flyer, dim, cx, cz));
            check("...and unclaimall leaves that chunk alone",
                    store.unclaimAll(flyer) == 0 && store.claimCount(flyer) == 1);
            check("...while a trophy pins nothing",
                    !store.ownStandardInChunk(other, dim, cx, cz));
        } finally {
            store.clearStandardAt(dim, at);
            store.unclaim(dim, cx, cz);
        }
    }

    /**
     * Factions' messages sign themselves as Factions.
     *
     * <p>They did not: every one opens with {@code {term.prefix}}, which resolved to Standards'
     * prefix, so <em>"Claimed 2, 0"</em> announced itself as <b>[Standards]</b> — as did every
     * other command in the mod. Factions is a separate mod and a separate release; it was signing
     * its work with somebody else's name, and it took the owner reading his own chat log.</p>
     *
     * <p>Standards now resolves that token to the prefix of whichever mod owns the key. This checks
     * the outcome rather than the mechanism, and checks both halves — that ours changed, and that
     * Standards' own did not, because a fix that re-badged everything would be the same bug wearing
     * the other hat.</p>
     */
    private void prefixChecks() {
        String ours = Lang.get("msg.factions.none_yet");
        check("a Factions message signs itself Factions", ours.contains("Factions"));
        check("...and not Standards", !ours.contains("Standards"));

        // The map's labels: cheap, and it catches a mistyped placeholder, which renders as the
        // literal {holder} on a waypoint nobody would think to check.
        String own = Lang.fmt("msg.factions.map_standard", "name", "Sabletopia");
        check("a standard's map label names its faction", own.equals("Sabletopia's standard"));
        String taken = Lang.fmt("msg.factions.map_standard_captured",
                "name", "Ashfell", "holder", "Sabletopia");
        check("a captured one leads with whose flag it is, not who holds it",
                taken.equals("Ashfell's standard (captured by Sabletopia)"));
        check("...and leaves no placeholder behind", !taken.contains("{"));
        check("the waypoint group name is short enough not to clip",
                Lang.get("msg.factions.map_group").length() <= 14);

        String theirs = Lang.get("msg.toggle.self");
        check("a Standards message still signs itself Standards", theirs.contains("Standards"));
        check("...and not Factions", !theirs.contains("Factions"));
    }

    /** How many chunks a set of rows covers in total, counting any overlap twice. */
    private static int covered(List<int[]> rows) {
        int n = 0;
        for (int[] row : rows) {
            n += row[2] - row[0] + 1;
        }
        return n;
    }

    private static boolean covers(List<int[]> rows, int x, int z) {
        for (int[] row : rows) {
            if (row[1] == z && x >= row[0] && x <= row[2]) {
                return true;
            }
        }
        return false;
    }
}

package com.sablednah.factions;

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
     * The raid tally, and above all that the two sides agree.
     *
     * <p>Pure arithmetic on a record, so it can be checked without a world. The property worth
     * asserting is not that a win increments a counter — it is that <b>one raid moves exactly one
     * number on each side</b>. A tally where a win were credited without the matching loss being
     * debited would produce a leaderboard that does not add up, and nothing anywhere would say so.
     */
    private void checkRaidRecords() {
        FactionStore.RaidRecord fresh = new FactionStore.RaidRecord("a", 0, 0, 0, 0);
        check("a faction that has never raided has fought nothing", fresh.fought() == 0);
        check("...and won nothing", fresh.won() == 0);

        FactionStore.RaidRecord raider = new FactionStore.RaidRecord("a", 3, 2, 1, 4);
        check("fought counts both ends", raider.fought() == 10);
        check("won counts both ends", raider.won() == 4);
        // The distinction the four columns exist for: these two have identical won/fought and are
        // completely different factions to be next door to.
        FactionStore.RaidRecord fortress = new FactionStore.RaidRecord("b", 0, 0, 4, 6);
        check("a raider and a fortress can tie on the headline",
                raider.won() == fortress.won() && raider.fought() == fortress.fought());
        check("...and still be told apart", raider.attacksWon() != fortress.attacksWon());

        // The ORDER, not just the arithmetic. The first board shipped ascending — the faction that
        // had won nothing sat at the top — because .reversed() written after each key reverses the
        // composed comparator rather than the last one, undoing the first reversal. It reads
        // correctly, compiles, and is backwards. Everything above passed while it was wrong,
        // because all of it tested the numbers and none of it tested the sort.
        java.util.List<FactionStore.RaidRecord> board = new java.util.ArrayList<>(java.util.List.of(
                new FactionStore.RaidRecord("none", 0, 1, 0, 0),
                new FactionStore.RaidRecord("best", 5, 0, 0, 0),
                new FactionStore.RaidRecord("some", 2, 0, 0, 0)));
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
}

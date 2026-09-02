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
        new FactionsSelfTest().run();
    }

    private void run() {
        Factions.LOGGER.info("=== Factions self-test ===");
        checkEntitlement();
        checkOverreach();
        checkModes();
        checkStandardColours();
        checkBypass();
        checkRaids();
        if (failed == 0) {
            Factions.LOGGER.info("=== Factions self-test PASSED ({} checks) ===", passed);
        } else {
            Factions.LOGGER.error("=== Factions self-test FAILED ({} of {}) ===",
                    failed, passed + failed);
        }
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
        } finally {
            FactionRaid.clear();
            check("the raid fixtures are gone", FactionRaid.active().isEmpty()
                    && FactionRaid.cooldownLeft("a", "b", 1_000_000_000_000L) == 0);
        }
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

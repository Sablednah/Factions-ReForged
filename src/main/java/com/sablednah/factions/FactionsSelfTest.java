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
        if (failed == 0) {
            Factions.LOGGER.info("=== Factions self-test PASSED ({} checks) ===", passed);
        } else {
            Factions.LOGGER.error("=== Factions self-test FAILED ({} of {}) ===",
                    failed, passed + failed);
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

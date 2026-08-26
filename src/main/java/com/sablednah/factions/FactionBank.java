package com.sablednah.factions;

import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.api.economy.TransactionResult;

/**
 * The faction's money.
 *
 * <h2>An account, not an economy</h2>
 *
 * <p>Standards' {@link Economy} facade already decides who holds <em>player</em> money, and
 * exactly one provider wins that — a balance is a single fact and two ledgers disagreeing about
 * it is worse than either. A faction balance is a different question. It is a container, like a
 * chest, so Factions stores it and moves money in and out through the facade.</p>
 *
 * <p>Which means a server running a dedicated economy mod keeps it, and nothing here contests the
 * provider seam. Registering as a second provider to hold faction money would have been the
 * obvious mistake.</p>
 *
 * <h2>Every transfer is two halves, and the second one can fail</h2>
 *
 * <p>Taking money off a player and putting it in a bank is two operations against two different
 * ledgers, and the interesting case is the one where the first succeeds and the second does not.
 * Every method here does the <b>fallible half first</b> and refunds if the second half fails, so
 * the worst outcome is a no-op rather than money that stopped existing.</p>
 */
public final class FactionBank {

    /** What a transfer did, so the caller can say something specific about it. */
    public enum Result {
        OK,
        /** The payer did not have it. */
        INSUFFICIENT,
        /** No economy provider is installed at all. */
        NO_ECONOMY,
        /** The refund path fired; nothing moved, and this should be looked at. */
        FAILED
    }

    public static double balance(FactionStore store, String faction) {
        return store.balanceOf(faction);
    }

    /** Player → faction. */
    public static Result deposit(FactionStore store, ServerPlayer player, String faction,
            double amount) {
        if (!Economy.isAvailable()) {
            return Result.NO_ECONOMY;
        }
        if (!Economy.has(player.getUUID(), amount)) {
            return Result.INSUFFICIENT;
        }
        // Off the player first: theirs is the ledger that can refuse.
        TransactionResult taken = Economy.withdraw(player.getUUID(), amount, "factions:deposit");
        if (!taken.success()) {
            return Result.INSUFFICIENT;
        }
        if (!store.adjustBank(faction, amount)) {
            Economy.deposit(player.getUUID(), amount, "factions:deposit-refund");
            return Result.FAILED;
        }
        return Result.OK;
    }

    /** Faction → player. */
    public static Result withdraw(FactionStore store, ServerPlayer player, String faction,
            double amount) {
        if (!Economy.isAvailable()) {
            return Result.NO_ECONOMY;
        }
        // Out of the bank first, for the same reason: it is the side that can be short.
        if (!store.adjustBank(faction, -amount)) {
            return Result.INSUFFICIENT;
        }
        TransactionResult given = Economy.deposit(player.getUUID(), amount, "factions:withdraw");
        if (!given.success()) {
            store.adjustBank(faction, amount);
            return Result.FAILED;
        }
        return Result.OK;
    }

    /** Faction → faction, for tribute, ransom, or paying an ally to look the other way. */
    public static Result pay(FactionStore store, String from, String to, double amount) {
        if (!store.adjustBank(from, -amount)) {
            return Result.INSUFFICIENT;
        }
        store.adjustBank(to, amount);
        return Result.OK;
    }

    /**
     * What the next chunk costs this faction.
     *
     * <p>Rising with every chunk held, which is the original's idea and a good one: a flat price
     * means the biggest faction — the one that least needs more land — buys it most easily, and
     * the curve is what stops a rich faction simply purchasing a continent. Land becomes an
     * investment with a carrying cost rather than a thing you take because you are stood on it.</p>
     */
    public static double claimCost(int held) {
        double base = FactionsConfig.CLAIM_COST.get();
        if (base <= 0.0D) {
            return 0.0D;
        }
        return base + base * FactionsConfig.CLAIM_COST_MULTIPLIER.get() * held;
    }

    /**
     * What comes back on release, at the price it was bought for rather than today's.
     *
     * <p>Refunding the <em>current</em> price would let a faction buy cheap while small, grow,
     * then release the same chunk for more than it paid — a money printer whose fuel is claiming
     * and unclaiming the same square. So the refund is priced at the position the chunk occupied:
     * releasing your 20th chunk refunds a fraction of what the 20th cost.</p>
     *
     * <p><b>Position, not receipt.</b> Nothing records what any individual chunk actually cost,
     * which means land claimed while {@code claimCost} was zero still refunds when it is turned
     * on. That is a deliberate trade rather than an oversight: a per-chunk purchase price is a
     * second number stored against every claim on the server — the hottest and most numerous data
     * this mod holds — to make an edge case exact that occurs once in a world's life.</p>
     *
     * <p>It does have a size, though, and an owner should know it before flipping the switch. The
     * one-off exposure is the whole refund curve for the land already out there: a faction sitting
     * on 67 chunks at {@code claimCost = 30} and {@code claimCostGrowth = 0.5} can release its way
     * to roughly <b>25,000</b>. <b>Turn claim costs on for a fresh world, or expect established
     * landholders to be able to cash out once.</b></p>
     */
    public static double refund(int heldBefore) {
        return claimCost(heldBefore - 1) * FactionsConfig.CLAIM_REFUND.get();
    }

    private FactionBank() {}
}

package com.sablednah.factions;

import java.util.Optional;

import net.minecraft.world.level.ChunkPos;

/**
 * Taking one chunk, and every reason that can fail.
 *
 * <p>Extracted because {@code /f claim} and {@code /f autoclaim} must agree. They are the same
 * decision asked at different moments, and the moment autoclaim disagreed about the connectedness
 * rule — or the claim limit — would be the moment somebody walked a border into existence that
 * the command would have refused.</p>
 *
 * <p>The outcome is returned rather than announced, because the two callers want to say different
 * things about it. Typing {@code /f claim} at land somebody else owns is a question that deserves
 * an answer; <em>walking</em> across it with autoclaim on is not a question at all, and answering
 * it every sixteen blocks is how autoclaim earns its reputation.</p>
 */
public final class FactionClaims {

    public enum Result {
        CLAIMED,
        /** Already this faction's. */
        ALREADY_YOURS,
        /** Somebody else holds it. */
        OWNED,
        /** At the per-member cap. */
        LIMIT,
        /** Does not touch land the faction already holds, and the server requires that. */
        DISCONNECTED,
        /** The bank cannot afford it. */
        BROKE,
        /** Taken from an over-extended faction, rather than claimed from wilderness. */
        TAKEN,
        /** Somebody else holds it and is strong enough to keep it. */
        THEIRS_AND_HELD,
        /** Theirs, and you would have to declare war first. */
        NOT_AT_WAR,
        /** Theirs, but one of you is peaceful. */
        PEACEFUL,
        /** Theirs and takeable, but not from here — you must start at the edge of their land. */
        NOT_THEIR_BORDER
    }

    /** How many chunks this faction may hold, or -1 for no limit. */
    public static int limitFor(FactionStore.Faction f) {
        int perMember = FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get();
        return perMember < 0 ? -1 : perMember * f.members().size();
    }

    public static Result attempt(FactionStore store, String dim, ChunkPos chunk,
            FactionStore.Faction f) {
        Optional<String> owner = store.ownerOf(dim, chunk.x, chunk.z);
        boolean takingFromSomebody = false;
        if (owner.isPresent()) {
            if (owner.get().equals(f.id())) {
                return Result.ALREADY_YOURS;
            }
            // Somebody else holds it. Which is not automatically a refusal any more: if they are
            // holding more land than their power covers, the difference is takeable.
            Result raid = mayOverclaim(store, dim, chunk, f, owner.get());
            if (raid != Result.CLAIMED) {
                return raid;
            }
            takingFromSomebody = true;
        }
        int held = store.claimCount(f.id());
        int limit = limitFor(f);
        if (limit >= 0 && held >= limit) {
            return Result.LIMIT;
        }
        if (FactionsConfig.REQUIRE_CONNECTED_CLAIMS.get() && held > 0
                && !store.touchesOwnLand(dim, chunk.x, chunk.z, f.id())) {
            return Result.DISCONNECTED;
        }
        // Paid last, after every free refusal has had its say — so a claim that was going to be
        // turned down for some other reason never takes the money first.
        double cost = FactionBank.claimCost(held);
        if (cost > 0.0D && !store.adjustBank(f.id(), -cost)) {
            return Result.BROKE;
        }
        store.claim(dim, chunk.x, chunk.z, f.id());
        return takingFromSomebody ? Result.TAKEN : Result.CLAIMED;
    }

    /**
     * Release a chunk, refunding what its position cost.
     *
     * @return what came back, which is zero when claims are free
     */
    public static double release(FactionStore store, String dim, ChunkPos chunk,
            FactionStore.Faction f) {
        int heldBefore = store.claimCount(f.id());
        store.unclaim(dim, chunk.x, chunk.z);
        double back = FactionBank.refund(heldBefore);
        if (back > 0.0D) {
            store.adjustBank(f.id(), back);
        }
        return back;
    }

    /**
     * Whether this faction may take a chunk that somebody else holds.
     *
     * <h3>The rule the original taught, and where we differ</h3>
     *
     * <p>You may take land from a faction that holds <b>more than its power covers</b>, and only
     * the difference — each chunk you take reduces their overreach by one, so a faction five
     * chunks over stops being takeable after five. The attacker is not rewarded for attacking;
     * they are rewarded for <em>noticing</em>. Somebody else's carelessness opened a window and
     * they were standing there.</p>
     *
     * <p><b>Strictly over.</b> A faction holding exactly what it is entitled to is safe. Sitting on
     * the line is not the same as having overreached, and punishing it would mean every faction
     * had to keep a chunk spare to feel secure.</p>
     *
     * <p><b>At their border.</b> You eat inward from the edge; you cannot reach past a wall and
     * take the chunk with the vault in it. That single rule is what makes building inwards a
     * strategy and a raid a thing with a shape — get in, get to the edge, work.</p>
     *
     * <p><b>Only a declared enemy</b>, which the original did not require. It makes {@code /f
     * peaceful} follow from one rule rather than two, and it means a raid was preceded by somebody
     * saying so out loud — a warning, a public record, and a line in the victim's {@code /f status}
     * before anything is taken.</p>
     */
    private static Result mayOverclaim(FactionStore store, String dim, ChunkPos chunk,
            FactionStore.Faction mine, String theirId) {
        if (!FactionPower.Mode.of(FactionsConfig.POWER_MODE.get()).active()) {
            return Result.OWNED; // power is off, so land is simply theirs
        }
        Optional<FactionStore.Faction> theirs = store.byId(theirId);
        if (theirs.isEmpty()) {
            return Result.OWNED;
        }
        // Peaceful, either way. A faction that opted out of fighting cannot raid and cannot be
        // raided — the promise runs in both directions or it is not a promise.
        if (mine.peaceful() || theirs.get().peaceful()) {
            return Result.PEACEFUL;
        }
        if (FactionsConfig.OVERCLAIM_ENEMIES_ONLY.get()
                && store.relation(mine.id(), theirId) != FactionStore.Relation.ENEMY) {
            return Result.NOT_AT_WAR;
        }
        int theirHeld = store.claimCount(theirId);
        int theirEntitlement = FactionPower.entitlement(theirs.get().members().size(),
                store.powerOf(theirs.get()), FactionsConfig.POWER_MAX.get(),
                FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get());
        if (FactionPower.overreach(theirHeld, theirEntitlement) <= 0) {
            return Result.THEIRS_AND_HELD;
        }
        if (!store.isBorderOf(dim, chunk.x, chunk.z, theirId)) {
            return Result.NOT_THEIR_BORDER;
        }
        return Result.CLAIMED;
    }

    private FactionClaims() {}
}

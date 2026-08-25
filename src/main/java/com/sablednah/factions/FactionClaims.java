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
        BROKE
    }

    /** How many chunks this faction may hold, or -1 for no limit. */
    public static int limitFor(FactionStore.Faction f) {
        int perMember = FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get();
        return perMember < 0 ? -1 : perMember * f.members().size();
    }

    public static Result attempt(FactionStore store, String dim, ChunkPos chunk,
            FactionStore.Faction f) {
        Optional<String> owner = store.ownerOf(dim, chunk.x, chunk.z);
        if (owner.isPresent()) {
            return owner.get().equals(f.id()) ? Result.ALREADY_YOURS : Result.OWNED;
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
        return Result.CLAIMED;
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

    private FactionClaims() {}
}

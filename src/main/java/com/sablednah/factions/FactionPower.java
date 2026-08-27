package com.sablednah.factions;

/**
 * How much of its entitlement a faction is currently holding onto.
 *
 * <h2>Fixed is the ceiling; power is the erosion</h2>
 *
 * <p>The idea the whole system rests on, and the reason all four modes are one mechanism:</p>
 *
 * <blockquote><b>{@code chunksPerMember} still decides what a faction is entitled to. Power decides
 * how much of that entitlement it is currently holding.</b></blockquote>
 *
 * <p>A faction at full power gets exactly what it gets today. Power can only ever take entitlement
 * <em>away</em>; it can never grant more than the fixed rule would. Three things follow, and each
 * of them is a bug avoided rather than a feature added:</p>
 *
 * <ul>
 *   <li><b>Turning power on does not change anybody's allowance.</b> A server that enables it does
 *       not hand out or confiscate land on restart; it adds a way to lose land by playing badly.</li>
 *   <li><b>{@code chunksPerMember} keeps meaning what it says</b>, rather than being silently
 *       replaced by a second land formula that happens to also exist.</li>
 *   <li><b>Farming power cannot inflate land.</b> The ceiling is membership, not power, so killing
 *       things recovers you <em>toward</em> your entitlement and never past it — which quietly
 *       kills the mob-grinder exploit without a special case to maintain.</li>
 * </ul>
 *
 * <h2>Why any of this exists</h2>
 *
 * <p><b>Power exists to make land contestable. Build power without overclaiming and you have built
 * nothing</b> — a number that only caps how much you may claim is a claim limit with extra steps,
 * and this mod already had one. The whole point is the second half: when a faction's power drops
 * below the land it holds, an enemy may take the difference. Territory stops being a purchase and
 * becomes a position you hold.</p>
 */
public final class FactionPower {

    /**
     * What drains power, and what restores it.
     *
     * <p>The modes differ in <em>nothing else</em>. Restoration is the same rule throughout —
     * time online, plus experience earned — so adding a mode is a matter of saying what counts as
     * losing.</p>
     */
    public enum Mode {
        /** Power is inert. Today's behaviour exactly, and the default. */
        FIXED("fixed"),
        /** Being killed by a player drains. */
        PVP("pvp"),
        /** Being killed by a mob drains. Territory shrinks when the world beats you. */
        PVE("pve"),
        /** Either. */
        BOTH("both");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Mode of(String key) {
            for (Mode m : values()) {
                if (m.key.equalsIgnoreCase(key)) {
                    return m;
                }
            }
            return FIXED;
        }

        public boolean drainsOn(boolean killedByPlayer) {
            return switch (this) {
                case FIXED -> false;
                case PVP -> killedByPlayer;
                case PVE -> !killedByPlayer;
                case BOTH -> true;
            };
        }

        /** Whether power does anything at all. */
        public boolean active() {
            return this != FIXED;
        }
    }

    /**
     * What a faction may hold, given its members' power.
     *
     * <p>Each member contributes their share of {@code chunksPerMember} in proportion to how much
     * of their maximum power they still have. A faction of two at full power gets exactly
     * {@code 2 × chunksPerMember}; one of them dropping to 60% costs the faction 40% of one
     * member's worth of land, and nothing else changes.</p>
     *
     * <p>Floored rather than rounded, deliberately: rounding up would let a faction hold a chunk
     * its power does not cover, which is precisely the state overclaiming exists to punish — and
     * it would flicker in and out of raidable on a rounding boundary.</p>
     *
     * @param members      how many people are in it
     * @param totalPower   the sum of their current power
     * @param maxPerMember the per-player maximum
     * @param perMember    {@code chunksPerMember}, or -1 for no limit
     */
    public static int entitlement(int members, double totalPower, double maxPerMember,
            int perMember) {
        if (perMember < 0) {
            return -1;
        }
        if (members <= 0 || maxPerMember <= 0) {
            return 0;
        }
        return (int) Math.floor(perMember * (totalPower / maxPerMember));
    }

    /**
     * How much land an enemy may take from this faction right now.
     *
     * <p><b>Strictly over.</b> A faction holding exactly what it is entitled to is safe — sitting
     * on the line is not the same as having overreached, and a rule that punished it would mean
     * every faction had to keep a chunk spare to feel secure.</p>
     */
    public static int overreach(int held, int entitlement) {
        if (entitlement < 0) {
            return 0; // no limit configured, so nothing is ever over-extended
        }
        return Math.max(0, held - entitlement);
    }

    private FactionPower() {}
}

package com.sablednah.factions;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may touch what, in whose land.
 *
 * <h2>Building and touching are different questions</h2>
 *
 * <p>An ally is somebody you have agreed not to fight, and usually somebody you want walking
 * through your front door. That is not the same as somebody who may take your walls down. The two
 * got answered together at first, which quietly made every alliance a demolition permit — so they
 * are separate now, and they default differently:</p>
 *
 * <ul>
 *   <li><b>Building and breaking: members only.</b> The original's default, and the safer one. An
 *       alliance is a diplomatic position, and diplomatic positions change; the blocks should not
 *       be hostage to the week somebody fell out.</li>
 *   <li><b>Interacting: members and allies.</b> Doors, buttons, levers, chests. An ally who cannot
 *       open your gate is an ally who stands outside it.</li>
 * </ul>
 *
 * <h2>Interaction is the protection that actually matters</h2>
 *
 * <p>Guarding block-breaking alone protects the walls and leaves everything behind them open. A
 * stranger who cannot mine your chest can still <em>open</em> it, which is the theft the claim was
 * bought to prevent — and can flip your levers, open your doors, and empty your furnaces on the
 * way out.</p>
 *
 * <p>So the rule is the blunt one: <b>right-clicking a block in somebody's claim does nothing.</b>
 * Not a list of protected materials, which is a list somebody has to maintain and which every
 * modded block is missing from by default. The original shipped exactly that list — door,
 * trapdoor, chest, furnace, dispenser, repeater — and it was already incomplete for vanilla by the
 * time anyone read it.</p>
 *
 * <p>The escape hatch it leaves is deliberate and dates back just as far: <b>pressure plates still
 * work</b>, because standing on one is not a right-click. A landowner who wants visitors puts a
 * plate outside the door. Protection you can deliberately open a hole in beats protection you have
 * to switch off.</p>
 */
public final class FactionProtection {

    /** Break, place, and anything else that changes the world's blocks. */
    public static boolean mayBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        // The one deliberate hole in the protection, and the whole reason a standard is worth
        // planting: an enemy may take your flag. Without this the banner would be as unbreakable
        // as every other block you own, and the feature would be a decoration.
        //
        // Only a standard, only an enemy, and only where it stands — which is out in the open,
        // because a standard must see the sky. So the flag is defended by architecture and by
        // people, and never by being unbreakable.
        if (mayTakeStandard(player, level, pos)) {
            return true;
        }
        return may(player, level, pos, FactionsConfig.ALLIES_MAY_BUILD.get());
    }

    /** Whether this player is an enemy of whoever is flying a standard at this exact block. */
    public static boolean mayTakeStandard(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!FactionPower.Mode.of(FactionsConfig.POWER_MODE.get()).active()) {
            return false;
        }
        FactionStore store = FactionStore.get(level.getServer());
        Optional<String> flying = store.standardAt(FactionBridge.dimensionOf(level), pos);
        if (flying.isEmpty()) {
            return false;
        }
        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        if (mine.isEmpty() || mine.get().id().equals(flying.get())) {
            return false;
        }

        // Taking back your OWN flag is always allowed, whatever the relation and whatever either
        // side has declared. Recovering your property is not an act of aggression, and without
        // this exception there is a lock: capture a faction's standard, and if either of you then
        // goes peaceful they can neither reclaim it nor raise another, for ever.
        if (store.standardCapturedFrom(flying.get()).map(mine.get().id()::equals).orElse(false)) {
            return true;
        }

        // Otherwise the ordinary rules. Peaceful both ways: a faction that opted out of fighting
        // neither raids nor is raided, and its flag is part of that promise.
        Optional<FactionStore.Faction> theirs = store.byId(flying.get());
        if (mine.get().peaceful() || theirs.map(FactionStore.Faction::peaceful).orElse(false)) {
            return false;
        }
        return store.relation(mine.get().id(), flying.get()) == FactionStore.Relation.ENEMY;
    }

    /** Right-clicking: doors, buttons, containers, item frames. */
    public static boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!FactionsConfig.PROTECT_INTERACTION.get()) {
            return true;
        }
        return may(player, level, pos, FactionsConfig.ALLIES_MAY_INTERACT.get());
    }

    private static boolean may(ServerPlayer player, ServerLevel level, BlockPos pos,
            boolean alliesToo) {
        FactionStore store = FactionStore.get(level.getServer());
        Optional<String> owner = store.ownerOf(FactionBridge.dimensionOf(level),
                pos.getX() >> 4, pos.getZ() >> 4);
        if (owner.isEmpty()) {
            return true; // wilderness belongs to nobody and is defended by nobody
        }
        Optional<FactionStore.Faction> mine = store.of(player.getUUID());
        if (mine.isEmpty()) {
            return false;
        }
        String id = mine.get().id();
        if (id.equals(owner.get())) {
            return true;
        }
        return alliesToo
                && store.relation(id, owner.get()) == FactionStore.Relation.ALLY;
    }

    private FactionProtection() {}
}

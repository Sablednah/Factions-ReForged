package com.sablednah.factions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Right-click harvesting by allies: a break that is really an interaction.
 *
 * <p>Harvest mods (RightClickHarvest is the one ZARP runs) pick a ripe crop by posting a
 * {@code BreakBlockEvent} for it and then an {@code EntityPlaceEvent} for the replant, so that
 * protection mods get a say. Ours said no to allies, because both go through the build rule, so an
 * ally could open your chests but not pick your wheat. Picking and replanting leaves the farm as it
 * was found, so it gets the interaction answer instead. No harvest mod is named here: anything
 * that harvests from inside a right-click gets the same.</p>
 *
 * <h3>Detected by the call stack, not by remembering the click</h3>
 *
 * <p>The obvious design, which was the one asked for, is to note the right-click and excuse a
 * break that follows it. <b>It cannot work for RightClickHarvest.</b> jamlib hooks the click with a
 * mixin at the <em>head</em> of {@code ServerPlayerGameMode.useItemOn}, so the harvest and its break
 * event are over before NeoForge fires {@code RightClickBlock} a few lines further down. Nothing
 * would be remembered yet. Read from the shipped jars, not guessed.</p>
 *
 * <p>So the question becomes "is {@code useItemOn} on the stack?". Every right-click on a block,
 * from any mod, at any priority, comes through that method, and a left-click break never does.
 * Walking the stack costs something, so it is only asked on the path that was about to refuse:
 * a harvestable block, in somebody else's claim, with the switch on.</p>
 *
 * <h3>Why the tag as well</h3>
 *
 * <p>An ordinary block placement also happens inside {@code useItemOn}. Without a tag, the stack
 * test alone would let allies place anything. So a break must be of a block in
 * {@code #factions:harvestable}, and a place is excused only as the replant of a harvest this
 * class has just allowed: same player, same block, same tick.</p>
 */
final class FactionHarvest {

    /** What may be picked: crops, nether wart, cocoa, sugar cane, cactus. A pack can add to it. */
    static final TagKey<Block> HARVESTABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("factions", "harvestable"));

    private static final String GAME_MODE = "net.minecraft.server.level.ServerPlayerGameMode";

    /** The last harvest break we let through, per player, so its replant can follow it. */
    private record Allowed(ResourceKey<Level> dimension, BlockPos pos, int tick) {}

    private static final Map<UUID, Allowed> ALLOWED = new ConcurrentHashMap<>();

    /** Whether this refused break is really an ally picking a crop. */
    static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(HARVESTABLE) || !FactionProtection.mayHarvest(player, level, pos)
                || !insideRightClick()) {
            return false;
        }
        ALLOWED.put(player.getUUID(),
                new Allowed(level.dimension(), pos.immutable(), level.getServer().getTickCount()));
        return true;
    }

    /**
     * Whether this refused placement is the replant of a harvest just allowed.
     *
     * <p>RightClickHarvest posts the place event <em>before</em> it replants, so the snapshot
     * still holds the ripe crop. That is why the state is checked against the tag here too.</p>
     */
    static boolean mayReplant(ServerPlayer player, ServerLevel level, BlockPos pos,
            BlockState placed) {
        Allowed last = ALLOWED.get(player.getUUID());
        return last != null
                && placed.is(HARVESTABLE)
                && last.tick() == level.getServer().getTickCount()
                && last.dimension().equals(level.dimension())
                && last.pos().equals(pos);
    }

    /** Is a right-click on a block being handled further up this thread? */
    static boolean insideRightClick() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(f ->
                f.getMethodName().equals("useItemOn") && f.getClassName().equals(GAME_MODE)));
    }

    static void forget(UUID player) {
        ALLOWED.remove(player);
    }

    private FactionHarvest() {}
}

package com.sablednah.factions;

import java.util.Optional;

import com.sablednah.standards.api.groups.Claims;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Where Factions attaches to the game. */
public final class FactionsEvents {

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        // /f is the one people type; /factions is the one they can find.
        event.getDispatcher().register(FactionCommands.build("f"));
        event.getDispatcher().register(FactionCommands.build("factions"));
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        FactionBridge.install(event.getServer());
        FactionChat.install();
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        FactionBridge.uninstall();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        FactionBorders.tick(event.getServer());
        FactionAutoClaim.tick(event.getServer());
        FactionPowerEvents.tick(event.getServer());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FactionBorders.forget(event.getEntity().getUUID());
        FactionAutoClaim.forget(event.getEntity().getUUID());
        FactionChat.forget(event.getEntity().getUUID());
        FactionPowerEvents.forget(event.getEntity().getUUID());
        // The one piece of state here that is MEANT to be lost. See FactionBypass: coming back
        // with the protection off, having forgotten, is the whole thing it exists to prevent.
        FactionBypass.forget(event.getEntity().getUUID());
    }

    // --- protection ---

    /**
     * Block breaking, through Standards' claims seam rather than our own store.
     *
     * <p>Deliberately the long way round. Asking {@link Claims} means this mod is exercising the
     * same path ZombieMod and CityWorld will use — so if the seam is wrong, it is wrong for us
     * first and we find out immediately rather than when somebody else reports it.</p>
     */
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Before the permission check, because taking a standard is allowed precisely where
        // breaking things is not, and the bookkeeping has to happen while the block still exists.
        if (FactionStandards.onBroken(level, event.getPos(), player)) {
            return;
        }
        if (Claims.mayModify(player, level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        refuse(player, level, event.getPos());
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (Claims.mayModify(player, level, event.getPos())) {
            // Planting a captured flag in your own land IS flying it. Requiring a command as well
            // produced exactly the confusion it deserved: a player planted a trophy, nothing
            // happened, and there was no message to say why. The act is the declaration.
            FactionStandards.onPlaced(level, event.getPos(), player);
            return;
        }
        event.setCanceled(true);
        refuse(player, level, event.getPos());
    }

    /**
     * Doors, buttons, levers, chests, furnaces — everything a right-click reaches.
     *
     * <p>Cancelled outright rather than only denying the block half, so an item used against the
     * block cannot slip through either: a bucket, a flint and steel and a bone meal are all
     * "interaction" by any reading a landowner cares about.</p>
     */
    @SubscribeEvent
    static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
            .RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (FactionProtection.mayInteract(player, level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        // The exact face they clicked, so the refusal appears under the cursor rather than
        // inside the block — which for a lever on a wall is where nobody can see it.
        refuse(player, level, event.getPos(),
                event.getHitVec() != null ? event.getHitVec().getLocation() : null);
    }

    /**
     * Item frames, armour stands, and the paintings somebody always takes.
     *
     * <p>Worth its own listener because none of these are blocks, so every block-shaped guard
     * misses them — and they are the first thing a visitor walks off with. An armour stand full
     * of diamond is a chest that forgot to be one.</p>
     */
    @SubscribeEvent
    static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
            .EntityInteract event) {
        guardEntity(event, event.getTarget());
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(net.neoforged.neoforge.event.entity.player
            .PlayerInteractEvent.EntityInteractSpecific event) {
        guardEntity(event, event.getTarget());
    }

    /** Breaking a frame is a left-click, so it arrives as an attack rather than an interaction. */
    @SubscribeEvent
    static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Only the furniture. Fighting is the pvp listener's business, and cancelling an attack
        // here would quietly disarm everybody standing in a claim.
        net.minecraft.world.entity.Entity target = event.getTarget();
        if (!(target instanceof net.minecraft.world.entity.decoration.HangingEntity)
                && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            return;
        }
        if (FactionProtection.mayBuild(player, level, target.blockPosition())) {
            return;
        }
        event.setCanceled(true);
        // Aimed at the entity rather than the block it is standing in. An armour stand's
        // blockPosition is the floor under its feet, so the refusal appeared at ankle height —
        // out of shot for anybody stood close enough to have clicked it.
        refuse(player, level, target.blockPosition(), target.getBoundingBox().getCenter());
    }

    private static void guardEntity(net.neoforged.bus.api.ICancellableEvent event,
            net.minecraft.world.entity.Entity target) {
        if (!(event instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent e)
                || !(e.getEntity() instanceof ServerPlayer player)
                || !(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Animals are left alone: a claim is not a reason a wandering cow cannot be fed, and
        // leads and boats are how people get about.
        if (!(target instanceof net.minecraft.world.entity.decoration.HangingEntity)
                && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            return;
        }
        if (FactionProtection.mayInteract(player, level, target.blockPosition())) {
            return;
        }
        // The calling card. Turning a frame that already holds something takes nothing and
        // changes nothing that cannot be turned back — but it is unmistakable evidence somebody
        // stood there, which on a roleplay server is worth more than anything they could steal.
        //
        // Only a frame that is already occupied: an EMPTY one is not being turned, it is being
        // filled, and putting your own item into somebody's wall is a change to their base.
        if (FactionsConfig.ANYONE_MAY_ROTATE_FRAMES.get()
                && target instanceof net.minecraft.world.entity.decoration.ItemFrame frame
                && !frame.getItem().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        refuse(player, level, target.blockPosition(), target.getBoundingBox().getCenter());
    }

    /**
     * Explosions stop at the fence.
     *
     * <h3>Filtered, not cancelled</h3>
     *
     * <p>The affected-block list is edited rather than the event being called off, because an
     * explosion does not respect the border it straddles: a creeper on the wilderness side of your
     * wall should crater the wilderness and leave the wall standing. Cancelling the whole thing
     * would protect land nobody claimed, and would do it invisibly.</p>
     *
     * <h3>Blocks only</h3>
     *
     * <p>The entity list is left completely alone. Standing next to a creeper in your own base is
     * still a mistake, and a claim that made its owners immune to explosions would be a PvP
     * mechanic smuggled in as a building one.</p>
     *
     * <h3>Whose claim, not whose creeper</h3>
     *
     * <p>Every affected block is tested against the chunk it is in, one at a time, and the answer
     * does not depend on who lit it. That matters for TNT: a raider cannot get a cheaper result by
     * standing outside and throwing it in, which is exactly the workaround somebody finds within a
     * day of the first wall going up.</p>
     */
    @SubscribeEvent
    static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean tnt = isTnt(event.getExplosion().getDirectSourceEntity());
        if (tnt ? !FactionsConfig.BLOCK_TNT.get() : !FactionsConfig.BLOCK_MOB_EXPLOSIONS.get()) {
            return;
        }
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);
        event.getAffectedBlocks().removeIf(pos ->
                store.ownerOf(dim, pos.getX() >> 4, pos.getZ() >> 4).isPresent());
    }

    private static boolean isTnt(net.minecraft.world.entity.Entity source) {
        return source instanceof net.minecraft.world.entity.item.PrimedTnt
                || source instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
    }

    /**
     * Say whose land it is rather than just refusing.
     *
     * <p>A block that silently refuses to break reads as lag, and the player tries again — half a
     * dozen times, getting crosser. Naming the owner turns it into information, and quite often
     * into a conversation.</p>
     */
    private static void refuse(ServerPlayer player, ServerLevel level,
            net.minecraft.core.BlockPos pos) {
        refuse(player, level, pos, null);
    }

    private static void refuse(ServerPlayer player, ServerLevel level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec3 at) {
        String name = Claims.owner(level, new ChunkPos(pos))
                .map(com.sablednah.standards.api.groups.Group::name)
                .orElse("?");
        player.displayClientMessage(Feedback.colored(
                Lang.fmt("msg.factions.cannot_build", "name", name)), true);
        deny(player, level, pos, at);
    }

    /** The same red the borders and the map use for an enemy. One palette, one meaning. */
    private static final int DENIED = 0xFF5555;

    /**
     * A puff of red where they clicked, and a correction for what their client already drew.
     *
     * <p>Two problems, and the first one told us about the second. A cancelled interaction is
     * <b>silent at the point of contact</b>: the action bar says whose land it is, but the action
     * bar is at the top of the screen and the disappointment is under the cursor, so the first
     * instinct is to click again — and the second, and the third — before anybody reads anything.
     * That is the same failure a teleport with no countdown has, and it wants the same answer:
     * say no <em>where the no happened</em>.</p>
     *
     * <p>The second problem is that the client has usually already drawn a <em>yes</em>. Minecraft
     * predicts an interaction locally before the server rules on it, which is why a denied lever
     * still throws its redstone spark — and can be left looking flipped when it is not. A block
     * that lies about its own state is worse than either a refusal or a lag spike, so the real
     * state is resent to that player alone. Neighbours too, because a door is two blocks and only
     * one of them was clicked.</p>
     *
     * <p>Sent to the one player. Everybody else watching a stranger fail to open a door should see
     * exactly what happened in the world, which is nothing.</p>
     */
    private static void deny(ServerPlayer player, ServerLevel level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec3 at) {
        net.minecraft.world.phys.Vec3 where = at != null
                ? at
                : net.minecraft.world.phys.Vec3.atCenterOf(pos);
        level.sendParticles(player,
                new net.minecraft.core.particles.DustParticleOptions(DENIED, 1.0F),
                true, false, where.x, where.y, where.z, 8, 0.15D, 0.15D, 0.15D, 0.0D);
        // To this player only — a sound played into the level would tell the landowner's
        // neighbours that somebody is rattling their door, which is a different feature.
        //
        // Full volume and pitched right down. At 0.4 it arrived, showed up in the subtitles, and
        // was inaudible under a door that was making its own noise at the same moment — which is
        // precisely when it needed to be heard. A cue that competes with the thing it is
        // explaining has to win.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value()),
                net.minecraft.sounds.SoundSource.BLOCKS,
                where.x, where.y, where.z, 1.0F, 0.5F, 0L));

        // And whatever it predicted about the player's own hands. Placing a block consumes it
        // client-side before the server rules, so a refused pressure plate vanished from the
        // hotbar and stayed gone until relog — which reads as the mod eating your items, and is
        // the single most alarming thing a protection plugin can appear to do. The inventory is
        // authoritative on the server the whole time; only the picture was wrong.
        player.containerMenu.sendAllDataToRemote();

        // Undo whatever the client predicted, for the block and the ones a door or a bed shares
        // its state with.
        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundBlockUpdatePacket(level, pos));
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            player.connection.send(new net.minecraft.network.protocol.game
                    .ClientboundBlockUpdatePacket(level, pos.relative(dir)));
        }
    }


    private FactionsEvents() {}

    /**
     * A death costs power, if the mode says this kind of death counts.
     *
     * <p>Attribution comes from Standards' combat API rather than being worked out again here:
     * "was a player behind this" is exactly what it answers, arrows and pets included, and a
     * second implementation would eventually disagree with the first in a way that depended on
     * whether an arrow was involved.</p>
     */
    @SubscribeEvent
    static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FactionPowerEvents.onDeath(player, event.getSource());
        }
    }

    /** A mob dropping experience restores power to whoever killed it. */
    @SubscribeEvent
    static void onExperienceDrop(
            net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer killer) {
            FactionPowerEvents.onExperience(killer, event.getOriginalExperience());
        }
    }

    /**
     * Name a taken standard's drop.
     *
     * <p>Vanilla's banner loot table does not copy a block entity's custom name onto the item, so
     * naming the block when it was designated is not enough — the drop has to be named again here,
     * where the item finally exists.</p>
     */
    @SubscribeEvent
    static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        FactionStandards.renameDrops(event.getPos(), event.getDrops());
    }
}

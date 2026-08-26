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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FactionBorders.forget(event.getEntity().getUUID());
        FactionAutoClaim.forget(event.getEntity().getUUID());
        FactionChat.forget(event.getEntity().getUUID());
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
        refuse(player, level, target.blockPosition());
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
        event.setCanceled(true);
        refuse(player, level, target.blockPosition());
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

        // Undo whatever the client predicted, for the block and the ones a door or a bed shares
        // its state with.
        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundBlockUpdatePacket(level, pos));
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            player.connection.send(new net.minecraft.network.protocol.game
                    .ClientboundBlockUpdatePacket(level, pos.relative(dir)));
        }
    }

    /**
     * Who may hit whom.
     *
     * <p>Peaceful is checked first and in both directions, because it is a promise rather than a
     * preference: a faction that has declared itself out of the fighting should not be draggable
     * back in by somebody else's declaration.</p>
     */
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (attacker.getUUID().equals(victim.getUUID())) {
            return;
        }
        FactionStore store = FactionStore.get(victim.level().getServer());
        Optional<FactionStore.Faction> mine = store.of(attacker.getUUID());
        Optional<FactionStore.Faction> theirs = store.of(victim.getUUID());

        String refusal = null;
        if (mine.map(FactionStore.Faction::peaceful).orElse(false)
                || theirs.map(FactionStore.Faction::peaceful).orElse(false)) {
            refusal = "msg.factions.pvp_peaceful";
        } else if (mine.isPresent() && theirs.isPresent()
                && mine.get().id().equals(theirs.get().id())) {
            if (!FactionsConfig.PVP_IN_OWN_LAND.get()) {
                refusal = "msg.factions.pvp_same_faction";
            }
        } else if (!FactionsConfig.PVP_BETWEEN_FACTIONS.get()) {
            refusal = "msg.factions.pvp_disabled";
        }

        if (refusal != null) {
            event.setCanceled(true);
            attacker.displayClientMessage(Feedback.colored(Lang.get(refusal)), true);
        }
    }

    private FactionsEvents() {}
}

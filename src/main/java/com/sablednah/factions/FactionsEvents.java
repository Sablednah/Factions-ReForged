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
     * Say whose land it is rather than just refusing.
     *
     * <p>A block that silently refuses to break reads as lag, and the player tries again — half a
     * dozen times, getting crosser. Naming the owner turns it into information, and quite often
     * into a conversation.</p>
     */
    private static void refuse(ServerPlayer player, ServerLevel level,
            net.minecraft.core.BlockPos pos) {
        String name = Claims.owner(level, new ChunkPos(pos))
                .map(com.sablednah.standards.api.groups.Group::name)
                .orElse("?");
        player.displayClientMessage(Feedback.colored(
                Lang.fmt("msg.factions.cannot_build", "name", name)), true);
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

package com.sablednah.factions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * The permission nodes Factions declares — and there are deliberately very few.
 *
 * <h2>Rank is not a permission</h2>
 *
 * <p>Almost everything under {@code /f} is gated on your rank inside your own faction: leader,
 * officer, member. That is <b>game state</b>, not a permission. You become an officer by being
 * promoted, and a permissions mod should no more be able to grant it than it should be able to
 * grant "has a faction". Turning those checks into nodes would let a server hand somebody officer
 * powers over a faction that never chose them, which is not a feature.</p>
 *
 * <p>So this class exists for the small set of things that genuinely are <em>server</em>
 * permissions — decisions about what a person may do on this server, rather than what their
 * faction has decided about them.</p>
 *
 * <p>See {@code NODES.md}, which sets out the whole access model and the reasoning.</p>
 */
public final class FactionPermissions {

    /** Declared first, before the nodes: static initialisers run in source order. */
    private static final List<PermissionNode<Boolean>> NODES = new ArrayList<>();

    /**
     * Turn the claim override on and off — {@code /f bypass}.
     *
     * <p>Note this grants the <b>ability to enter the state</b>, not the override itself. Holding
     * it changes nothing until you type the command, which is the point: see
     * {@link FactionBypass}.</p>
     *
     * <p>Operators by default, so a server that configures nothing behaves exactly as it did.
     * Grantable so a moderator can undo a grief inside a claim without being made an operator and
     * handed {@code /stop} along with it.</p>
     */
    public static final PermissionNode<Boolean> BYPASS = node("bypass", true);

    private static PermissionNode<Boolean> node(String path, boolean opsByDefault) {
        PermissionNode<Boolean> created = new PermissionNode<>(
                Factions.MODID, path, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> opsByDefault
                        && player != null
                        && Commands.LEVEL_GAMEMASTERS.check(player.permissions()));
        NODES.add(created);
        return created;
    }

    @SubscribeEvent
    static void onGather(PermissionGatherEvent.Nodes event) {
        NODES.forEach(event::addNodes);
        Factions.LOGGER.info("Factions: registered {} permission node(s)", NODES.size());
    }

    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        return PermissionAPI.getPermission(player, node);
    }

    /**
     * The {@code requires()} predicate: a player is asked about the node, anything else is judged
     * on vanilla's permission level.
     *
     * <p>The second half matters for the console and command blocks, which are not players and
     * therefore have no node to be asked about.</p>
     */
    public static java.util.function.Predicate<CommandSourceStack> require(
            PermissionNode<Boolean> node) {
        return source -> source.getEntity() instanceof ServerPlayer player
                ? has(player, node)
                : Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    private FactionPermissions() {}
}

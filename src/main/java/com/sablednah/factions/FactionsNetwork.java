package com.sablednah.factions;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Factions' optional client channel.
 *
 * <p>One clientbound payload, and the same two guards Standards learned the hard way:</p>
 *
 * <ul>
 * <li>⚠ the registration is a <b>single chained expression</b>, because {@code optional()} returns
 *     a <em>clone</em> rather than mutating — assigning the registrar and calling {@code optional()}
 *     on it separately registers a REQUIRED channel, which compiles, reads correctly, and kicks
 *     every vanilla player at login with "Invalid player data";</li>
 * <li>every send goes through Standards' {@code Net.sendIfAble}, because {@code optional()} makes
 *     the handshake tolerant and does <b>not</b> make sends droppable.</li>
 * </ul>
 */
public final class FactionsNetwork {

    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).optional()
                .playToClient(FactionPanelPayload.TYPE, FactionPanelPayload.CODEC,
                        (payload, context) ->
                                com.sablednah.factions.client.FactionPanelData.accept(payload));
    }

    private FactionsNetwork() {}
}

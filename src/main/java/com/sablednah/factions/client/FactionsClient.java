package com.sablednah.factions.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import com.sablednah.factions.Factions;

/**
 * Factions' client half — one screen, and nothing a dedicated server may load.
 *
 * <p>Nothing here is load-bearing. With this class absent, {@code /f panel} prints the same facts
 * as text and every other command is unchanged, which is the rule the whole client half of this
 * pair is built on.</p>
 */
@Mod(value = Factions.MODID, dist = Dist.CLIENT)
public class FactionsClient {

    public FactionsClient(ModContainer container, IEventBus modEventBus) {
        // Deliberately empty. The panel is NOT registered as an action screen, because it cannot
        // be built until the server has answered: the button sends "f panel" like any other
        // action, the server replies with the data, and the reply is what opens the screen.
        //
        // Which means the vanilla path and the modded path are the same command, differing only in
        // whether anybody was listening for the answer — the strongest form of "same answers,
        // nicer surface" available, since there is no second code path at all.
    }
}

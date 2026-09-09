package com.sablednah.factions.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import com.sablednah.factions.Factions;
import com.sablednah.standards.api.actions.Actions;
import com.sablednah.standards.client.panels.Panels;

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
        // The panel button toggles a pane on the inventory screen rather than opening a screen, so
        // it registers a HANDLER rather than a screen: "make one and show it" cannot express an
        // second press putting it away. Guarded because Standards is a hard dependency but an
        // older one has no handler seam, and that should cost a button rather than the mod.
        // No event handlers of our own: the pane draws nothing until Standards hands it a
        // rectangle, and Standards owns every screen event it needs. One place to look for "what
        // draws on the inventory screen", which is the entire point of the seam.
        try {
            Panels.register(FactionPanel.ID, Panels.Area.LEFT, FactionPanel.INSTANCE);
            Actions.registerHandler(FactionPanel.ID, FactionPanel::toggle);
            // ...and the bar lights the button while the pane is open. The server cannot answer
            // this one — whether a pane is showing is not a fact it has — so the client claims the
            // question. Without it the panel button would be the only toggle on the bar that never
            // shows its own state.
            Actions.registerClientState(FactionPanel.ID, FactionPanel::isOpen);
        } catch (LinkageError e) {
            Factions.LOGGER.info("Factions: Standards has no panel seam; the panel button will "
                    + "send its command and print as text instead ({})",
                    e.getClass().getSimpleName());
        }
        // The handler still asks the server with the same "f panel" a vanilla client sends, and
        // draws only what comes back — so the two paths are the same command, differing only in
        // whether anybody was listening for the answer.
    }
}

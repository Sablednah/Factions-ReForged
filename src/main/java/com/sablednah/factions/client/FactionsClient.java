package com.sablednah.factions.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import com.sablednah.factions.Factions;
import com.sablednah.standards.api.actions.Actions;

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
        // Registered explicitly rather than by annotation, to match how Standards wires its own
        // action bar — one place to look for "what draws on the inventory screen".
        NeoForge.EVENT_BUS.register(FactionPanel.class);
        try {
            Actions.registerHandler("factions:panel", FactionPanel::toggle);
        } catch (LinkageError e) {
            Factions.LOGGER.info("Factions: Standards has no action handler seam; the panel "
                    + "button will send its command instead ({})", e.getClass().getSimpleName());
        }
        // The handler still asks the server with the same "f panel" a vanilla client sends, and
        // draws only what comes back — so the two paths are the same command, differing only in
        // whether anybody was listening for the answer.
    }
}

package com.sablednah.factions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import com.sablednah.factions.FactionPanelPayload;
import com.sablednah.standards.client.panels.Panels;

/**
 * The last panel the server sent, and whether a screen is waiting for it.
 *
 * <p>Holds data and nothing else — no rendering type is named here, so it is safe to reference from
 * the common-side payload handler. The screen that draws it is a separate class the dedicated
 * server never loads.</p>
 */
public final class FactionPanelData {

    private static volatile FactionPanelPayload latest;

    public static void accept(FactionPanelPayload payload) {
        latest = payload;
        // ⚠ Storing it is not enough, and thinking it was broke `/f panel` typed in chat.
        //
        // While the panel was a Screen, the reply arriving is what opened it — so typing the
        // command worked. Turning it into a pane the button toggles made this a pure store, and a
        // player who typed `/f panel` got a payload, no chat message (the server had answered) and
        // nothing on screen. The command silently did nothing on exactly the clients that had gone
        // to the trouble of installing the mod.
        //
        // So a reply still opens the pane — but only when it is not already showing, which is what
        // keeps the toggle honest: every refresh (onOpen, and after a promote or kick) arrives
        // while it IS open and changes nothing.
        if (Panels.isOpen(FactionPanel.ID)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Panels.open(FactionPanel.ID);
            // A pane only draws on the inventory screen, so a command typed in the world would
            // open something the player cannot see. Show them the inventory it lives on — but
            // never replace a screen they are already in the middle of.
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
        });
    }

    public static FactionPanelPayload latest() {
        return latest;
    }

    private FactionPanelData() {}
}

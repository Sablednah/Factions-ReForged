package com.sablednah.factions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import com.sablednah.factions.FactionPanelPayload;
import com.sablednah.standards.client.panels.Panels;

/**
 * The last panel the server sent, and what to do when one arrives.
 *
 * <h2>⚠ Referenced from common code, and no longer free of client types</h2>
 *
 * <p>This class used to say it named no rendering type, and that this was what made it safe to
 * reference from {@code FactionsNetwork}'s common-side payload handler. <b>That stopped being true
 * the moment it learned to open the pane</b> — it now names {@code Minecraft} and
 * {@code InventoryScreen} — and a stale safety claim is worse than none, because the next person
 * moves the reference somewhere on the strength of it.</p>
 *
 * <p>The guarantee that actually holds is about <em>invocation</em>, not purity: the lambda in
 * {@code FactionsNetwork.register} is a {@code playToClient} handler, so a dedicated server
 * registers it and never calls it. Creating a lambda does not load the classes its body names —
 * only running it does — so this class is never loaded on a server. That is the stronger claim
 * anyway, since a class naming no rendering type could still reference one that does.</p>
 *
 * <p>Verified rather than reasoned: the dedicated-server self-test passes on all three Minecraft
 * lines with Factions installed, and a vanilla client joins because every clientbound payload here
 * is registered {@code optional()}.</p>
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
            // 26.2 moved both of these: Minecraft.screen became Minecraft.gui.screen(), and
            // setScreen became setScreenAndShow.
            if (mc.gui.screen() == null && mc.player != null) {
                mc.setScreenAndShow(new InventoryScreen(mc.player));
            }
        });
    }

    public static FactionPanelPayload latest() {
        return latest;
    }

    private FactionPanelData() {}
}

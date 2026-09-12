package com.sablednah.factions.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

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

        container.registerConfig(ModConfig.Type.CLIENT, FactionsClientConfig.SPEC);

        modEventBus.addListener(FactionsClient::onRegisterDebugRenderers);
        modEventBus.addListener(FactionsClient::onRegisterKeys);
        NeoForge.EVENT_BUS.register(ClaimGrid.class);
        NeoForge.EVENT_BUS.addListener(FactionsClient::onClientTick);
    }

    /**
     * ⚠ Registered as a DEBUG renderer, which is not what it sounds like.
     *
     * <p>It is the only way into a {@code GizmoCollector} — vanilla's line and rect primitives
     * throw without one — and a mod-registered renderer is added unconditionally and ticked every
     * frame with no F3 gate. See {@link ClaimBorderRenderer} for why borrowing vanilla's own
     * primitives matters on the one surface in this mod that draws in the world.</p>
     */
    private static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(ClaimBorderRenderer::new);
    }

    /**
     * Bound to {@code '} by default, and the key runs the COMMAND.
     *
     * <p>⚠ <b>The default binding is the owner's call, made after using it.</b> The standing rule
     * is that a mod claiming a key on install is how conflicts start, and this shipped unbound
     * because of it — which meant the feature had a keybind nobody had ever pressed, and a broken
     * one at that. Apostrophe is not a vanilla binding, it is next to the hand already on the
     * movement keys, and a border display is checked often enough that "go and bind it first" is
     * the wrong toll. Anybody it does collide with re-binds it in one screen; nobody re-binds a
     * key they never learned existed.</p>
     *
     * <p>The other half is not a preference: a key that toggled the grid locally would be a second
     * source of truth about whether borders are on, which is exactly how a button and a command
     * come to disagree. This sends {@code f borders}, so the server decides and a vanilla client
     * typing it gets the same answer in particles.</p>
     *
     * <p>⚠ And it is {@code borders}, plural, because <b>a brigadier literal is not a prefix
     * match</b>. This sent {@code f border} for its whole life and could not have worked; the key
     * was unbound then, so nothing had ever pressed it. The command now answers to both.</p>
     */
    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(BORDER_KEY);
    }

    private static final net.minecraft.client.KeyMapping BORDER_KEY =
            new net.minecraft.client.KeyMapping("key.factions.border",
                    com.mojang.blaze3d.platform.InputConstants.KEY_APOSTROPHE,
                    new net.minecraft.client.KeyMapping.Category(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                    Factions.MODID, "main")));

    private static void onClientTick(ClientTickEvent.Post event) {
        while (BORDER_KEY.consumeClick()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getConnection() != null) {
                mc.getConnection().sendCommand("f borders");
            }
        }
    }
}

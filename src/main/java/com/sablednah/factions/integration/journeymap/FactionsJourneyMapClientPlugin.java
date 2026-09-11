package com.sablednah.factions.integration.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.OptionCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import com.sablednah.factions.Factions;

/**
 * Factions' own controls inside JourneyMap: the claims layer switch, and claim mode.
 *
 * <p>Three things the server plugin cannot do — a toggle in the options screen, a button on the
 * fullscreen toolbar, and knowing where the mouse is. Everything else belongs on the server, where
 * the claims actually live; see {@link FactionsJourneyMapPlugin}.
 *
 * <p>Same package rule as the server half: JourneyMap instantiates this itself after finding the
 * annotation, and nothing outside this package may reference it.
 *
 * <h2>⚠ Claim mode sends a COMMAND</h2>
 *
 * <p>Clicking a chunk runs {@code /f claim <x> <z>} exactly as if it had been typed. No packet of
 * our own, no second code path, and therefore no way for the map to do something a player at a
 * keyboard could not — which is the rule the whole client half of this pair runs on. Every refusal,
 * cost, limit and raid check applies unchanged because it is the same command.
 *
 * <h2>⚠ And whether to offer it is answered by Standards, not guessed</h2>
 *
 * <p>The button appears only when the server has told this client it may claim. That answer already
 * arrives — Standards' action bar syncs a capability set, and {@code factions:claim} is in it when
 * the player is in a faction. Asking that rather than inventing a permission packet means the
 * button and the action bar cannot disagree, and a client cannot show itself a button by lying.
 */
@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = { Factions.MODID })
public class FactionsJourneyMapClientPlugin implements IClientPlugin {

    /**
     * Our own icons, at {@code assets/factions/textures/gui/}.
     *
     * <p>⚠ Never point at JourneyMap's theme assets. Its example mod suggests
     * {@code journeymap:/resources/assets/journeymap/theme/flat/icon/*.png}, which is not where the
     * asset is — the button then renders a null texture, JourneyMap throws inside
     * {@code jm.fullscreen.render()} every frame and closes the map to survive, presenting as "the
     * map crashes when I press J". CityWorld lost a playtest round to exactly that.
     */
    private static final Identifier LAYER_ICON =
            Identifier.fromNamespaceAndPath(Factions.MODID, "textures/gui/claims.png");
    private static final Identifier CLAIM_ICON =
            Identifier.fromNamespaceAndPath(Factions.MODID, "textures/gui/claim_mode.png");

    private BooleanOption showClaims;

    /** What we last read, for while the option is unbound. See {@link #claimsOn()}. */
    private boolean lastKnown = true;

    /**
     * Claim mode, which is deliberately NOT an option.
     *
     * <p>An option is a preference and persists; this is a mode you are in for ten seconds while
     * you draw a border, and leaving it on across a session is how somebody unclaims their capital
     * by right-clicking to pan.</p>
     */
    private boolean claimMode;

    @Override
    public String getModId() {
        return Factions.MODID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        // Every handler wrapped: these run inside JourneyMap's own event bus, during client setup
        // and while the map is open, where an exception is a crash report rather than a log line.
        ClientEventRegistry.OPTIONS_REGISTRY_EVENT.subscribe(Factions.MODID,
                event -> safely(() -> onOptionsRegistry(event)));
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(Factions.MODID,
                event -> safely(() -> onAddonButtons(event)));
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(Factions.MODID,
                event -> safely(() -> onClick(event)));
        // Refusals are chat, and chat is behind the fullscreen map. See MapNotices.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(MapNotices.class);
        Factions.LOGGER.info("JourneyMap client API found — faction claims layer and claim mode added");
    }

    /**
     * ⚠ Creates the option; constructing it registers it.
     *
     * <p><b>Do not read it here.</b> JourneyMap binds an option to its stored config <em>after</em>
     * this event returns, so {@code get()} throws until then — and thrown from client setup that is
     * a crash on the loading screen rather than a warning. Everything reads through
     * {@link #claimsOn()}.</p>
     */
    private void onOptionsRegistry(journeymap.api.v2.client.event.RegistryEvent.OptionsRegistryEvent event) {
        OptionCategory category = new OptionCategory(Factions.MODID, "Factions",
                "Faction territory drawn on the map");
        showClaims = new BooleanOption(category, "showClaims", "Faction claims", true);
    }

    /** The option's value, or the last one we know of while it is unbound. Never throws. */
    private boolean claimsOn() {
        if (showClaims == null) {
            return lastKnown;
        }
        try {
            lastKnown = showClaims.get();
        } catch (RuntimeException notBoundYet) {
            // Left at the last known value; the option catches up when it is next readable.
        }
        return lastKnown;
    }

    private void setClaimsOn(boolean on) {
        lastKnown = on;
        if (showClaims != null) {
            try {
                showClaims.set(on);
            } catch (RuntimeException notBoundYet) {
                // Nothing to do: the value is remembered above and written when binding catches up.
            }
        }
        tellServer(on);
    }

    /** What the server was last told, so a reconnect or a stored preference is re-stated. */
    private Boolean told;

    /**
     * ⚠ <b>Which</b> server was told — the connection object, held weakly, not a boolean.
     *
     * <p>A plain "have we sent it yet" flag survives leaving the world, so the second server you
     * join in one session is never told anything: the value has not changed since the first, so
     * there is nothing to send, and the preference silently applies to one server per launch.
     * Comparing the connection makes "a different server" and "a changed value" the same test.</p>
     *
     * <p>Weak because this plugin outlives every connection JourneyMap sees, and a strong reference
     * to a dead {@code ClientPacketListener} pins the whole network stack of a world you left.</p>
     */
    private java.lang.ref.WeakReference<Object> toldWho = new java.lang.ref.WeakReference<>(null);

    /**
     * ⚠ The half that makes the switch do anything.
     *
     * <p>The overlays are pushed by the <b>server</b>, so writing the option and stopping there
     * flips a tooltip and leaves the territory drawn — which is exactly what the first version did,
     * and it looked like a rendering bug rather than a missing message. It goes as a command, like
     * every other button in this pair, so a vanilla client can say the same thing by typing it.</p>
     *
     * <p>Sent only on a change, because it is chat traffic; {@link #syncServer()} covers the case
     * where there has been no change but the server has never heard.</p>
     */
    private void tellServer(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        if (told != null && told == on && toldWho.get() == connection) {
            return;
        }
        told = on;
        toldWho = new java.lang.ref.WeakReference<>(connection);
        connection.sendCommand(on ? "f map layer on" : "f map layer off");
    }

    /**
     * Re-state the preference to a server that has not heard it.
     *
     * <p>⚠ <b>The stored-option case.</b> JourneyMap persists the option, so a player who turned
     * the layer off last week joins with it off and never touches the button — nothing changes, so
     * nothing is sent, and the server draws territory the client's own switch says is hidden. The
     * server defaults to on and cannot know better. So the preference is re-stated whenever the map
     * opens, which is the first moment it matters and the first moment there is certainly a
     * connection.</p>
     *
     * <p>A different connection counts as never having heard it — see {@link #toldWho}.</p>
     */
    private void syncServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        // ⚠ Only when it is OFF. The server draws the layer by default, so re-stating "on" tells it
        // something it already believes — and every one of those would print a line of chat the
        // player did not ask for, on every join, for the majority who never touch the button. When
        // it IS off, that one line is the explanation for why the map looks bare.
        if (!claimsOn()) {
            tellServer(false);
        }
    }

    /** Logged once, because "the callback never fired" and "the button drew nowhere" look identical. */
    private boolean announcedButtons;

    private void onAddonButtons(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        syncServer();
        if (!announcedButtons) {
            announcedButtons = true;
            Factions.LOGGER.info("Factions: JourneyMap asked for addon buttons; adding {}",
                    mayClaim() ? "claims layer + claim mode" : "claims layer only (claim withheld)");
        }
        event.getThemeButtonDisplay().addThemeToggleButton("Faction claims on", "Faction claims off",
                LAYER_ICON, claimsOn(), button -> setClaimsOn(!claimsOn()));

        // Offered only when the server says this player may claim — see the class note. A button
        // that can only be refused teaches people to ignore buttons.
        if (mayClaim()) {
            event.getThemeButtonDisplay().addThemeToggleButton("Claim mode on", "Claim mode off",
                    CLAIM_ICON, claimMode, button -> claimMode = !claimMode);
        }
    }

    /**
     * Whether the server is currently offering this player {@code /f claim}.
     *
     * <p>Guarded rather than trusted: Standards is a hard dependency, but an older one has no
     * capability set and the honest answer then is "do not offer the button" rather than a crash
     * inside JourneyMap's render.</p>
     */
    private static boolean mayClaim() {
        try {
            return com.sablednah.standards.client.ClientCapabilities.has("factions:claim");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * A click on the fullscreen map, while claim mode is on.
     *
     * <p>Left claims the chunk, right releases it. Cancelled so the click does not also pan or
     * place a waypoint — in claim mode the map is a tool rather than a map, and a click that did
     * both would be a click nobody could predict.</p>
     */
    private void onClick(FullscreenMapEvent.ClickEvent event) {
        if (!claimMode || !mayClaim()) {
            return;
        }
        BlockPos at = event.getLocation();
        if (at == null) {
            return;
        }
        int chunkX = at.getX() >> 4;
        int chunkZ = at.getZ() >> 4;
        String command = event.getButton() == 1
                ? "f unclaim " + chunkX + " " + chunkZ
                : "f claim " + chunkX + " " + chunkZ;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            // Armed BEFORE the send, or a fast local server answers before we are listening.
            MapNotices.expect();
            mc.getConnection().sendCommand(command);
        }
        if (event.isCancellable()) {
            event.cancel();
        }
    }

    private static void safely(Runnable work) {
        try {
            work.run();
        } catch (Throwable t) {
            Factions.LOGGER.error("Factions' JourneyMap client hook failed", t);
        }
    }
}

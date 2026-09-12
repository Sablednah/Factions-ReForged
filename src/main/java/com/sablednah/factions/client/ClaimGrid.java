package com.sablednah.factions.client;

import com.sablednah.factions.ClaimsNearbyPayload;

import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The last claim map the server sent, and nothing else.
 *
 * <p>Deliberately a dumb holder. The renderer reads it every frame and the network thread writes
 * it, so it is one volatile reference to an immutable payload rather than a structure with a lock:
 * a frame either sees the old map or the new one, and both are complete.</p>
 */
public final class ClaimGrid {

    private static volatile ClaimsNearbyPayload current;

    /** Bumped on every accepted payload, so a cached mesh knows it is stale without comparing. */
    private static volatile int version;

    /** The radius we last asked for, so a server that says no is not argued with forever. */
    private static volatile int asked = -1;

    private ClaimGrid() {}

    public static void accept(ClaimsNearbyPayload payload) {
        current = payload;
        version++;
        negotiate(payload.radius());
    }

    /**
     * Tell the server how far this machine would like to see, once.
     *
     * <h2>⚠ Ask once, not until it agrees</h2>
     *
     * <p>The obvious loop is "while what arrived is not what I wanted, ask again", and it never
     * terminates against a server whose ceiling is lower than the request — one command per grid
     * push, forever, quietly. So the guard is on <b>what was asked</b>, not on what came back: a
     * server that clamps 8 to 4 is asked exactly once and then believed.</p>
     *
     * <p>The first grid of a session is also the right moment for it. Doing this on login would
     * spend a command on every player who never turns borders on, and the radius only matters at
     * the instant something is being drawn with it.</p>
     */
    private static void negotiate(int serving) {
        int want = FactionsClientConfig.BORDER_RADIUS.get();
        if (want == serving || want == asked) {
            return;
        }
        asked = want;
        // ⚠ The payload arrives on the network thread and sendCommand must not be called from it.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) {
                mc.getConnection().sendCommand("f borders radius " + want);
            }
        });
    }

    public static ClaimsNearbyPayload current() {
        return current;
    }

    public static int version() {
        return version;
    }

    /**
     * ⚠ Cleared on the way out, or the next world is drawn with the last one's borders.
     *
     * <p>The grid is anchored to absolute chunk coordinates, so a stale map does not merely look
     * wrong — it looks plausible, in the wrong place, in a world where those chunks belong to
     * somebody else entirely.</p>
     */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
        version++;
        // The next server is a different server, and has its own ceiling to be told about.
        asked = -1;
    }
}

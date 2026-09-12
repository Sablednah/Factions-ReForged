package com.sablednah.factions.client;

import com.sablednah.factions.ClaimsNearbyPayload;

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

    private ClaimGrid() {}

    public static void accept(ClaimsNearbyPayload payload) {
        current = payload;
        version++;
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
    }
}

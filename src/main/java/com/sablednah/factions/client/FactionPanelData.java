package com.sablednah.factions.client;

import com.sablednah.factions.FactionPanelPayload;

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
        // Stored, and nothing else. It used to open a screen from here — the answer arriving was
        // what opened it — which was right while the panel WAS a screen and wrong the moment it
        // became a pane you toggle: a reply landing would have re-opened a pane you had just put
        // away, and every promote/kick sends a fresh request. The pane decides whether it is
        // showing; this only decides what it shows.
    }

    public static FactionPanelPayload latest() {
        return latest;
    }

    private FactionPanelData() {}
}

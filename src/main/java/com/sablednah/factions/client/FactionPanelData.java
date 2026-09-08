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
        // Opened here rather than by the button, because the data has to exist before the screen
        // can draw anything. The button asks; the answer arriving is what opens it.
        FactionPanelScreen.openWith(payload);
    }

    public static FactionPanelPayload latest() {
        return latest;
    }

    private FactionPanelData() {}
}

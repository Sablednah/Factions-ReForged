package com.sablednah.factions.integration.journeymap;

import java.util.EnumSet;

import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.server.overlay.OverlayShapeProps;

/**
 * Builds the style block for a map overlay.
 *
 * <p>Its only reason to exist is one import. JourneyMap moved {@code Context} from
 * {@code journeymap.api.v2.client.display} (1.21.11, 26.1) to {@code journeymap.api.v2.common} in
 * 26.2, and {@link OverlayShapeProps} names it. Keeping that import here means <b>this file is the
 * only one in the package that differs between version branches</b>, so every other file
 * cherry-picks across cleanly. If a future JourneyMap moves something else, move that here too.
 *
 * <p>Lifted wholesale from CityWorld's integration, which paid for the lesson across five renames in
 * one release arc. Copied rather than shared: a version-shim is exactly the wrong thing to make two
 * mods agree on, because the whole point is that it differs per branch.</p>
 */
final class OverlayProps {

    private OverlayProps() {}

    /** Every UI and every map type — a claim is a claim on the minimap and the fullscreen alike. */
    static OverlayShapeProps everywhere(int fillColor, float fillOpacity, int strokeColor,
            float strokeWidth, float strokeOpacity, int displayOrder, int minZoom, int maxZoom,
            String label, String title) {
        return new OverlayShapeProps(fillColor, fillOpacity, strokeColor, strokeWidth, strokeOpacity,
                displayOrder, minZoom, maxZoom,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class), label, title);
    }
}

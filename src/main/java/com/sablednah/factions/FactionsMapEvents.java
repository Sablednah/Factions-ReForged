package com.sablednah.factions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * "Something a map would want to redraw has changed."
 *
 * <h2>Why this exists rather than the map polling</h2>
 *
 * <p>Claims move constantly — every {@code /f claim}, every raid, every disband — and a map that
 * redrew on a timer would either lag behind the world or burn a scan of every claim on every tick.
 * A faction taking one chunk should cost one faction's worth of redraw, and nothing else should
 * notice.</p>
 *
 * <h2>Why it is not in {@code api/}</h2>
 *
 * <p>This is Factions' own bookkeeping, not a seam another mod is invited to build on. The seam for
 * "who owns this chunk" already exists and is Standards' claims provider; this only says <em>a
 * redraw is due</em>, which is meaningless to anybody who is not drawing.</p>
 *
 * <p>⚠ A listener that throws is logged and dropped rather than allowed to fail the mutation that
 * notified it. Claiming a chunk must not be able to fail because something that draws maps is
 * broken — the same courtesy CityWorld's generator extends to map mods, for the same reason.</p>
 */
public final class FactionsMapEvents {

    /** What changed, so a listener can redraw only that. */
    public record ClaimsChanged(String factionId, String dimension) {}

    private static final List<Consumer<ClaimsChanged>> CLAIM_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> STANDARD_LISTENERS = new CopyOnWriteArrayList<>();

    /** Told when any faction's claims change, with which faction and which dimension. */
    public static void onClaimsChanged(Consumer<ClaimsChanged> listener) {
        CLAIM_LISTENERS.add(listener);
    }

    /**
     * Told when a standard is planted, broken, captured or moved.
     *
     * <p>No argument: standards are few, and a map that redraws all of them is cheaper than one that
     * works out which one moved. Claims get an argument because a faction can hold hundreds.</p>
     */
    public static void onStandardsChanged(Runnable listener) {
        STANDARD_LISTENERS.add(listener);
    }

    /** {@code factionId} may be null where a change is not attributable to one faction. */
    public static void claimsChanged(String factionId, String dimension) {
        ClaimsChanged event = new ClaimsChanged(factionId, dimension);
        for (Consumer<ClaimsChanged> listener : CLAIM_LISTENERS) {
            safely(() -> listener.accept(event));
        }
    }

    public static void standardsChanged() {
        for (Runnable listener : STANDARD_LISTENERS) {
            safely(listener);
        }
    }

    private static void safely(Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            // Never propagates. See the class note: a broken map must not fail a claim.
            Factions.LOGGER.warn("Factions: a map listener threw ({}); dropping it for this change",
                    e.toString());
        }
    }

    private FactionsMapEvents() {}
}

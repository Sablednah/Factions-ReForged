package com.sablednah.factions;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * Staff editing claimed land — as a <b>state you enter on purpose</b>, never a silent op check.
 *
 * <h2>Why it is a state and not a permission</h2>
 *
 * <p>The obvious implementation is one line in {@link FactionProtection}: if the player is an
 * operator, let them build. It is also the wrong one, and the reason is not security — staff are
 * trusted — it is <b>attention</b>.</p>
 *
 * <p>An always-on override means every operator spends every session able to break somebody's
 * base by accident, with nothing to tell them whose land they are stood on. The mistakes that
 * follow are not malicious and are not rare: a misplaced block in a claim looks exactly like a
 * grief to the faction that finds it, and the person who did it has no idea it happened.</p>
 *
 * <p>So bypassing is a thing you turn on, do the job, and turn off. The protection stays real the
 * rest of the time, which is what makes it a protection.</p>
 *
 * <h2>It resets on logout, deliberately</h2>
 *
 * <p>Held in memory and dropped when the player leaves — the one piece of state in either mod that
 * is <em>designed</em> to be lost. Standards' switches persist across a logout because forgetting
 * you can fly is harmless; forgetting you can edit everybody's land is not. A staff member who
 * logs off mid-job comes back with the protection back on, and has to decide again.</p>
 *
 * <p>That decision is the whole feature. Editing claimed land should cost a thought every time.</p>
 *
 * <h2>Grantable, not op-only</h2>
 *
 * <p>Gated on {@code factions.bypass} through NeoForge's {@code PermissionAPI} — <b>the first
 * permission node this mod has ever declared</b>, and declared for exactly the reason
 * {@code NODES.md} argued one might be needed: a moderator who should be able to undo a grief
 * without also being handed {@code /stop}. It defaults to operators, so a server that configures
 * nothing behaves as before.</p>
 */
public final class FactionBypass {

    /** Who is currently overriding. Almost always empty, so membership is checked cheaply. */
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    /**
     * Whether this player is currently editing through claims.
     *
     * <p>Called from the protection path on every block break, place and interaction, so it opens
     * with the empty check — the feature costs a set lookup on a server where nobody uses it, and
     * nothing at all on one where the set is empty.</p>
     */
    public static boolean isActive(UUID player) {
        return !ACTIVE.isEmpty() && ACTIVE.contains(player);
    }

    /** @return the state it ended up in */
    public static boolean set(ServerPlayer player, boolean on) {
        return set(player.getUUID(), on);
    }

    /**
     * The same, by id.
     *
     * <p>Exists so the self-test can drive the real add-and-drop path — a headless test has no
     * {@link ServerPlayer}, and a test that could only call {@code forget} would be asserting
     * that an absent player is absent.</p>
     */
    public static boolean set(UUID player, boolean on) {
        if (on) {
            ACTIVE.add(player);
        } else {
            ACTIVE.remove(player);
        }
        return on;
    }

    public static boolean toggle(ServerPlayer player) {
        return set(player.getUUID(), !isActive(player.getUUID()));
    }

    /**
     * Drop it on logout. Called from {@link FactionsEvents}, beside the other per-session state.
     *
     * <p>If this is ever missed, the symptom is a staff member silently retaining the override
     * across a relog — which is precisely the thing the design exists to prevent, and which
     * nothing would report.</p>
     */
    public static void forget(UUID player) {
        ACTIVE.remove(player);
    }

    /** For the self-test, which must not leave anybody overriding. */
    public static void clear() {
        ACTIVE.clear();
    }

    public static int active() {
        return ACTIVE.size();
    }

    private FactionBypass() {}
}

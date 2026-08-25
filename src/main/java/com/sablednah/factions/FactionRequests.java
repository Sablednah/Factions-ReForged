package com.sablednah.factions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asking to be let in — the invitation, the other way round.
 *
 * <h2>Why it is not just an invite with the arrow reversed</h2>
 *
 * <p>An invite finds a specific person: an officer knows who they want and says so. A request is
 * sent by somebody who does not know anybody yet, which is exactly the person the invite flow
 * cannot help. Without this, joining a faction requires already being known to one — fine on a
 * server of twenty friends, useless to the player who logged in an hour ago and read the tag in
 * chat.</p>
 *
 * <p><b>Not persisted</b>, for the same reason invites are not (see {@link FactionInvites}). A
 * request accepted three weeks later, by an officer who does not remember it, from a player who
 * has since joined somewhere else, is worse than one that lapsed quietly. Membership is checked
 * again at the moment of acceptance regardless, because the gap between asking and being answered
 * is where the world moves.</p>
 */
public final class FactionRequests {

    /** "factionId|uuid" → when it was asked. */
    private static final Map<String, Long> OPEN = new ConcurrentHashMap<>();

    private static String key(String faction, UUID player) {
        return faction + "|" + player;
    }

    public static void ask(String faction, UUID player) {
        OPEN.put(key(faction, player), System.currentTimeMillis());
    }

    public static boolean pending(String faction, UUID player) {
        return OPEN.containsKey(key(faction, player));
    }

    public static void withdraw(String faction, UUID player) {
        OPEN.remove(key(faction, player));
    }

    /** Everyone waiting on this faction, oldest first — the order they should be answered in. */
    public static List<UUID> forFaction(String faction) {
        String prefix = faction + "|";
        return OPEN.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .sorted(Map.Entry.comparingByValue())
                .map(e -> UUID.fromString(e.getKey().substring(prefix.length())))
                .toList();
    }

    /**
     * Everything this player has asked for, so joining one faction can clear the rest.
     *
     * <p>Leaving them open would let an officer somewhere else accept a member they cannot have,
     * and then have to be told why not.</p>
     */
    public static void forgetPlayer(UUID player) {
        OPEN.keySet().removeIf(k -> k.endsWith("|" + player));
    }

    /** A disbanded faction's requests go with it. */
    public static void forgetFaction(String faction) {
        OPEN.keySet().removeIf(k -> k.startsWith(faction + "|"));
    }

    private FactionRequests() {}
}

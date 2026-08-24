package com.sablednah.factions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open invitations.
 *
 * <p><b>Not persisted, deliberately</b> — unlike Standards' group invites, which are. A faction
 * invite is an act of recruitment in a live conversation; one that survives a restart and gets
 * accepted three weeks later, by somebody the officer no longer remembers asking, is worse than
 * one that quietly lapses. The same reasoning Standards applies to pending teleport requests.</p>
 */
public final class FactionInvites {

    /** "factionId|uuid" → when it was offered. */
    private static final Map<String, Long> OPEN = new ConcurrentHashMap<>();

    private static String key(String faction, UUID player) {
        return faction + "|" + player;
    }

    public static void offer(String faction, UUID player) {
        OPEN.put(key(faction, player), System.currentTimeMillis());
    }

    public static boolean invited(String faction, UUID player) {
        return OPEN.containsKey(key(faction, player));
    }

    public static void revoke(String faction, UUID player) {
        OPEN.remove(key(faction, player));
    }

    /** Everything offered to this player, so /f can list it. */
    public static java.util.List<String> forPlayer(UUID player) {
        String suffix = "|" + player;
        return OPEN.keySet().stream()
                .filter(k -> k.endsWith(suffix))
                .map(k -> k.substring(0, k.length() - suffix.length()))
                .toList();
    }

    /** A disbanded faction's invitations go with it. */
    public static void forgetFaction(String faction) {
        OPEN.keySet().removeIf(k -> k.startsWith(faction + "|"));
    }

    private FactionInvites() {}
}

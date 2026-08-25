package com.sablednah.factions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.api.chat.Chat;
import com.sablednah.standards.api.chat.ChatRouter;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

/**
 * Talking to your faction, and to your allies.
 *
 * <h2>Through the seam, not around it</h2>
 *
 * <p>This is a {@link ChatRouter} rather than our own {@code ServerChatEvent} listener, and the
 * difference is not stylistic. A channel that cancels the event itself runs <em>before</em>
 * Standards' checks, so a muted player flips to faction chat and talks — and a mute that silences
 * only public chat is not a mute. Their AFK marker never clears either, so they stay listed as
 * away while holding a conversation.</p>
 *
 * <p>By the time {@link #route} is called the sender is known not to be muted and their AFK marker
 * is already cleared. The seam exists so that gate cannot be skipped by anybody, including us.</p>
 *
 * <h2>Ignores do not apply here, deliberately</h2>
 *
 * <p>The seam leaves that judgement to the channel, and for a small opt-in channel the answer is
 * no. You chose to be in this faction and so did they; if that is unbearable there is
 * {@code /f kick} and there is leaving. Silently dropping a member's words inside their own
 * faction chat produces a conversation where one person is answering questions nobody else saw
 * asked.</p>
 */
public final class FactionChat {

    /** Where each player is currently speaking. Absent means public — the default costs nothing. */
    private static final Map<UUID, Channel> SPEAKING = new ConcurrentHashMap<>();

    /** Staff watching every faction channel. */
    private static final java.util.Set<UUID> SPIES = ConcurrentHashMap.newKeySet();

    public enum Channel {
        PUBLIC("public"),
        FACTION("faction"),
        ALLY("ally");

        private final String key;

        Channel(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** Cycle, so {@code /f chat} with no argument keeps working the way a toggle would. */
        public Channel next() {
            return switch (this) {
                case PUBLIC -> FACTION;
                case FACTION -> ALLY;
                case ALLY -> PUBLIC;
            };
        }
    }

    public static void install() {
        Chat.registerRouter(new ChatRouter() {
            @Override
            public String id() {
                return "factions:chat";
            }

            @Override
            public int priority() {
                return 100;
            }

            @Override
            public boolean route(ServerPlayer sender, String message) {
                Channel channel = channelOf(sender);
                if (channel == Channel.PUBLIC) {
                    return false; // not ours; let the server have it
                }
                return send(sender, channel, message);
            }
        });
    }

    public static Channel channelOf(ServerPlayer player) {
        return SPEAKING.getOrDefault(player.getUUID(), Channel.PUBLIC);
    }

    public static void setChannel(ServerPlayer player, Channel channel) {
        if (channel == Channel.PUBLIC) {
            SPEAKING.remove(player.getUUID());
        } else {
            SPEAKING.put(player.getUUID(), channel);
        }
    }

    /**
     * Not persisted, and not carried across a disconnect.
     *
     * <p>Coming back from a crash still talking to your faction is how a private remark reaches
     * the wrong room — or, worse, how a public remark does not reach the room you thought it was
     * in. Log in speaking to everybody, always.</p>
     */
    public static void forget(UUID player) {
        SPEAKING.remove(player);
        SPIES.remove(player);
    }

    public static boolean toggleSpy(ServerPlayer player) {
        UUID id = player.getUUID();
        if (SPIES.remove(id)) {
            return false;
        }
        SPIES.add(id);
        return true;
    }

    /**
     * Deliver one message to a channel.
     *
     * @return true if it was delivered — false means the sender was told why not, and the message
     *         must <b>not</b> fall through to public chat. Saying something to your faction and
     *         having it broadcast to the server because you had left the faction is the single
     *         worst thing this class could do.
     */
    public static boolean send(ServerPlayer sender, Channel channel, String message) {
        MinecraftServer server = sender.level().getServer();
        FactionStore store = FactionStore.get(server);
        Optional<FactionStore.Faction> mine = store.of(sender.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(sender, Lang.get("msg.factions.chat_no_faction"));
            setChannel(sender, Channel.PUBLIC);
            return true; // claimed and answered; never leaks to the server
        }

        // What the player typed is text, never formatting — the same rule public chat follows.
        String body = Feedback.stripCodes(message);
        String rendered = Lang.fmt(channel == Channel.ALLY
                        ? "msg.factions.chat_ally" : "msg.factions.chat_faction",
                "faction", mine.get().name(),
                "tag", mine.get().tag().isEmpty() ? mine.get().name() : mine.get().tag(),
                "player", sender.getName().getString(),
                "message", body);

        int reached = 0;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (hears(store, mine.get(), channel, viewer)) {
                Feedback.chat(viewer, rendered);
                reached++;
            }
        }

        // Staff see it too, marked as overheard so nobody mistakes it for chat they were in.
        String spied = Lang.fmt("msg.factions.chat_spy", "channel", channel.key(),
                "faction", mine.get().name(), "player", sender.getName().getString(),
                "message", body);
        for (UUID spy : SPIES) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(spy);
            if (watcher != null && !hears(store, mine.get(), channel, watcher)) {
                Feedback.chat(watcher, spied);
            }
        }

        // Talking to an empty room should say so. Otherwise a lone member types into the void and
        // concludes the channel is broken rather than that nobody else is on.
        if (reached <= 1) {
            Feedback.chat(sender, Lang.get("msg.factions.chat_alone"));
        }
        // Logged like vanilla logs chat, or a report of what was said in a private channel cannot
        // be checked by anybody.
        Factions.LOGGER.info("[{} {}] <{}> {}", channel.key(), mine.get().name(),
                sender.getName().getString(), body);
        return true;
    }

    private static boolean hears(FactionStore store, FactionStore.Faction speaking,
            Channel channel, ServerPlayer viewer) {
        Optional<FactionStore.Faction> theirs = store.of(viewer.getUUID());
        if (theirs.isEmpty()) {
            return false;
        }
        if (theirs.get().id().equals(speaking.id())) {
            return true;
        }
        return channel == Channel.ALLY
                && store.relation(speaking.id(), theirs.get().id()) == FactionStore.Relation.ALLY;
    }

    private FactionChat() {}
}

package com.sablednah.factions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntBiFunction;

import com.sablednah.standards.api.reputation.Reputation;
import com.sablednah.standards.api.reputation.ReputationEvent;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * A faction that answers alliance offers by reputation rather than by anybody typing.
 *
 * <h2>What it is for</h2>
 *
 * <p>An NPC-run faction — a quest camp, a town guard — has a leader who never logs in, so an
 * alliance offered to it would wait for ever. A link hands its side of the handshake to a
 * Standards reputation standing instead: earn the camp's trust through quests and it offers back;
 * lose it and it withdraws. Everything else about alliances is unchanged, which is the point —
 * allies open the doors because allies always did, and nothing in protection had to learn about
 * reputation.</p>
 *
 * <h2>Whose reputation</h2>
 *
 * <p>The <b>leader's</b> of the faction asking. A faction has no reputation of its own, and
 * "any member" would let one trusted recruit carry an entire faction of strangers through the
 * gate. The leader is the one person who answers for who else is let in.</p>
 *
 * <h2>Two thresholds, not one</h2>
 *
 * <p>Allied at {@code allyAt} and above, withdrawn below {@code revokeBelow}, and left exactly as
 * it is anywhere between. With one threshold, a leader sitting on it would ally and un-ally on
 * every quest turned in and every point lost — and each flip is an announcement to the whole
 * faction. The gap is what lets the relation mean something.</p>
 *
 * <h2>Withdrawn, not cancelled</h2>
 *
 * <p>A revocation takes back only the linked faction's side. The asking faction's offer stays,
 * so the relation falls to "offered, not returned" — and climbs back by itself if the leader earns
 * the trust again, with nobody having to remember to re-offer.</p>
 *
 * <h2>Or nobody asks at all: {@code auto}</h2>
 *
 * <p>A link with a fifth field, {@code "Camp Okafor|camp|80|40|auto"}, forms the alliance itself:
 * the moment a faction's leader reaches {@code allyAt}, both sides are declared, and below
 * {@code revokeBelow} both are taken down again. Asked for by the ZARP pack, because a player who
 * earns the camp's trust through quests has no reason to know that an alliance is something
 * <em>they</em> have to offer — the camp's doors stayed shut long after the trust was earned.</p>
 *
 * <p>Under {@code auto} the relation belongs to the standing, not to anybody's declaration, so
 * {@code /f neutral} towards the camp is refused with the reason rather than accepted: it would
 * otherwise hold until the next quest turned in, and a relation that quietly comes back is a
 * command reporting one thing while the world does another.</p>
 *
 * <p>Nothing here touches war. A linked faction is expected to be peaceful, and peaceful already
 * refuses enemy declarations in both directions.</p>
 */
public final class FactionReputationLinks {

    /** One configured link. Standing is normalised the way Standards normalises it. */
    public record Link(String faction, String standing, int allyAt, int revokeBelow,
            boolean auto) {

        /** A link that answers offers, the original kind. */
        public Link(String faction, String standing, int allyAt, int revokeBelow) {
            this(faction, standing, allyAt, revokeBelow, false);
        }

        /**
         * {@code "Camp Okafor|camp|80|40"}, or with {@code |auto} on the end. Empty for anything
         * malformed, which is logged by the caller rather than thrown — a typo in one link should
         * not take the others down.
         */
        public static Optional<Link> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String[] bits = raw.split("\\|", -1);
            if (bits.length != 4 && bits.length != 5) {
                return Optional.empty();
            }
            // Spelled out rather than "anything in the fifth field": a typo there would otherwise
            // fall back to the handshake, and look like the option not working.
            boolean auto = bits.length == 5;
            if (auto && !bits[4].trim().equalsIgnoreCase("auto")) {
                return Optional.empty();
            }
            String faction = bits[0].trim();
            String standing = Reputation.normalise(bits[1]);
            if (faction.isEmpty() || standing.isEmpty()) {
                return Optional.empty();
            }
            try {
                int allyAt = Integer.parseInt(bits[2].trim());
                int revokeBelow = Integer.parseInt(bits[3].trim());
                if (revokeBelow > allyAt) {
                    return Optional.empty(); // would ally and revoke at the same number
                }
                return Optional.of(new Link(faction, standing, allyAt, revokeBelow, auto));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    /** What the linked faction should do about one asker. */
    public enum Decision { ACCEPT, WITHDRAW, KEEP }

    /**
     * The whole rule, pure so the self-test runs the real thing.
     *
     * @param offered  the asker has declared ally on the linked faction
     * @param returned the linked faction has declared ally back
     * @param rep      the asker's leader's standing
     */
    public static Decision decide(Link link, boolean offered, boolean returned, int rep) {
        if (link.auto()) {
            // Both sides, either way: nobody offered, so there is nobody's offer to leave standing.
            if (rep >= link.allyAt() && !(offered && returned)) {
                return Decision.ACCEPT;
            }
            if (rep < link.revokeBelow() && returned) {
                return Decision.WITHDRAW;
            }
            return Decision.KEEP;
        }
        if (offered && !returned && rep >= link.allyAt()) {
            return Decision.ACCEPT;
        }
        // Withdrawn also when the asker has taken its own offer back: a one-sided declaration by
        // the camp would otherwise sit there, and re-offering would ally instantly whatever the
        // leader's reputation had become in the meantime.
        if (returned && (!offered || rep < link.revokeBelow())) {
            return Decision.WITHDRAW;
        }
        return Decision.KEEP;
    }

    /** Every well-formed link in config. */
    public static List<Link> links() {
        List<Link> out = new ArrayList<>();
        for (String raw : FactionsConfig.REPUTATION_LINKS.get()) {
            Link.parse(raw).ifPresentOrElse(out::add,
                    () -> Factions.LOGGER.warn("Ignoring reputation link '{}': expected "
                            + "\"faction|standing|allyAt|revokeBelow\" or the same with \"|auto\", "
                            + "and allyAt >= revokeBelow",
                            raw));
        }
        return out;
    }

    /** The link that governs this faction, if any. */
    public static Optional<Link> linkFor(FactionStore store, FactionStore.Faction f) {
        for (Link link : links()) {
            if (resolve(store, link).map(l -> l.id().equals(f.id())).orElse(false)) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }

    private static Optional<FactionStore.Faction> resolve(FactionStore store, Link link) {
        return store.byId(link.faction()).or(() -> store.lookup(link.faction()));
    }

    /**
     * Bring one asker's relation with every linked faction into line.
     *
     * @return the decisions that changed something, for the caller to announce
     */
    static List<Applied> reconcile(FactionStore store, FactionStore.Faction asker,
            ToIntBiFunction<UUID, String> rep) {
        List<Applied> out = new ArrayList<>();
        for (Link link : links()) {
            Optional<Applied> a = reconcile(store, asker, link, rep);
            a.ifPresent(out::add);
        }
        return out;
    }

    /** One link. Package-private so the self-test can supply its own link and reputation. */
    static Optional<Applied> reconcile(FactionStore store, FactionStore.Faction asker, Link link,
            ToIntBiFunction<UUID, String> rep) {
        Optional<FactionStore.Faction> linked = resolve(store, link);
        if (linked.isEmpty() || linked.get().id().equals(asker.id())) {
            return Optional.empty();
        }
        FactionStore.Faction camp = linked.get();
        boolean offered = asker.allies().contains(camp.id());
        boolean returned = camp.allies().contains(asker.id());
        if (!offered && !returned && !link.auto()) {
            return Optional.empty(); // nothing to answer, and no reason to ask the provider
        }
        int standing = rep.applyAsInt(asker.leader(), link.standing());
        Decision d = decide(link, offered, returned, standing);
        switch (d) {
            case ACCEPT -> {
                store.declare(camp.id(), asker.id(), FactionStore.Relation.ALLY);
                if (link.auto()) {
                    store.declare(asker.id(), camp.id(), FactionStore.Relation.ALLY);
                }
            }
            case WITHDRAW -> {
                store.declare(camp.id(), asker.id(), FactionStore.Relation.NEUTRAL);
                if (link.auto()) {
                    store.declare(asker.id(), camp.id(), FactionStore.Relation.NEUTRAL);
                }
            }
            case KEEP -> {
                return Optional.empty();
            }
        }
        return Optional.of(new Applied(d, camp, asker, link, standing));
    }

    record Applied(Decision decision, FactionStore.Faction linked, FactionStore.Faction asker,
            Link link, int standing) {}

    /** The live reputation, or "no opinion" when Standards has no provider. */
    private static int liveRep(UUID player, String standing) {
        return Reputation.get(player, standing);
    }

    /**
     * Called straight after {@code /f ally}, so the asker learns the answer in the same breath.
     *
     * @return true if a link answered, so the caller does not also say "offered, waiting"
     */
    static boolean afterOffer(MinecraftServer server, FactionStore store, ServerPlayer player,
            FactionStore.Faction asker, FactionStore.Faction them) {
        Optional<Link> link = linkFor(store, them);
        if (link.isEmpty() || !Reputation.isAvailable()) {
            return false;
        }
        FactionStore.Faction fresh = store.byId(asker.id()).orElse(asker);
        Optional<Applied> a = reconcile(store, fresh, link.get(), FactionReputationLinks::liveRep);
        if (a.isPresent() && a.get().decision() == Decision.ACCEPT) {
            announce(server, a.get());
            return true;
        }
        // Waiting on trust rather than on a person — say so, or the asker sits there expecting a
        // leader who will never log in to reply.
        int standing = liveRep(fresh.leader(), link.get().standing());
        Feedback.chat(player, Lang.fmt(link.get().auto()
                        ? "msg.factions.rep_link_waiting_auto" : "msg.factions.rep_link_waiting",
                "name", them.name(),
                "band", Reputation.band(link.get().standing(), standing)
                        .orElse(String.valueOf(standing)),
                "need", Reputation.band(link.get().standing(), link.get().allyAt())
                        .orElse(String.valueOf(link.get().allyAt()))));
        return true;
    }

    /**
     * Whether a declaration other than ally towards this faction should be refused, because an
     * {@code auto} link owns the relation. Said to the player here, so the caller only stops.
     */
    static boolean refuseDeclaration(FactionStore store, ServerPlayer player,
            FactionStore.Faction them) {
        Optional<Link> link = linkFor(store, them);
        if (link.isEmpty() || !link.get().auto()) {
            return false;
        }
        Feedback.chat(player, Lang.fmt("msg.factions.rep_link_auto_fixed",
                "name", them.name()));
        return true;
    }

    /**
     * A faction has just been founded, and its founder is its leader. Under an {@code auto} link
     * a player who earned the trust before they had a faction to carry it is allied on the spot,
     * rather than at the next quest they happen to turn in.
     */
    static void afterFounded(MinecraftServer server, FactionStore store,
            FactionStore.Faction founded) {
        if (!Reputation.isAvailable()) {
            return;
        }
        for (Link link : links()) {
            if (!link.auto()) {
                continue;
            }
            FactionStore.Faction fresh = store.byId(founded.id()).orElse(founded);
            reconcile(store, fresh, link, FactionReputationLinks::liveRep)
                    .ifPresent(a -> announce(server, a));
        }
    }

    /**
     * After any other declaration towards a linked faction — chiefly {@code /f neutral}, which
     * takes the asker's offer back and so must take the camp's reply back with it. Silent: the
     * asker did it themselves and has already been told.
     */
    static void afterOtherDeclaration(FactionStore store, FactionStore.Faction asker,
            FactionStore.Faction them) {
        Optional<Link> link = linkFor(store, them);
        if (link.isEmpty()) {
            return;
        }
        FactionStore.Faction fresh = store.byId(asker.id()).orElse(asker);
        reconcile(store, fresh, link.get(), FactionReputationLinks::liveRep);
    }

    /** Somebody's standing moved. Only a leader's matters, and only for their own faction. */
    @SubscribeEvent
    static void onReputation(ReputationEvent event) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        FactionStore store = FactionStore.get(server);
        Optional<FactionStore.Faction> f = store.of(event.getPlayer());
        if (f.isEmpty() || !f.get().leader().equals(event.getPlayer())) {
            return;
        }
        String standing = Reputation.normalise(event.getStanding());
        for (Link link : links()) {
            if (!link.standing().equals(standing)) {
                continue;
            }
            FactionStore.Faction fresh = store.byId(f.get().id()).orElse(f.get());
            // The event's own value, not a re-read: it is the number that just landed.
            reconcile(store, fresh, link, (p, s) -> event.getAfter())
                    .ifPresent(a -> announce(server, a));
        }
    }

    /**
     * Config is authoritative. A link added, removed or re-thresholded while the server was down
     * takes effect on start, rather than waiting for somebody's reputation to happen to move.
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (!Reputation.isAvailable() || links().isEmpty()) {
            return;
        }
        FactionStore store = FactionStore.get(event.getServer());
        for (FactionStore.Faction f : store.all()) {
            FactionStore.Faction fresh = store.byId(f.id()).orElse(f);
            for (Applied a : reconcile(store, fresh, FactionReputationLinks::liveRep)) {
                Factions.LOGGER.info("Reputation link: {} {} {} (leader standing {} in '{}')",
                        a.linked().name(),
                        a.decision() == Decision.ACCEPT ? "allied with" : "withdrew from",
                        a.asker().name(), a.standing(), a.link().standing());
            }
        }
    }

    /**
     * Tell every online member, on screen as well as in chat.
     *
     * <p>A title, because this is the moment the camp's doors and chests open — Sable's words were
     * "a big 'you can use camp items now'". A chat line scrolls away under whatever else is being
     * said, and a player who missed it goes on walking round the camp's chests. Chat as well,
     * because a title is gone in seconds and chat is where somebody looks to find out what
     * happened. Withdrawal gets a shorter, quieter one: it matters, but it is not a reward.</p>
     */
    private static void announce(MinecraftServer server, Applied a) {
        boolean allied = a.decision() == Decision.ACCEPT;
        String key = allied ? "msg.factions.rep_link_allied"
                : a.link().auto() ? "msg.factions.rep_link_withdrawn_auto"
                : "msg.factions.rep_link_withdrawn";
        String message = Lang.fmt(key, "name", a.linked().name());
        String prefix = allied ? "msg.factions.rep_link_allied_" : "msg.factions.rep_link_withdrawn_";
        var title = Feedback.colored(Lang.fmt(prefix + "title", "name", a.linked().name()));
        var subtitle = Feedback.colored(Lang.fmt(prefix + "subtitle", "name", a.linked().name()));
        var timing = allied
                ? new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                        10, 70, 20)
                : new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                        10, 40, 20);
        for (UUID member : a.asker().memberIds()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                Feedback.chat(online, message);
                // Timing first, then subtitle, then title: the title packet is what shows them,
                // and a subtitle sent after it waits for the next title.
                online.connection.send(timing);
                online.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                                subtitle));
                online.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                title));
            }
        }
    }

    private FactionReputationLinks() {}
}

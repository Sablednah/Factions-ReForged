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
 * <p>Nothing here touches war. A linked faction is expected to be peaceful, and peaceful already
 * refuses enemy declarations in both directions.</p>
 */
public final class FactionReputationLinks {

    /** One configured link. Standing is normalised the way Standards normalises it. */
    public record Link(String faction, String standing, int allyAt, int revokeBelow) {

        /**
         * {@code "Camp Okafor|camp|80|40"}. Empty for anything malformed, which is logged by the
         * caller rather than thrown — a typo in one link should not take the others down.
         */
        public static Optional<Link> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String[] bits = raw.split("\\|", -1);
            if (bits.length != 4) {
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
                return Optional.of(new Link(faction, standing, allyAt, revokeBelow));
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
                            + "\"faction|standing|allyAt|revokeBelow\" with allyAt >= revokeBelow",
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
        if (!offered && !returned) {
            return Optional.empty(); // nothing to answer, and no reason to ask the provider
        }
        int standing = rep.applyAsInt(asker.leader(), link.standing());
        Decision d = decide(link, offered, returned, standing);
        switch (d) {
            case ACCEPT -> store.declare(camp.id(), asker.id(), FactionStore.Relation.ALLY);
            case WITHDRAW -> store.declare(camp.id(), asker.id(), FactionStore.Relation.NEUTRAL);
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
        Feedback.chat(player, Lang.fmt("msg.factions.rep_link_waiting",
                "name", them.name(),
                "band", Reputation.band(link.get().standing(), standing)
                        .orElse(String.valueOf(standing)),
                "need", Reputation.band(link.get().standing(), link.get().allyAt())
                        .orElse(String.valueOf(link.get().allyAt()))));
        return true;
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

    private static void announce(MinecraftServer server, Applied a) {
        String key = a.decision() == Decision.ACCEPT
                ? "msg.factions.rep_link_allied" : "msg.factions.rep_link_withdrawn";
        String message = Lang.fmt(key, "name", a.linked().name());
        for (UUID member : a.asker().memberIds()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                Feedback.chat(online, message);
            }
        }
    }

    private FactionReputationLinks() {}
}

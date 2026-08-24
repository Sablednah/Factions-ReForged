package com.sablednah.factions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * {@code /f} — everything a faction does.
 *
 * <p>One tree, and a short one, because {@code /f} is typed constantly and by people who are
 * usually being shot at. The verbs are the ones MassiveCraft's players already have in their
 * fingers — {@code claim}, {@code home}, {@code ally} — because that muscle memory is worth more
 * than any improvement a rename could buy.</p>
 */
public final class FactionCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal) {
        return Commands.literal(literal)
                .executes(FactionCommands::mine)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FactionCommands::create)))
                .then(Commands.literal("disband").executes(FactionCommands::disband))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FactionCommands::invite)))
                .then(Commands.literal("join")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests(FactionCommands::suggestFactions)
                                .executes(FactionCommands::join)))
                .then(Commands.literal("leave").executes(FactionCommands::leave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(FactionCommands::kick)))
                .then(Commands.literal("promote")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> rank(ctx, true))))
                .then(Commands.literal("demote")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> rank(ctx, false))))
                .then(Commands.literal("claim").executes(FactionCommands::claim))
                .then(Commands.literal("unclaim").executes(FactionCommands::unclaim))
                .then(Commands.literal("unclaimall").executes(FactionCommands::unclaimAll))
                .then(Commands.literal("sethome").executes(FactionCommands::setHome))
                .then(Commands.literal("home").executes(FactionCommands::home))
                .then(Commands.literal("tag")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(FactionCommands::tag)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FactionCommands::rename)))
                .then(Commands.literal("peaceful").executes(FactionCommands::peaceful))
                .then(relation("ally", FactionStore.Relation.ALLY))
                .then(relation("enemy", FactionStore.Relation.ENEMY))
                .then(relation("neutral", FactionStore.Relation.NEUTRAL))
                .then(Commands.literal("list").executes(FactionCommands::list))
                .then(Commands.literal("who")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests(FactionCommands::suggestFactions)
                                .executes(ctx -> who(ctx,
                                        StringArgumentType.getString(ctx, "faction")))))
                .then(Commands.literal("map")
                        .executes(ctx -> map(ctx, false))
                        .then(Commands.literal("item").executes(ctx -> map(ctx, true))))
                .then(Commands.literal("borders").executes(FactionCommands::borders));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> relation(
            String literal, FactionStore.Relation relation) {
        return Commands.literal(literal)
                .then(Commands.argument("faction", StringArgumentType.word())
                        .suggests(FactionCommands::suggestFactions)
                        .executes(ctx -> declare(ctx, relation)));
    }

    // --- membership ---

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Optional<FactionStore.Faction> made = store(ctx).create(name, player.getUUID());
        if (made.isEmpty()) {
            Feedback.chat(player, store(ctx).of(player.getUUID()).isPresent()
                    ? Lang.get("msg.factions.already_in_one")
                    : Lang.fmt("msg.factions.name_taken", "name", name));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.factions.created", "name", made.get().name()));
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            return 0;
        }
        announce(ctx, f.get(), Lang.fmt("msg.factions.disbanded", "name", f.get().name()), null);
        store(ctx).disband(f.get().id());
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        if (store(ctx).of(target.getUUID()).isPresent()) {
            Feedback.chat(player, Lang.fmt("msg.factions.they_are_in_one",
                    "player", target.getName().getString()));
            return 0;
        }
        FactionInvites.offer(f.get().id(), target.getUUID());
        Feedback.chat(player, Lang.fmt("msg.factions.invited",
                "player", target.getName().getString()));
        Feedback.chat(target, Lang.fmt("msg.factions.invite_received",
                "player", player.getName().getString(), "name", f.get().name()));
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "faction");
        Optional<FactionStore.Faction> target = store(ctx).lookup(name);
        if (target.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.factions.unknown", "name", name));
            return 0;
        }
        if (!FactionInvites.invited(target.get().id(), player.getUUID())) {
            Feedback.chat(player, Lang.fmt("msg.factions.not_invited", "name", target.get().name()));
            return 0;
        }
        if (!store(ctx).addMember(target.get().id(), player.getUUID(), FactionStore.Rank.MEMBER)) {
            Feedback.chat(player, Lang.get("msg.factions.already_in_one"));
            return 0;
        }
        FactionInvites.revoke(target.get().id(), player.getUUID());
        Feedback.chat(player, Lang.fmt("msg.factions.joined", "name", target.get().name()));
        announce(ctx, target.get(), Lang.fmt("msg.factions.member_joined",
                "player", player.getName().getString()), player.getUUID());
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = store(ctx).of(player.getUUID());
        if (f.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.none"));
            return 0;
        }
        boolean leader = f.get().leader().equals(player.getUUID());
        if (leader && f.get().members().size() > 1) {
            // Leaving as leader would disband a faction that still has people in it, and they
            // would find out by their land vanishing. Make it a deliberate act instead.
            Feedback.chat(player, Lang.get("msg.factions.leader_must_disband"));
            return 0;
        }
        announce(ctx, f.get(), Lang.fmt("msg.factions.member_left",
                "player", player.getName().getString()), player.getUUID());
        store(ctx).removeMember(f.get().id(), player.getUUID());
        Feedback.chat(player, Lang.fmt("msg.factions.you_left", "name", f.get().name()));
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        Optional<UUID> target = lookupPlayer(ctx, name);
        if (target.isEmpty() || !f.get().contains(target.get())) {
            Feedback.chat(player, Lang.fmt("msg.factions.not_a_member", "player", name));
            return 0;
        }
        if (target.get().equals(player.getUUID())) {
            Feedback.chat(player, Lang.get("msg.factions.kick_self"));
            return 0;
        }
        // An officer cannot kick an equal or a superior, or two officers can trade blows until
        // one of them happens to be online alone.
        FactionStore.Rank theirs = f.get().rankOf(target.get());
        FactionStore.Rank mine = f.get().rankOf(player.getUUID());
        if (theirs != null && mine != null && theirs.atLeast(mine)) {
            Feedback.chat(player, Lang.get("msg.factions.outranked"));
            return 0;
        }
        store(ctx).removeMember(f.get().id(), target.get());
        Feedback.chat(player, Lang.fmt("msg.factions.kicked", "player", name));
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(target.get());
        if (online != null) {
            Feedback.chat(online, Lang.fmt("msg.factions.you_were_kicked", "name", f.get().name()));
        }
        return 1;
    }

    private static int rank(CommandContext<CommandSourceStack> ctx, boolean up)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            return 0;
        }
        Optional<UUID> target = lookupPlayer(ctx, name);
        if (target.isEmpty() || !f.get().contains(target.get())) {
            Feedback.chat(player, Lang.fmt("msg.factions.not_a_member", "player", name));
            return 0;
        }
        FactionStore.Rank now = f.get().rankOf(target.get());
        FactionStore.Rank next = up
                ? (now == FactionStore.Rank.MEMBER ? FactionStore.Rank.OFFICER : null)
                : (now == FactionStore.Rank.OFFICER ? FactionStore.Rank.MEMBER : null);
        if (next == null) {
            Feedback.chat(player, Lang.get("msg.factions.rank_unchanged"));
            return 0;
        }
        store(ctx).setRank(f.get().id(), target.get(), next);
        Feedback.chat(player, Lang.fmt("msg.factions.rank_set",
                "player", name, "rank", Lang.get("msg.factions.rank." + next.key())));
        return 1;
    }

    // --- land ---

    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        ServerLevel level = player.level();
        String dim = FactionBridge.dimensionOf(level);
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        FactionStore store = store(ctx);

        Optional<String> owner = store.ownerOf(dim, chunk.x, chunk.z);
        if (owner.isPresent()) {
            Feedback.chat(player, owner.get().equals(f.get().id())
                    ? Lang.get("msg.factions.already_yours")
                    : Lang.fmt("msg.factions.claimed_by_other",
                            "name", store.byId(owner.get()).map(FactionStore.Faction::name).orElse("?")));
            return 0;
        }

        int held = store.claimCount(f.get().id());
        int perMember = FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get();
        int limit = perMember < 0 ? -1 : perMember * f.get().members().size();
        if (limit >= 0 && held >= limit) {
            // Scaling with membership is what stops one person fencing off a continent, and it
            // gives recruiting a point beyond the numbers.
            Feedback.chat(player, Lang.fmt("msg.factions.claim_limit",
                    "held", held, "limit", limit, "members", f.get().members().size()));
            return 0;
        }
        if (FactionsConfig.REQUIRE_CONNECTED_CLAIMS.get() && held > 0
                && !store.touchesOwnLand(dim, chunk.x, chunk.z, f.get().id())) {
            Feedback.chat(player, Lang.get("msg.factions.must_connect"));
            return 0;
        }

        store.claim(dim, chunk.x, chunk.z, f.get().id());
        Feedback.chat(player, Lang.fmt("msg.factions.claimed",
                "x", chunk.x, "z", chunk.z, "held", held + 1,
                "limit", limit < 0 ? Lang.get("msg.factions.no_limit") : String.valueOf(limit)));
        return 1;
    }

    private static int unclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        String dim = FactionBridge.dimensionOf(player.level());
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        Optional<String> owner = store(ctx).ownerOf(dim, chunk.x, chunk.z);
        if (owner.isEmpty() || !owner.get().equals(f.get().id())) {
            Feedback.chat(player, Lang.get("msg.factions.not_yours"));
            return 0;
        }
        store(ctx).unclaim(dim, chunk.x, chunk.z);
        Feedback.chat(player, Lang.fmt("msg.factions.unclaimed", "x", chunk.x, "z", chunk.z));
        return 1;
    }

    private static int unclaimAll(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            return 0;
        }
        int removed = store(ctx).unclaimAll(f.get().id());
        Feedback.chat(player, Lang.fmt("msg.factions.unclaimed_all", "count", removed));
        announce(ctx, f.get(), Lang.fmt("msg.factions.unclaimed_all_others",
                "player", player.getName().getString(), "count", removed), player.getUUID());
        return removed;
    }

    // --- home ---

    private static int setHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        String dim = FactionBridge.dimensionOf(player.level());
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        Optional<String> owner = store(ctx).ownerOf(dim, chunk.x, chunk.z);
        if (owner.isEmpty() || !owner.get().equals(f.get().id())) {
            // A home outside your own land is a home an enemy can camp with impunity.
            Feedback.chat(player, Lang.get("msg.factions.home_must_be_claimed"));
            return 0;
        }
        Waypoint here = Waypoint.of(player);
        store(ctx).setHome(f.get().id(), here);
        Feedback.chat(player, Lang.fmt("msg.factions.home_set", "place", here.describe()));
        Feedback.warnIfUnreachable(player, here);
        announce(ctx, f.get(), Lang.fmt("msg.factions.home_set_others",
                "player", player.getName().getString()), player.getUUID());
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = store(ctx).of(player.getUUID());
        if (f.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.none"));
            return 0;
        }
        if (f.get().home().isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.no_home"));
            return 0;
        }
        // Standards' teleport, so the warmup, the safe landing and the cancel-on-move all apply —
        // including the anti-combat-log half, which is the whole point on a PvP server.
        Teleports.Attempt attempt = Teleports.request(player, f.get().home().get(), true,
                Lang.fmt("msg.factions.home_went", "name", f.get().name()));
        return com.sablednah.standards.neoforge.commands.MoveCommands.report(player, attempt)
                ? 1 : 0;
    }

    // --- identity and relations ---

    private static int tag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String tag = StringArgumentType.getString(ctx, "tag");
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            return 0;
        }
        String wanted = tag.equals("-") ? "" : tag;
        if (wanted.length() > 5) {
            Feedback.chat(player, Lang.fmt("msg.factions.tag_too_long", "max", 5));
            return 0;
        }
        if (!store(ctx).setTag(f.get().id(), wanted)) {
            Feedback.chat(player, Lang.fmt("msg.factions.tag_taken", "tag", wanted));
            return 0;
        }
        Feedback.chat(player, wanted.isEmpty()
                ? Lang.get("msg.factions.tag_cleared")
                : Lang.fmt("msg.factions.tag_set", "tag", wanted));
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            return 0;
        }
        if (!store(ctx).rename(f.get().id(), name)) {
            Feedback.chat(player, Lang.fmt("msg.factions.name_taken", "name", name));
            return 0;
        }
        announce(ctx, f.get(), Lang.fmt("msg.factions.renamed",
                "old", f.get().name(), "name", name), null);
        return 1;
    }

    private static int peaceful(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            return 0;
        }
        boolean now = !f.get().peaceful();
        store(ctx).setPeaceful(f.get().id(), now);
        announce(ctx, f.get(), Lang.get(now
                ? "msg.factions.now_peaceful" : "msg.factions.no_longer_peaceful"), null);
        return 1;
    }

    private static int declare(CommandContext<CommandSourceStack> ctx,
            FactionStore.Relation relation) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "faction");
        Optional<FactionStore.Faction> mine = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (mine.isEmpty()) {
            return 0;
        }
        Optional<FactionStore.Faction> them = store(ctx).lookup(name);
        if (them.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.factions.unknown", "name", name));
            return 0;
        }
        if (them.get().id().equals(mine.get().id())) {
            Feedback.chat(player, Lang.get("msg.factions.relation_self"));
            return 0;
        }
        if (relation == FactionStore.Relation.ENEMY
                && (mine.get().peaceful() || them.get().peaceful())) {
            // Peaceful is a declaration other factions can see, and it holds in both directions.
            Feedback.chat(player, Lang.get("msg.factions.peaceful_no_enemies"));
            return 0;
        }
        store(ctx).declare(mine.get().id(), them.get().id(), relation);

        FactionStore.Relation now = store(ctx).relation(mine.get().id(), them.get().id());
        Feedback.chat(player, Lang.fmt("msg.factions.declared",
                "name", them.get().name(),
                "relation", Lang.get("msg.factions.relation." + relation.key())));
        // An offered alliance is worth saying out loud to both sides — otherwise it looks like
        // nothing happened until, one day, the other faction happens to reciprocate.
        if (relation == FactionStore.Relation.ALLY && now != FactionStore.Relation.ALLY) {
            Feedback.chat(player, Lang.fmt("msg.factions.alliance_pending", "name", them.get().name()));
            announce(ctx, them.get(), Lang.fmt("msg.factions.alliance_offered",
                    "name", mine.get().name()), null);
        }
        return 1;
    }

    // --- looking ---

    private static int mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = store(ctx).of(player.getUUID());
        if (f.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.none"));
            return 0;
        }
        return describe(ctx, player, f.get());
    }

    private static int who(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = store(ctx).lookup(name);
        if (f.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.factions.unknown", "name", name));
            return 0;
        }
        return describe(ctx, player, f.get());
    }

    private static int describe(CommandContext<CommandSourceStack> ctx,
            ServerPlayer viewer, FactionStore.Faction f) {
        MinecraftServer server = ctx.getSource().getServer();
        var names = com.sablednah.standards.neoforge.StandardsData.get(server);
        String members = f.memberIds().stream()
                .map(id -> names.nameOf(id).orElse("?"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + ", " + b).orElse("");
        Optional<FactionStore.Faction> mine = store(ctx).of(viewer.getUUID());
        FactionStore.Relation rel = mine.map(m -> store(ctx).relation(m.id(), f.id()))
                .orElse(FactionStore.Relation.NEUTRAL);
        Feedback.chat(viewer, Lang.fmt("msg.factions.who",
                "name", f.name(),
                "tag", f.tag().isEmpty() ? Lang.get("msg.factions.no_tag") : f.tag(),
                "relation", Lang.get("msg.factions.relation." + rel.key()),
                "peaceful", f.peaceful() ? Lang.get("msg.factions.is_peaceful") : "",
                "land", store(ctx).claimCount(f.id()),
                "count", f.members().size(),
                "members", members));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<FactionStore.Faction> all = store(ctx).all();
        if (all.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.none_yet"));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.factions.list_header", "count", all.size()));
        for (FactionStore.Faction f : all) {
            Feedback.chat(player, Lang.fmt("msg.factions.list_row",
                    "name", f.name(),
                    "tag", f.tag().isEmpty() ? "" : "[" + f.tag() + "]",
                    "count", f.members().size(),
                    "land", store(ctx).claimCount(f.id())));
        }
        return all.size();
    }

    private static int map(CommandContext<CommandSourceStack> ctx, boolean asItem)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        if (!asItem) {
            for (String row : FactionMap.ascii(player, level, 4)) {
                Feedback.chat(player, row);
            }
            Feedback.chat(player, Lang.get("msg.factions.map_legend"));
            return 1;
        }
        Optional<net.minecraft.world.item.ItemStack> atlas = FactionMap.create(player, level);
        if (atlas.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.map_failed"));
            return 0;
        }
        if (!player.getInventory().add(atlas.get())) {
            player.drop(atlas.get(), false);
        }
        Feedback.chat(player, Lang.get("msg.factions.map_given"));
        return 1;
    }

    private static int borders(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean on = FactionBorders.toggle(player);
        Feedback.chat(player, Lang.get(on
                ? "msg.factions.borders_on" : "msg.factions.borders_off"));
        return 1;
    }

    // --- helpers ---

    private static FactionStore store(CommandContext<CommandSourceStack> ctx) {
        return FactionStore.get(ctx.getSource().getServer());
    }

    /** The player's faction, if they are in one and outrank the bar. Complains for them if not. */
    private static Optional<FactionStore.Faction> atLeast(CommandContext<CommandSourceStack> ctx,
            ServerPlayer player, FactionStore.Rank needed) {
        Optional<FactionStore.Faction> f = store(ctx).of(player.getUUID());
        if (f.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.none"));
            return Optional.empty();
        }
        FactionStore.Rank rank = f.get().rankOf(player.getUUID());
        if (rank == null || !rank.atLeast(needed)) {
            Feedback.chat(player, Lang.fmt("msg.factions.need_rank",
                    "rank", Lang.get("msg.factions.rank." + needed.key())));
            return Optional.empty();
        }
        return f;
    }

    private static Optional<UUID> lookupPlayer(CommandContext<CommandSourceStack> ctx, String name) {
        MinecraftServer server = ctx.getSource().getServer();
        return com.sablednah.standards.neoforge.StandardsData.get(server).byName(server, name);
    }

    private static void announce(CommandContext<CommandSourceStack> ctx,
            FactionStore.Faction f, String message, UUID except) {
        MinecraftServer server = ctx.getSource().getServer();
        for (UUID member : f.memberIds()) {
            if (member.equals(except)) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                Feedback.chat(online, message);
            }
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestFactions(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                store(ctx).all().stream().map(FactionStore.Faction::name).toList(), builder);
    }

    private FactionCommands() {}
}

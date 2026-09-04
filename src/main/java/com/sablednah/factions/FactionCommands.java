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
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(FactionCommands::create)))
                .then(Commands.literal("disband").executes(FactionCommands::disband))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FactionCommands::invite)))
                .then(Commands.literal("join")
                        .then(factionName()
                                .executes(FactionCommands::join)))
                .then(Commands.literal("request")
                        .then(factionName()
                                .executes(FactionCommands::request)))
                .then(Commands.literal("requests").executes(FactionCommands::requests))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(FactionCommands::suggestRequesters)
                                .executes(ctx -> answer(ctx, true))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(FactionCommands::suggestRequesters)
                                .executes(ctx -> answer(ctx, false))))
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
                .then(Commands.literal("autoclaim").executes(FactionCommands::autoclaim))
                .then(Commands.literal("unclaim").executes(FactionCommands::unclaim))
                .then(Commands.literal("unclaimall").executes(FactionCommands::unclaimAll))
                .then(Commands.literal("sethome").executes(FactionCommands::setHome))
                .then(Commands.literal("home").executes(FactionCommands::home))
                .then(Commands.literal("tag")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(FactionCommands::tag)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(FactionCommands::rename)))
                .then(Commands.literal("peaceful").executes(FactionCommands::peaceful))
                .then(Commands.literal("raid")
                        .requires(src -> FactionsConfig.ENABLE_RAIDS.get())
                        // A bare /f raid lists what is running rather than refusing as incomplete.
                        // Same lesson as Standards' bare /nick: a command that knows its own name
                        // should never answer 'unknown or incomplete'.
                        .executes(FactionCommands::raids)
                        .then(Commands.literal("top").executes(FactionCommands::raidTop))
                        .then(factionName()
                                .executes(FactionCommands::raid)))
                .then(Commands.literal("raids")
                        .requires(src -> FactionsConfig.ENABLE_RAIDS.get())
                        .executes(FactionCommands::raids))
                .then(relation("ally", FactionStore.Relation.ALLY))
                .then(relation("enemy", FactionStore.Relation.ENEMY))
                .then(relation("neutral", FactionStore.Relation.NEUTRAL))
                .then(Commands.literal("list").executes(FactionCommands::list))
                .then(Commands.literal("who")
                        .then(factionName()
                                .executes(ctx -> who(ctx,
                                        StringArgumentType.getString(ctx, "faction")))))
                .then(Commands.literal("map")
                        .executes(ctx -> map(ctx, false, 0))
                        .then(Commands.literal("item")
                                .executes(ctx -> map(ctx, true, 0))
                                .then(Commands.argument("zoom",
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .integer(1, 8))
                                        .executes(ctx -> map(ctx, true,
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "zoom"))))))
                .then(Commands.literal("chat")
                        .executes(ctx -> chat(ctx, null))
                        .then(Commands.literal("public").executes(ctx -> chat(ctx, FactionChat.Channel.PUBLIC)))
                        .then(Commands.literal("faction").executes(ctx -> chat(ctx, FactionChat.Channel.FACTION)))
                        .then(Commands.literal("ally").executes(ctx -> chat(ctx, FactionChat.Channel.ALLY))))
                // One message without moving house. The classic shorthand, and the reason the
                // channel switch is survivable at all — most of what people want to say to their
                // faction is one line in the middle of a public conversation.
                .then(Commands.literal("c")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> chatOnce(ctx, FactionChat.Channel.FACTION))))
                .then(Commands.literal("ca")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> chatOnce(ctx, FactionChat.Channel.ALLY))))
                .then(Commands.literal("chatspy")
                        .requires(src -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src))
                        .executes(FactionCommands::chatspy))
                // on / off / toggle, like every switch in Standards — a bare '/f bypass' flips it
                // for a human, and the explicit forms exist so a command block, a datapack or a
                // staff macro can turn it OFF reliably rather than guessing at a toggle.
                .then(Commands.literal("bypass")
                        .requires(FactionPermissions.require(FactionPermissions.BYPASS))
                        .executes(ctx -> bypass(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> bypass(ctx, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(ctx -> bypass(ctx, Boolean.FALSE)))
                        .then(Commands.literal("toggle").executes(ctx -> bypass(ctx, null))))
                .then(Commands.literal("money")
                        .executes(FactionCommands::money)
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount",
                                                com.mojang.brigadier.arguments.DoubleArgumentType
                                                        .doubleArg(0.01D))
                                        .executes(ctx -> moveMoney(ctx, true))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount",
                                                com.mojang.brigadier.arguments.DoubleArgumentType
                                                        .doubleArg(0.01D))
                                        .executes(ctx -> moveMoney(ctx, false))))
                        .then(Commands.literal("pay")
                                .then(quotedName()
                                        .then(Commands.argument("amount",
                                                        com.mojang.brigadier.arguments
                                                                .DoubleArgumentType.doubleArg(0.01D))
                                                .executes(FactionCommands::payFaction)
                                                // A ransom that cannot state its terms is a
                                                // donation nobody can act on.
                                                .then(Commands.argument("reason",
                                                                StringArgumentType.greedyString())
                                                        .executes(FactionCommands::payFaction))))))
                .then(Commands.literal("standard").executes(FactionCommands::standard))
                .then(Commands.literal("power")
                        .executes(ctx -> power(ctx, null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> power(ctx,
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("status").executes(FactionCommands::status))
                .then(fixtures())
                .then(Commands.literal("borders").executes(FactionCommands::borders));
    }

    /**
     * {@code /f fixture} — invent neighbours, or take them away.
     *
     * <p>Registered only where the config asks for it, so a server that will never run it does
     * not offer it in tab-complete. Op-gated on top of that: it is the one command here that can
     * rewrite the diplomatic map in a single keystroke.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> fixtures() {
        if (!FactionsConfig.FIXTURES.get()) {
            // A node nobody satisfies. Brigadier hides it from tab-complete and answers an
            // attempt with "unknown command", which is what Standards' decision 7 is after — the
            // command is absent rather than present and arguing. A literal absence would be
            // tidier still and costs restructuring the whole builder chain to save nothing a
            // player could ever observe.
            return Commands.literal("fixture").requires(src -> false);
        }
        return Commands.literal("fixture")
                .requires(src -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src))
                .then(Commands.literal("seed")
                        .executes(ctx -> seedFixtures(ctx, 4))
                        .then(Commands.argument("chunksEach",
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .integer(1, 16))
                                .executes(ctx -> seedFixtures(ctx,
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .getInteger(ctx, "chunksEach")))))
                .then(Commands.literal("standards")
                        .executes(FactionCommands::seedFixtureStandards))
                .then(Commands.literal("clear").executes(FactionCommands::clearFixtures));
    }

    private static int seedFixtures(CommandContext<CommandSourceStack> ctx, int chunksEach)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> report = FactionFixtures.seed(player, chunksEach);
        Feedback.chat(player, Lang.fmt("msg.factions.fixtures_seeded", "count", report.size()));
        for (String row : report) {
            Feedback.chat(player, Lang.fmt("msg.factions.fixtures_row", "row", row));
        }
        return report.size();
    }

    /**
     * {@code /f fixture standards} — plant a real flag for every seeded neighbour.
     *
     * <p>Separate from {@code seed} on purpose. Seeding is cheap and repeatable; this one edits the
     * world, so it should be a thing somebody asked for rather than a side effect of asking for
     * something else.</p>
     */
    private static int seedFixtureStandards(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> report = FactionFixtures.standards(player);
        Feedback.chat(player, Lang.fmt("msg.factions.fixtures_standards", "count", report.size()));
        for (String row : report) {
            Feedback.chat(player, Lang.fmt("msg.factions.fixtures_row", "row", row));
        }
        return report.size();
    }

    private static int clearFixtures(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int gone = FactionFixtures.clear(ctx.getSource().getServer());
        Feedback.chat(player, Lang.fmt("msg.factions.fixtures_cleared", "count", gone));
        return gone;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> relation(
            String literal, FactionStore.Relation relation) {
        return Commands.literal(literal)
                .then(factionName()
                        .executes(ctx -> declare(ctx, relation)));
    }

    // --- membership ---

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        if (!nameIsSane(player, name)) {
            return 0;
        }
        Optional<FactionStore.Faction> made = store(ctx).create(name, player.getUUID());
        if (made.isEmpty()) {
            Feedback.chat(player, store(ctx).of(player.getUUID()).isPresent()
                    ? Lang.get("msg.factions.already_in_one")
                    : Lang.fmt("msg.factions.name_taken", "name", name));
            return 0;
        }
        // Founding your own answers the question you were asking everybody else. Left pending,
        // an officer somewhere still sees you in /f requests and can accept a member they cannot
        // have — the same reason joining clears them, arriving through the other door out of
        // being factionless.
        FactionRequests.forgetPlayer(player.getUUID());
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
        // Both were written and neither was called. Nothing was exploitable — /f join looks the
        // faction up first — but an offer outliving the thing that made it is a leak with a
        // longer fuse than that.
        FactionInvites.forgetFaction(f.get().id());
        FactionRequests.forgetFaction(f.get().id());
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
        // Everything else you had asked for is moot; leaving it open only lets some other officer
        // accept a member they cannot have and then be told why not.
        FactionRequests.forgetPlayer(player.getUUID());
        Feedback.chat(player, Lang.fmt("msg.factions.joined", "name", target.get().name()));
        announce(ctx, target.get(), Lang.fmt("msg.factions.member_joined",
                "player", player.getName().getString()), player.getUUID());
        return 1;
    }

    // --- asking to join ---

    /**
     * The rank that may answer a request.
     *
     * <p>Officers by default, because an officer can already {@code /f invite} whoever they like —
     * letting them recruit a stranger but not one who asked first is a rule nobody could
     * explain.</p>
     */
    private static FactionStore.Rank answerRank() {
        return FactionsConfig.OFFICERS_MAY_ACCEPT.get()
                ? FactionStore.Rank.OFFICER : FactionStore.Rank.LEADER;
    }

    private static int request(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "faction");
        if (store(ctx).of(player.getUUID()).isPresent()) {
            Feedback.chat(player, Lang.get("msg.factions.already_in_one"));
            return 0;
        }
        Optional<FactionStore.Faction> target = store(ctx).lookup(name);
        if (target.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.factions.unknown", "name", name));
            return 0;
        }
        if (FactionRequests.pending(target.get().id(), player.getUUID())) {
            Feedback.chat(player, Lang.fmt("msg.factions.already_asked",
                    "name", target.get().name()));
            return 0;
        }
        FactionRequests.ask(target.get().id(), player.getUUID());
        Feedback.chat(player, Lang.fmt("msg.factions.requested", "name", target.get().name()));

        // Told to whoever can act on it, and only them. A request nobody is shown is a request
        // that sits there until the asker gives up and concludes the faction ignored them.
        String heard = Lang.fmt("msg.factions.request_received",
                "player", player.getName().getString());
        MinecraftServer server = ctx.getSource().getServer();
        for (UUID member : target.get().memberIds()) {
            FactionStore.Rank rank = target.get().rankOf(member);
            if (rank == null || !rank.atLeast(answerRank())) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                Feedback.chat(online, heard);
            }
        }
        return 1;
    }

    private static int requests(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, answerRank());
        if (f.isEmpty()) {
            return 0;
        }
        List<UUID> waiting = FactionRequests.forFaction(f.get().id());
        if (waiting.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.no_requests"));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        com.sablednah.standards.neoforge.StandardsData names =
                com.sablednah.standards.neoforge.StandardsData.get(server);
        Feedback.chat(player, Lang.fmt("msg.factions.requests_header",
                "count", waiting.size(),
                "list", waiting.stream()
                        .map(u -> names.nameOf(u).orElse(u.toString().substring(0, 8)))
                        .collect(java.util.stream.Collectors.joining(", "))));
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, boolean accept)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<FactionStore.Faction> f = atLeast(ctx, player, answerRank());
        if (f.isEmpty()) {
            return 0;
        }
        Optional<UUID> who = lookupPlayer(ctx, name);
        if (who.isEmpty() || !FactionRequests.pending(f.get().id(), who.get())) {
            Feedback.chat(player, Lang.fmt("msg.factions.no_request_from", "player", name));
            return 0;
        }
        FactionRequests.withdraw(f.get().id(), who.get());
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(who.get());

        if (!accept) {
            Feedback.chat(player, Lang.fmt("msg.factions.declined", "player", name));
            if (online != null) {
                Feedback.chat(online, Lang.fmt("msg.factions.you_were_declined",
                        "name", f.get().name()));
            }
            return 1;
        }

        // Checked again here rather than trusted from when they asked: the gap between the two is
        // where somebody accepts an invitation somewhere else.
        if (!store(ctx).addMember(f.get().id(), who.get(), FactionStore.Rank.MEMBER)) {
            Feedback.chat(player, Lang.fmt("msg.factions.they_are_in_one", "player", name));
            return 0;
        }
        FactionRequests.forgetPlayer(who.get());
        Feedback.chat(player, Lang.fmt("msg.factions.accepted", "player", name));
        if (online != null) {
            Feedback.chat(online, Lang.fmt("msg.factions.joined", "name", f.get().name()));
        }
        announce(ctx, f.get(), Lang.fmt("msg.factions.member_joined", "player", name),
                who.get());
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestRequesters(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        com.sablednah.standards.neoforge.StandardsData names =
                com.sablednah.standards.neoforge.StandardsData.get(server);
        List<String> waiting = store(ctx).of(player.getUUID())
                .map(f -> FactionRequests.forFaction(f.id()).stream()
                        .map(u -> names.nameOf(u).orElse(u.toString().substring(0, 8)))
                        .toList())
                .orElse(List.of());
        return SharedSuggestionProvider.suggest(waiting, builder);
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
        ChunkPos chunk = ChunkPos.containing(player.blockPosition());
        FactionStore store = store(ctx);

        int limit = FactionClaims.limitFor(f.get());
        // Read BEFORE the attempt: once the chunk changes hands the old owner is no longer the
        // owner, and both the message and the announcement would name the raider.
        Optional<FactionStore.Faction> victim = store.ownerOf(dim, chunk.x(), chunk.z())
                .flatMap(store::byId)
                .filter(other -> !other.id().equals(f.get().id()));
        String victimName = victim.map(FactionStore.Faction::name).orElse("?");
        // The decision itself lives in FactionClaims so that /f autoclaim cannot drift from it.
        switch (FactionClaims.attempt(store, dim, chunk, f.get())) {
            case ALREADY_YOURS -> {
                Feedback.chat(player, Lang.get("msg.factions.already_yours"));
                return 0;
            }
            case OWNED -> {
                Feedback.chat(player, Lang.fmt("msg.factions.claimed_by_other", "name",
                        store.ownerOf(dim, chunk.x(), chunk.z()).flatMap(store::byId)
                                .map(FactionStore.Faction::name).orElse("?")));
                return 0;
            }
            case LIMIT -> {
                // Scaling with membership is what stops one person fencing off a continent, and
                // it gives recruiting a point beyond the numbers.
                Feedback.chat(player, Lang.fmt("msg.factions.claim_limit",
                        "held", store.claimCount(f.get().id()), "limit", limit,
                        "members", f.get().members().size()));
                return 0;
            }
            case DISCONNECTED -> {
                Feedback.chat(player, Lang.get("msg.factions.must_connect"));
                return 0;
            }
            case BROKE -> {
                Feedback.chat(player, Lang.fmt("msg.factions.claim_too_dear",
                        "amount", com.sablednah.standards.api.economy.Economy.format(
                                FactionBank.claimCost(store.claimCount(f.get().id()))),
                        "balance", com.sablednah.standards.api.economy.Economy.format(
                                FactionBank.balance(store, f.get().id()))));
                return 0;
            }
            case THEIRS_AND_HELD -> {
                Feedback.chat(player, Lang.fmt("msg.factions.claim_held", "name",
                        store.ownerOf(dim, chunk.x(), chunk.z()).flatMap(store::byId)
                                .map(FactionStore.Faction::name).orElse("?")));
                return 0;
            }
            case NOT_AT_WAR -> {
                Feedback.chat(player, Lang.fmt("msg.factions.claim_not_at_war", "name",
                        store.ownerOf(dim, chunk.x(), chunk.z()).flatMap(store::byId)
                                .map(FactionStore.Faction::name).orElse("?")));
                return 0;
            }
            case RAID_CLAIM_LIMIT -> {
                Feedback.chat(player, Lang.fmt("msg.factions.raid_claim_limit",
                        "count", String.valueOf(FactionsConfig.RAID_CLAIM_LIMIT.get())));
                return 0;
            }
            case NO_RAID -> {
                // Names the command that would fix it: the land IS takeable, and the only thing
                // missing is somebody saying so out loud.
                Feedback.chat(player, Lang.fmt("msg.factions.raid_needed", "name",
                        store.ownerOf(dim, chunk.x(), chunk.z()).flatMap(store::byId)
                                .map(FactionStore.Faction::name).orElse("?")));
                return 0;
            }
            case PEACEFUL -> {
                Feedback.chat(player, Lang.get("msg.factions.peaceful_no_enemies"));
                return 0;
            }
            case NOT_THEIR_BORDER -> {
                Feedback.chat(player, Lang.get("msg.factions.claim_not_border"));
                return 0;
            }
            case TAKEN -> {
                Feedback.chat(player, Lang.fmt("msg.factions.claim_taken",
                        "x", chunk.x(), "z", chunk.z(), "name", victimName));
                // Told to the victim, and it must be. Land quietly changing hands is the one thing
                // a claim system cannot do silently: they would find out by walking home, and by
                // then the raid is over and nobody was there to answer it.
                victim.ifPresent(loser -> announce(ctx, loser, Lang.fmt("msg.factions.claim_lost",
                        "name", f.get().name(), "x", chunk.x(), "z", chunk.z()), null));
            }
            case CLAIMED -> Feedback.chat(player, Lang.fmt("msg.factions.claimed",
                    "x", chunk.x(), "z", chunk.z(), "held", store.claimCount(f.get().id()),
                    "limit", limit < 0 ? Lang.get("msg.factions.no_limit")
                            : String.valueOf(limit)));
        }
        return 1;
    }

    /**
     * {@code /f autoclaim} — take every chunk you walk into.
     *
     * <p>Officer, exactly as {@code /f claim} is: walking is a faster way to spend the faction's
     * land than typing, not a lower bar for spending it.</p>
     */
    private static int autoclaim(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (atLeast(ctx, player, FactionStore.Rank.OFFICER).isEmpty()) {
            return 0;
        }
        boolean on = FactionAutoClaim.toggle(player);
        Feedback.chat(player, Lang.get(on
                ? "msg.factions.autoclaim_on" : "msg.factions.autoclaim_off"));
        return 1;
    }

    private static int unclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        String dim = FactionBridge.dimensionOf(player.level());
        ChunkPos chunk = ChunkPos.containing(player.blockPosition());
        Optional<String> owner = store(ctx).ownerOf(dim, chunk.x(), chunk.z());
        if (owner.isEmpty() || !owner.get().equals(f.get().id())) {
            Feedback.chat(player, Lang.get("msg.factions.not_yours"));
            return 0;
        }
        double back = FactionClaims.release(store(ctx), dim, chunk, f.get());
        Feedback.chat(player, Lang.fmt("msg.factions.unclaimed", "x", chunk.x(), "z", chunk.z()));
        if (back > 0.0D) {
            Feedback.chat(player, Lang.fmt("msg.factions.unclaim_refund",
                    "amount", com.sablednah.standards.api.economy.Economy.format(back)));
        }
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
        ChunkPos chunk = ChunkPos.containing(player.blockPosition());
        Optional<String> owner = store(ctx).ownerOf(dim, chunk.x(), chunk.z());
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
        if (!nameIsSane(player, name)) {
            return 0;
        }
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

    /**
     * {@code /f raid <faction>} — declare an attack.
     *
     * <p>The checks are the design, and each refusal names the rule it broke rather than saying no.
     * See {@code POWER.md} §5; the ordering matters only in that the free refusals come before
     * anything that reads the player list.</p>
     */
    /** What is happening right now. Open to anybody — a raid is a public event by design. */
    private static int raids(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var live = FactionRaid.active();
        if (live.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.raid_none"));
            return 0;
        }
        FactionStore store = store(ctx);
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (FactionRaid.Raid raid : live) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(Lang.fmt("msg.factions.raid_row",
                    "attacker", store.byId(raid.attackerId())
                            .map(FactionStore.Faction::name).orElse("?"),
                    "defender", store.byId(raid.defenderId())
                            .map(FactionStore.Faction::name).orElse("?"),
                    "time", com.sablednah.standards.core.Duration.describe(raid.secondsLeft(now))));
        }
        Feedback.chat(player, sb.toString());
        return live.size();
    }

    private static int raid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Officer, like every other act of war here — allying, enemying, claiming.
        Optional<FactionStore.Faction> mine = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (mine.isEmpty()) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "faction");
        Optional<FactionStore.Faction> target = store(ctx).byName(name);
        if (target.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.factions.unknown", "name", name));
            return 0;
        }
        FactionStore.Faction us = mine.get();
        FactionStore.Faction them = target.get();
        if (us.id().equals(them.id())) {
            Feedback.chat(player, Lang.get("msg.factions.raid_self"));
            return 0;
        }
        // Peaceful runs both ways, as it does everywhere else: a faction that opted out of
        // fighting neither raids nor is raided, and the promise is not a promise otherwise.
        if (us.peaceful() || them.peaceful()) {
            Feedback.chat(player, Lang.get("msg.factions.raid_peaceful"));
            return 0;
        }
        if (FactionRaid.involved(us.id()) || FactionRaid.involved(them.id())) {
            Feedback.chat(player, Lang.get("msg.factions.raid_already"));
            return 0;
        }
        long now = System.currentTimeMillis();
        long wait = FactionRaid.cooldownLeft(us.id(), them.id(), now);
        if (wait > 0) {
            Feedback.chat(player, Lang.fmt("msg.factions.raid_cooldown",
                    "name", them.name(),
                    "time", com.sablednah.standards.core.Duration.describe(wait)));
            return 0;
        }
        // THE RULE THAT REPLACES DECLINING. Nobody is raided while they are asleep, which is what
        // a decline could never have achieved: the faction that most needs protecting is the one
        // with nobody online, and they are not there to decline.
        int needed = FactionsConfig.RAID_MIN_DEFENDERS.get();
        int online = FactionRaid.onlineMembers(ctx.getSource().getServer(), them.id()).size();
        if (online < needed) {
            Feedback.chat(player, Lang.fmt("msg.factions.raid_nobody_home",
                    "name", them.name(), "needed", String.valueOf(needed),
                    "online", String.valueOf(online)));
            return 0;
        }

        // Recorded now, because the objective is that their flag FALLS — and a faction flying
        // none would otherwise satisfy "their standard is gone" the moment the raid started.
        boolean theyFly = store(ctx).hasStandard(them.id());
        FactionRaid.Raid raid = FactionRaid.begin(us.id(), them.id(), now, theyFly);
        if (!theyFly) {
            // Not a refusal: they may plant one during the raid, and with raidGatesOverclaim on a
            // raid is also how land moves. But saying nothing left an attacker trying every
            // combination of placing and stealing a flag on a raid that could not be won.
            Feedback.chat(player, Lang.fmt("msg.factions.raid_no_standard", "name", them.name()));
        }
        FactionRaidEvents.announce(ctx.getSource().getServer(),
                Lang.fmt("msg.factions.raid_declared",
                        "attacker", us.name(), "defender", them.name(),
                        "time", com.sablednah.standards.core.Duration.describe(
                                raid.secondsLeft(now))));
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

    /**
     * {@code /f status} — where your faction stands with everybody else.
     *
     * <h3>The half that only this can tell you</h3>
     *
     * <p>An offered alliance is announced when it is made, and that is the whole of it: if you
     * were offline, or scrolled past, the offer exists and nothing will ever mention it again.
     * The same goes for being declared upon — you learn there is a war on by being killed in it.
     * Everything else here is available a faction at a time through {@code /f who}, which means
     * knowing who to ask about, which is the thing you do not know.</p>
     *
     * <p>So the offers come first and are split by direction, because they are the two ends of
     * one word and only one of them is your move. An offer <em>to</em> you is a decision waiting
     * on you; an offer <em>from</em> you is a thing you are waiting on. Sorting both into one
     * "pending" list would hide which is which behind a name you have to recognise.</p>
     *
     * <p>Enemies are split the same way and for a sharper reason: a war you started and a war
     * somebody started on you need different responses, and the relation itself cannot tell them
     * apart — {@code relation()} resolves to ENEMY from one side's declaration alone, which is
     * exactly what makes the direction worth printing.</p>
     */
    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> owned = store(ctx).of(player.getUUID());
        if (owned.isEmpty()) {
            // Not an error. Somebody with no faction has a status too — it is just made of the
            // things they are waiting on rather than the things they hold, and those are exactly
            // the two lists nothing else will show them: an invitation is announced once and
            // never mentioned again, and a request they made is invisible from their side
            // entirely. Answering "you have no faction" to the one person who most needs a list
            // is the least helpful thing this command could do.
            return statusOfNobody(ctx, player);
        }
        FactionStore store = store(ctx);
        FactionStore.Faction mine = owned.get();

        List<String> allies = new java.util.ArrayList<>();
        List<String> offeredToUs = new java.util.ArrayList<>();
        List<String> offeredByUs = new java.util.ArrayList<>();
        List<String> weDeclared = new java.util.ArrayList<>();
        List<String> theyDeclared = new java.util.ArrayList<>();

        for (FactionStore.Faction other : store.all()) {
            if (other.id().equals(mine.id())) {
                continue;
            }
            switch (store.relation(mine.id(), other.id())) {
                case ALLY -> allies.add(other.name());
                case ENEMY -> {
                    // Both lists when it is mutual: "we declared on them and they declared back"
                    // is a different situation from either half alone.
                    if (mine.enemies().contains(other.id())) {
                        weDeclared.add(other.name());
                    }
                    if (other.enemies().contains(mine.id())) {
                        theyDeclared.add(other.name());
                    }
                }
                case NEUTRAL -> {
                    if (store.allianceOffered(other.id(), mine.id())) {
                        offeredToUs.add(other.name());
                    }
                    if (store.allianceOffered(mine.id(), other.id())) {
                        offeredByUs.add(other.name());
                    }
                }
            }
        }

        Feedback.chat(player, Lang.fmt("msg.factions.status_header",
                "name", FactionStandards.chatColour(store, mine.id()) + mine.name(),
                "peaceful", mine.peaceful() ? Lang.get("msg.factions.is_peaceful") : "",
                "land", store.claimCount(mine.id()),
                "count", mine.members().size()));

        // Yours to answer, so it goes first.
        line(player, "msg.factions.status_offered_to_us", offeredToUs);
        line(player, "msg.factions.status_offered_by_us", offeredByUs);
        line(player, "msg.factions.status_allies", allies);
        line(player, "msg.factions.status_we_declared", weDeclared);
        line(player, "msg.factions.status_they_declared", theyDeclared);

        // Power, and specifically the EXPOSURE. "You hold 22 chunks on an entitlement of 18" is
        // the line that earns the whole feature: a raid you did not know was possible is
        // indistinguishable from a bug.
        if (FactionPower.Mode.of(FactionsConfig.POWER_MODE.get()).active()) {
            int held = store.claimCount(mine.id());
            int entitled = FactionPower.entitlement(mine.members().size(), store.powerOf(mine),
                    FactionsConfig.POWER_MAX.get(), FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get());
            Feedback.chat(player, Lang.fmt("msg.factions.status_power",
                    "power", FactionPowerEvents.trim(store.powerOf(mine)),
                    "held", held,
                    "entitled", entitled < 0 ? Lang.get("msg.factions.no_limit")
                            : String.valueOf(entitled)));
            int over = FactionPower.overreach(held, entitled);
            if (over > 0) {
                Feedback.chat(player, Lang.fmt("msg.factions.power_exposed",
                        "over", over, "held", held, "entitled", entitled));
            }
            Feedback.chat(player, Lang.fmt("msg.factions.power_regen",
                    "rate", FactionPowerEvents.trim(
                            FactionPowerEvents.regenPerMinute(ctx.getSource().getServer(), store, mine.id())),
                    "standard", Lang.get(FactionPowerEvents.standardState(ctx.getSource().getServer(), store, mine.id()))));
        }

        // Money, but only when there is something to say about it — a bank line reading zero on
        // a server with no economy is noise on every status anybody ever runs.
        double bank = FactionBank.balance(store, mine.id());
        if (bank > 0.0D || FactionsConfig.CLAIM_COST.get() > 0.0D) {
            Feedback.chat(player, Lang.fmt("msg.factions.status_bank",
                    "amount", com.sablednah.standards.api.economy.Economy.format(bank),
                    "next", com.sablednah.standards.api.economy.Economy.format(
                            FactionBank.claimCost(store.claimCount(mine.id())))));
        }

        FactionStore.Rank rank = mine.rankOf(player.getUUID());
        if (rank != null && rank.atLeast(answerRank())) {
            List<UUID> waiting = FactionRequests.forFaction(mine.id());
            if (!waiting.isEmpty()) {
                var names = com.sablednah.standards.neoforge.StandardsData.get(
                        ctx.getSource().getServer());
                Feedback.chat(player, Lang.fmt("msg.factions.status_requests",
                        "count", waiting.size(),
                        "list", waiting.stream()
                                .map(u -> names.nameOf(u).orElse(u.toString().substring(0, 8)))
                                .collect(java.util.stream.Collectors.joining(", "))));
            }
        }

        if (allies.isEmpty() && offeredToUs.isEmpty() && offeredByUs.isEmpty()
                && weDeclared.isEmpty() && theyDeclared.isEmpty() && bank <= 0.0D) {
            // Said explicitly, because a header with nothing under it reads as a broken command
            // rather than as peace.
            Feedback.chat(player, Lang.get("msg.factions.status_nothing"));
        }
        return 1;
    }

    /** What a factionless player is waiting on: who has asked them, and who they have asked. */
    private static int statusOfNobody(CommandContext<CommandSourceStack> ctx, ServerPlayer player)
            throws CommandSyntaxException {
        FactionStore store = store(ctx);
        Feedback.chat(player, Lang.get("msg.factions.status_none_header"));

        List<String> invited = FactionInvites.forPlayer(player.getUUID()).stream()
                .map(store::byId)
                .flatMap(Optional::stream)
                .map(FactionStore.Faction::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        List<String> asked = store.all().stream()
                .filter(f -> FactionRequests.pending(f.id(), player.getUUID()))
                .map(FactionStore.Faction::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // Invitations first: those are the ones you can act on this second.
        line(player, "msg.factions.status_invited", invited);
        line(player, "msg.factions.status_asked", asked);
        if (invited.isEmpty() && asked.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.status_none_pending"));
        }
        return 1;
    }

    /** One line of the status, or none at all when there is nothing to say. */
    private static void line(ServerPlayer player, String key, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        Feedback.chat(player, Lang.fmt(key,
                "count", names.size(), "list", String.join(", ", names)));
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
                // Its own colour, from its own banner. Identity, not relation — the relation is
                // the separate field below and stays green/blue/red.
                "name", FactionStandards.chatColour(store(ctx), f.id()) + f.name(),
                "tag", f.tag().isEmpty() ? Lang.get("msg.factions.no_tag") : f.tag(),
                "relation", Lang.get("msg.factions.relation." + rel.key()),
                "peaceful", f.peaceful() ? Lang.get("msg.factions.is_peaceful") : "",
                "land", store(ctx).claimCount(f.id()),
                "count", f.members().size(),
                "members", members));
        // Only once they have actually been in one. A row of noughts on every faction says
        // nothing and pushes the members list further down the screen.
        FactionStore.RaidRecord raids = store(ctx).raidRecord(f.id());
        if (raids.fought() > 0) {
            Feedback.chat(viewer, Lang.fmt("msg.factions.who_raids",
                    "won", raids.won(), "fought", raids.fought(),
                    "taken", raids.attacksWon(), "held", raids.defencesHeld()));
        }
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
                    "name", FactionStandards.chatColour(store(ctx), f.id()) + f.name(),
                    "tag", f.tag().isEmpty() ? "" : "[" + f.tag() + "]",
                    "count", f.members().size(),
                    "land", store(ctx).claimCount(f.id())));
        }
        return all.size();
    }

    /**
     * @param zoom pixels per chunk, or 0 to take the server's configured default. A number rather
     *             than a literal because "how far in" is a quantity, and because the useful
     *             values are decided by the size of the factions on the server rather than by us.
     */
    private static int map(CommandContext<CommandSourceStack> ctx, boolean asItem, int zoom)
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
        int ppc = zoom > 0 ? zoom : FactionsConfig.MAP_PIXELS_PER_CHUNK.get();
        Optional<net.minecraft.world.item.ItemStack> atlas =
                FactionMap.create(player, level, ppc);
        if (atlas.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.map_failed"));
            return 0;
        }
        if (!player.getInventory().add(atlas.get())) {
            player.drop(atlas.get(), false);
        }
        // Says what it is showing, because two atlases in a chest look identical and the whole
        // point of asking for a zoom is that you wanted a different one.
        Feedback.chat(player, Lang.fmt("msg.factions.map_given",
                "chunks", 128 / Integer.highestOneBit(Math.min(8, ppc))));
        return 1;
    }

    /**
     * {@code /f standard} — designate the banner you are looking at, or report on the one you fly.
     *
     * <p>Looked at rather than named, because the thing being designated is a specific block in a
     * specific place, and pointing at it is unambiguous in a way coordinates are not.</p>
     */
    private static int standard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = atLeast(ctx, player, FactionStore.Rank.OFFICER);
        if (f.isEmpty()) {
            return 0;
        }
        ServerLevel level = player.level();
        FactionStore store = store(ctx);

        Optional<net.minecraft.core.BlockPos> looking =
                FactionStandards.lookingAtBanner(player, level);
        if (looking.isPresent()) {
            return FactionStandards.designate(player, level, looking.get(), f.get()) ? 1 : 0;
        }

        // Nothing in front of them: report instead of refusing. "Where is my flag" is at least as
        // common a question as "make this my flag", and the same word should answer both.
        Optional<net.minecraft.core.BlockPos> where = store.standardPos(f.get().id());
        if (where.isEmpty()) {
            // "Where is my flag" is the question, and "you have no standard" only answers it when
            // nobody took yours. Somebody who has just been raided knows perfectly well they have
            // none — what they want is where it went.
            FactionStore.Faction holder = null;
            FactionStore.Placed at = null;
            for (FactionStore.Faction other : store.all()) {
                for (FactionStore.Placed flag : store.standardsOf(other.id())) {
                    if (flag.capturedFrom().map(f.get().id()::equals).orElse(false)) {
                        holder = other;
                        at = flag;
                        break;
                    }
                }
            }
            if (holder != null) {
                // Where the TROPHY stands, not where their own flag does — the two are different
                // places now, and the one worth walking to is the one holding your colours.
                Feedback.chat(player, Lang.fmt("msg.factions.standard_theirs",
                        "name", holder.name(),
                        "x", at.pos().getX(), "y", at.pos().getY(), "z", at.pos().getZ(),
                        "world", at.dimension()));
                return 0;
            }
            Optional<ServerPlayer> carrier = FactionStandards.carriedBy(
                    ctx.getSource().getServer(), store, f.get().id());
            if (carrier.isPresent()) {
                ServerPlayer held = carrier.get();
                // One of your own carrying it home is not a theft to chase. Telling your own
                // faction to "go and get it" when the person holding it is stood next to them is
                // the message reading the situation backwards.
                boolean ours = store.of(held.getUUID())
                        .map(theirs -> theirs.id().equals(f.get().id())).orElse(false);
                Feedback.chat(player, Lang.fmt(ours
                                ? "msg.factions.standard_carried_ours"
                                : "msg.factions.standard_carried",
                        "player", held.getName().getString(),
                        "x", held.blockPosition().getX(),
                        "y", held.blockPosition().getY(),
                        "z", held.blockPosition().getZ(),
                        "world", FactionBridge.dimensionOf((ServerLevel) held.level())));
                return 0;
            }
            Feedback.chat(player, Lang.get("msg.factions.standard_none"));
            listTrophies(player, store, f.get().id());
            // What not having one costs, in the same breath. "Raise a flag" is advice; "you are
            // recovering at half speed" is a reason.
            Feedback.chat(player, Lang.fmt("msg.factions.power_regen",
                    "rate", FactionPowerEvents.trim(
                            FactionPowerEvents.regenPerMinute(ctx.getSource().getServer(), store, f.get().id())),
                    "standard", Lang.get(FactionPowerEvents.standardState(ctx.getSource().getServer(), store, f.get().id()))));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.factions.standard_where",
                "x", where.get().getX(), "y", where.get().getY(), "z", where.get().getZ(),
                "world", store.standardDimension(f.get().id()).orElse("?")));
        listTrophies(player, store, f.get().id());
        Feedback.chat(player, Lang.fmt("msg.factions.power_regen",
                "rate", FactionPowerEvents.trim(
                        FactionPowerEvents.regenPerMinute(ctx.getSource().getServer(), store, f.get().id())),
                "standard", Lang.get(FactionPowerEvents.standardState(ctx.getSource().getServer(), store, f.get().id()))));
        return 1;
    }

    /**
     * The enemy flags this faction flies, listed under its own.
     *
     * <p>Listed rather than counted, because each one is a place somebody can walk to and take
     * back. A number would say "you are winning"; the addresses say "here is what you have to
     * defend", which is the more useful sentence for both sides.</p>
     *
     * <p>The bonus they pay is flat however many there are — see {@code POWER.md} §6 — so the
     * line says what a stack of them is actually for.</p>
     */
    private static void listTrophies(ServerPlayer player, FactionStore store, String factionId) {
        List<FactionStore.Placed> trophies = store.standardsOf(factionId).stream()
                .filter(flag -> flag.capturedFrom().isPresent()).toList();
        if (trophies.isEmpty()) {
            return;
        }
        Feedback.chat(player, Lang.fmt("msg.factions.standard_trophies", "count", trophies.size()));
        for (FactionStore.Placed flag : trophies) {
            Feedback.chat(player, Lang.fmt("msg.factions.standard_trophy_line",
                    "name", flag.capturedFrom().flatMap(store::byId)
                            .map(FactionStore.Faction::name).orElse("?"),
                    "x", flag.pos().getX(), "y", flag.pos().getY(), "z", flag.pos().getZ(),
                    "world", flag.dimension(),
                    "state", Lang.get(FactionStandards.flyingAt(flag.dimension(), flag.pos())
                            ? "msg.factions.standard_state_flying"
                            : "msg.factions.standard_state_covered")));
        }
    }

    /** {@code /f power [player]} — yours, or theirs, and what it entitles your faction to. */
    private static int power(CommandContext<CommandSourceStack> ctx, String who)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!FactionPower.Mode.of(FactionsConfig.POWER_MODE.get()).active()) {
            Feedback.chat(player, Lang.get("msg.factions.power_off"));
            return 0;
        }
        FactionStore store = store(ctx);
        UUID subject = player.getUUID();
        String name = player.getName().getString();
        if (who != null) {
            Optional<UUID> found = lookupPlayer(ctx, who);
            if (found.isEmpty()) {
                Feedback.chat(player, Lang.fmt("msg.factions.unknown_player", "player", who));
                return 0;
            }
            subject = found.get();
            name = who;
        }
        Feedback.chat(player, Lang.fmt("msg.factions.power_mine",
                "player", name,
                "power", FactionPowerEvents.trim(store.powerOf(subject)),
                "max", FactionPowerEvents.trim(FactionsConfig.POWER_MAX.get())));

        store.of(subject).ifPresent(f -> {
            int held = store.claimCount(f.id());
            int entitled = FactionPower.entitlement(f.members().size(), store.powerOf(f),
                    FactionsConfig.POWER_MAX.get(), FactionsConfig.CLAIM_LIMIT_PER_MEMBER.get());
            Feedback.chat(player, Lang.fmt("msg.factions.power_faction",
                    "name", f.name(),
                    "power", FactionPowerEvents.trim(store.powerOf(f)),
                    "held", held,
                    "entitled", entitled < 0 ? Lang.get("msg.factions.no_limit")
                            : String.valueOf(entitled)));
            int over = FactionPower.overreach(held, entitled);
            if (over > 0) {
                Feedback.chat(player, Lang.fmt("msg.factions.power_exposed",
                        "over", over, "held", held, "entitled", entitled));
            }
            // How fast it comes back, and why. Without this the standard's whole effect is
            // invisible: you are told a flag matters and never shown that it does.
            Feedback.chat(player, Lang.fmt("msg.factions.power_regen",
                    "rate", FactionPowerEvents.trim(
                            FactionPowerEvents.regenPerMinute(ctx.getSource().getServer(), store, f.id())),
                    "standard", Lang.get(FactionPowerEvents.standardState(ctx.getSource().getServer(), store, f.id()))));
        });
        return 1;
    }

    // --- money ---

    private static int money(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<FactionStore.Faction> f = store(ctx).of(player.getUUID());
        if (f.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.none"));
            return 0;
        }
        double held = FactionBank.balance(store(ctx), f.get().id());
        Feedback.chat(player, Lang.fmt("msg.factions.bank_balance",
                "name", f.get().name(),
                "amount", com.sablednah.standards.api.economy.Economy.format(held)));
        if (FactionsConfig.CLAIM_COST.get() > 0.0D) {
            Feedback.chat(player, Lang.fmt("msg.factions.bank_next_claim",
                    "amount", com.sablednah.standards.api.economy.Economy.format(
                            FactionBank.claimCost(store(ctx).claimCount(f.get().id())))));
        }
        return 1;
    }

    private static int moveMoney(CommandContext<CommandSourceStack> ctx, boolean in)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount");
        // Depositing is ungated on purpose; taking money out is the direction that can grief.
        Optional<FactionStore.Faction> f = in
                ? store(ctx).of(player.getUUID())
                : atLeast(ctx, player, FactionsConfig.OFFICERS_MAY_WITHDRAW.get()
                        ? FactionStore.Rank.OFFICER : FactionStore.Rank.LEADER);
        if (f.isEmpty()) {
            if (in) {
                Feedback.chat(player, Lang.get("msg.factions.none"));
            }
            return 0;
        }
        FactionBank.Result result = in
                ? FactionBank.deposit(store(ctx), player, f.get().id(), amount)
                : FactionBank.withdraw(store(ctx), player, f.get().id(), amount);
        return report(player, result, amount, in
                ? "msg.factions.bank_deposited" : "msg.factions.bank_withdrew", f.get().name());
    }

    private static int payFaction(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount");
        Optional<FactionStore.Faction> mine = atLeast(ctx, player,
                FactionsConfig.OFFICERS_MAY_WITHDRAW.get()
                        ? FactionStore.Rank.OFFICER : FactionStore.Rank.LEADER);
        if (mine.isEmpty()) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "faction");
        Optional<FactionStore.Faction> them = store(ctx).lookup(name);
        if (them.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.factions.unknown", "name", name));
            return 0;
        }
        if (them.get().id().equals(mine.get().id())) {
            Feedback.chat(player, Lang.get("msg.factions.bank_pay_self"));
            return 0;
        }
        FactionBank.Result result =
                FactionBank.pay(store(ctx), mine.get().id(), them.get().id(), amount);
        if (result == FactionBank.Result.OK) {
            String note = "";
            try {
                // Their words, so the same rule chat follows: text, never formatting.
                note = Feedback.stripCodes(StringArgumentType.getString(ctx, "reason")).trim();
            } catch (IllegalArgumentException noReason) {
                // Optional, and usually absent.
            }
            // Told to them as well. Money arriving in a bank with no explanation is
            // indistinguishable from a bug, and this is how tribute and ransom get paid.
            announce(ctx, them.get(), Lang.fmt("msg.factions.bank_received",
                    "name", mine.get().name(),
                    "amount", com.sablednah.standards.api.economy.Economy.format(amount),
                    "note", note.isEmpty() ? ""
                            : Lang.fmt("msg.eco.pay_note", "reason", note)), null);
        }
        return report(player, result, amount, "msg.factions.bank_paid", them.get().name());
    }

    private static int report(ServerPlayer player, FactionBank.Result result, double amount,
            String successKey, String name) {
        String money = com.sablednah.standards.api.economy.Economy.format(amount);
        switch (result) {
            case OK -> Feedback.chat(player, Lang.fmt(successKey, "amount", money, "name", name));
            case INSUFFICIENT -> Feedback.chat(player, Lang.fmt("msg.factions.bank_short",
                    "amount", money));
            case NO_ECONOMY -> Feedback.chat(player, Lang.get("msg.factions.bank_no_economy"));
            case FAILED -> Feedback.chat(player, Lang.get("msg.factions.bank_failed"));
        }
        return result == FactionBank.Result.OK ? 1 : 0;
    }

    // --- talking ---

    /** @param wanted the channel asked for, or null to cycle. */
    private static int chat(CommandContext<CommandSourceStack> ctx, FactionChat.Channel wanted)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FactionChat.Channel next = wanted != null ? wanted : FactionChat.channelOf(player).next();
        if (next != FactionChat.Channel.PUBLIC && store(ctx).of(player.getUUID()).isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.chat_no_faction"));
            return 0;
        }
        FactionChat.setChannel(player, next);
        Feedback.chat(player, Lang.fmt("msg.factions.chat_now",
                "channel", Lang.get("msg.factions.chat_channel." + next.key())));
        return 1;
    }

    private static int chatOnce(CommandContext<CommandSourceStack> ctx, FactionChat.Channel channel)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Through the same gates a typed line passes, or /f c is the hole that makes a mute a
        // suggestion. speechBlocked answers for mutes; noteActivity clears the AFK marker.
        var blocked = com.sablednah.standards.api.chat.Chat.speechBlocked(player);
        if (blocked.isPresent()) {
            player.sendSystemMessage(blocked.get());
            return 0;
        }
        com.sablednah.standards.api.chat.Chat.noteActivity(player);
        FactionChat.send(player, channel, StringArgumentType.getString(ctx, "message"));
        return 1;
    }

    /**
     * Turn the claim override on or off.
     *
     * @param want {@code null} to flip it, which is what a bare {@code /f bypass} means
     */
    private static int bypass(CommandContext<CommandSourceStack> ctx, Boolean want)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean before = FactionBypass.isActive(player.getUUID());
        if (want != null && want == before) {
            // Say so rather than silently doing nothing: somebody typing 'off' twice wants to know
            // the second one was unnecessary, not to wonder whether it took.
            Feedback.chat(player, Lang.fmt("msg.factions.bypass_already",
                    "state", Lang.get(before ? "msg.toggle.on" : "msg.toggle.off")));
            return 0;
        }
        boolean now = want == null ? FactionBypass.toggle(player) : FactionBypass.set(player, want);
        Feedback.chat(player, Lang.get(now
                ? "msg.factions.bypass_on" : "msg.factions.bypass_off"));
        // Logged, because an override used is a thing somebody may need to account for later, and
        // the person asking will not be the person who used it.
        Factions.LOGGER.info("Factions: claim override {} for {}",
                now ? "ON" : "off", player.getName().getString());
        return 1;
    }

    private static int chatspy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean on = FactionChat.toggleSpy(player);
        Feedback.chat(player, Lang.get(on
                ? "msg.factions.chatspy_on" : "msg.factions.chatspy_off"));
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

    /**
     * {@code /f raids top} — who is actually winning them.
     *
     * <p>Ranked by raids <b>won</b>, either end, and a faction that has never been in one is simply
     * absent rather than sitting at the bottom on nought: a leaderboard of everybody is a list, and
     * the interesting property is that appearing on it at all means you turned up.</p>
     *
     * <p>Both columns are shown because they answer different questions. A faction can be top by
     * raiding constantly and top by never losing their ground, and flattening those into one number
     * would hide the more interesting of the two.</p>
     */
    private static int raidTop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FactionStore store = store(ctx);
        List<FactionStore.RaidRecord> rows = store.raidRecords().stream()
                .filter(r -> r.fought() > 0)
                // Build the ascending order and reverse the WHOLE thing exactly once. Writing
                // .reversed() after each key looks right and is not: the trailing call reverses
                // the composed comparator, undoing the first reversal, and the board came out
                // with the faction that had won nothing at the top.
                .sorted(java.util.Comparator
                        .comparingInt(FactionStore.RaidRecord::won)
                        .thenComparingInt(FactionStore.RaidRecord::fought)
                        .reversed())
                .limit(10)
                .toList();
        if (rows.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.factions.raid_top_none"));
            return 0;
        }
        Feedback.chat(player, Lang.get("msg.factions.raid_top_header"));
        int place = 0;
        for (FactionStore.RaidRecord r : rows) {
            place++;
            Feedback.chat(player, Lang.fmt("msg.factions.raid_top_row",
                    "place", place,
                    "name", store.byId(r.faction()).map(FactionStore.Faction::name)
                            .orElse(r.faction()),
                    "won", r.won(), "fought", r.fought(),
                    "taken", r.attacksWon(), "held", r.defencesHeld()));
        }
        return rows.size();
    }

    /**
     * A faction-name argument that can actually hold a faction name.
     *
     * <p><b>{@code word()} accepts letters, digits and {@code _.+-} and nothing else</b> — so a
     * faction called "Lantern Vale" was unaddressable by every command that took one. Not refused:
     * <em>unparseable</em>, answering "Expected whitespace to end one argument", which names
     * nothing and reads like the typist's mistake. The same trap has now cost this pair four
     * features (see Standards' {@code CLAUDE.md}); it is a rule, not an anecdote.</p>
     *
     * <p>Greedy, so it takes the rest of the line. That is only safe where the name is the
     * <b>last</b> argument — {@code /f bank pay} has an amount after it and uses {@link #quotedName}
     * instead.</p>
     */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            factionName() {
        return Commands.argument("faction", StringArgumentType.greedyString())
                .suggests(FactionCommands::suggestFactions);
    }

    /**
     * A faction name with something after it.
     *
     * <p>{@code string()} reads a bare word, or a quoted phrase when the input opens with a quote —
     * so {@code /f bank pay Ashfell 100} is unchanged and {@code /f bank pay "Lantern Vale" 100}
     * now works. Nobody types those quotes by hand, which is the usual objection; here they do not
     * have to, because {@link #suggestQuotedFactions} puts them in.</p>
     */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            quotedName() {
        return Commands.argument("faction", StringArgumentType.string())
                .suggests(FactionCommands::suggestQuotedFactions);
    }

    /** Longest a faction name may be. Long enough for a phrase, short enough for a chat prefix. */
    private static final int MAX_NAME = 24;

    /**
     * Whether a name is one somebody may actually have, complaining if not.
     *
     * <p>Needed the moment the argument stopped being {@code word()}: brigadier used to reject a
     * name with a space, a colour code or two hundred characters in it by refusing to parse, and
     * nothing here had to think about it. Greedy takes the lot, so the rules move into code — where
     * they belong anyway, because now the message can say <em>which</em> rule was broken.</p>
     */
    private static boolean nameIsSane(ServerPlayer player, String name) {
        if (name.isBlank() || name.length() > MAX_NAME) {
            Feedback.chat(player, Lang.fmt("msg.factions.name_length", "max", MAX_NAME));
            return false;
        }
        // A name is printed on every chat line and every claim message. Codes in one would let a
        // faction paint the rest of the line, or wear a colour it did not earn.
        if (name.indexOf('&') >= 0 || name.indexOf('\u00a7') >= 0
                || !name.equals(name.trim()) || name.contains("  ")
                || name.chars().noneMatch(Character::isLetterOrDigit)) {
            Feedback.chat(player, Lang.get("msg.factions.name_bad"));
            return false;
        }
        return true;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestFactions(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                store(ctx).all().stream().map(FactionStore.Faction::name).toList(), builder);
    }

    /** The same names, quoted where they need to be, for the one argument that is not greedy. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestQuotedFactions(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(store(ctx).all().stream()
                .map(FactionStore.Faction::name)
                .map(n -> n.chars().allMatch(c -> Character.isLetterOrDigit(c)
                        || "_.+-".indexOf(c) >= 0) ? n : '"' + n + '"')
                .toList(), builder);
    }

    private FactionCommands() {}
}

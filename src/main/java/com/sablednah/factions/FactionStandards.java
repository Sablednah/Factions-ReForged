package com.sablednah.factions;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;

/**
 * The faction standard: a real banner, standing where everyone can see it, that an enemy can come
 * and take.
 *
 * <h2>It is a banner, not a marker</h2>
 *
 * <p>The colour and the pattern are the faction's own — designed in a loom, like anybody else's
 * banner, and then <b>that design is the faction's identity</b>. The colour it wears in chat comes
 * from the flag it planted, so a faction is personalised by an object it made rather than by a
 * setting it typed.</p>
 *
 * <h2>It must see the sky</h2>
 *
 * <p>The rule that turns this from a mechanic into a game. Enemies cannot break your blocks or
 * open your doors, so the only way anyone reaches your standard is a path you left. Without the
 * sky rule everybody entombs their flag in a sealed box, it is unreachable by construction, and
 * the feature is dead on arrival.</p>
 *
 * <p>With it, the standard is necessarily <em>outside</em>, visible, and defended by architecture
 * and people rather than by burial. Placing it becomes a real decision with a real trade: high on
 * a tower where the whole server can see whose land this is, or in a walled courtyard that is
 * duller and harder to reach.</p>
 *
 * <h2>Losing it costs the bonus, never more</h2>
 *
 * <p>A planted standard makes power come back faster. Taking one denies that. <b>The worst case of
 * planting a flag is the case of never having planted one</b> — there is no state below not
 * having one, so putting it up is never mechanically worse than leaving it down, and the only
 * thing you spent is the opportunity you gave an enemy. That is the shape a symbol needs: it can
 * be lost, but it cannot be turned into a stick to beat you with.</p>
 */
public final class FactionStandards {

    /**
     * Standards broken this tick, and whose they were.
     *
     * <p>Needed because renaming the drop cannot happen in the break event: the item does not
     * exist yet. By the time {@code BlockDropsEvent} fires the standard has already been cleared
     * from the store, so the answer has to be carried across the gap. Keyed by position, cleared
     * as it is consumed.</p>
     */
    private static final java.util.Map<BlockPos, String> JUST_TAKEN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether this block is a banner of any kind, standing or wall-mounted. */
    public static boolean isBanner(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof AbstractBannerBlock;
    }

    /**
     * The banner a player is looking at, allowing for the fact that a standing banner's hit shape
     * is a thin pole occupying only part of its block.
     *
     * <p>Aim at the cloth — the part anybody would call "the banner" — and the ray passes through
     * empty air above the shape and hits whatever is behind. So the block the ray landed on is
     * checked, and then the two below it, because a banner is visually taller than it is
     * clickable and people aim at what they can see.</p>
     */
    public static Optional<BlockPos> lookingAtBanner(ServerPlayer player, ServerLevel level) {
        if (!(player.pick(6.0D, 0.0F, false)
                instanceof net.minecraft.world.phys.BlockHitResult hit)) {
            return Optional.empty();
        }
        BlockPos pos = hit.getBlockPos();
        for (BlockPos candidate : new BlockPos[] {pos, pos.below(), pos.below(2)}) {
            if (isBanner(level, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Designate the banner in front of you as your faction's standard.
     *
     * @return true if it took
     */
    public static boolean designate(ServerPlayer player, ServerLevel level, BlockPos pos,
            FactionStore.Faction faction) {
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);

        if (!isBanner(level, pos)) {
            Feedback.chat(player, Lang.get("msg.factions.standard_not_banner"));
            return false;
        }
        if (!store.ownerOf(dim, pos.getX() >> 4, pos.getZ() >> 4)
                .map(faction.id()::equals).orElse(false)) {
            Feedback.chat(player, Lang.get("msg.factions.standard_not_your_land"));
            return false;
        }
        if (!seesSky(level, pos)) {
            Feedback.chat(player, Lang.get("msg.factions.standard_needs_sky"));
            return false;
        }
        // Somebody else's captured flag cannot be re-flagged as your own identity. It stays what
        // it is — theirs, in your hands — which is the whole reason it is worth taking.
        if (store.standardAt(dim, pos).isPresent()) {
            Feedback.chat(player, Lang.get("msg.factions.standard_already"));
            return false;
        }

        BannerPatternLayers patterns = BannerPatternLayers.EMPTY;
        DyeColor colour = DyeColor.WHITE;
        Optional<net.minecraft.network.chat.Component> named = Optional.empty();
        if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
            patterns = banner.getPatterns();
            colour = banner.getBaseColor();
            named = Optional.ofNullable(banner.getCustomName());
        }

        // While an enemy is FLYING your standard, you cannot simply raise another. Without this
        // the trophy is worthless: you would shrug, plant a fresh banner, and their prize would
        // deny you nothing. Going and taking it back is the point.
        //
        // Flying, not merely held. A thief who roofs their trophy over earns nothing from it, and
        // it stops denying you at the same moment — so "capture it and bury it" is not a way to
        // keep somebody flagless forever. One visibility rule governs both halves.
        Optional<FactionStore.Faction> thief = store.all().stream()
                .filter(other -> store.standardCapturedFrom(other.id())
                        .map(faction.id()::equals).orElse(false))
                .filter(other -> flying(other.id()))
                .findFirst();
        if (thief.isPresent()) {
            Feedback.chat(player, Lang.fmt("msg.factions.standard_still_taken",
                    "name", thief.get().name()));
            return false;
        }

        // Or somebody is carrying it home right now. Told with their position, because a carrier
        // who could simply walk away and wait is not a target — and being a target while your
        // hands are full is the whole trade.
        Optional<ServerPlayer> carrier = carriedBy(level.getServer(), store, faction.id());
        if (carrier.isPresent()) {
            ServerPlayer holder = carrier.get();
            Feedback.chat(player, Lang.fmt("msg.factions.standard_carried",
                    "player", holder.getName().getString(),
                    "x", holder.blockPosition().getX(),
                    "y", holder.blockPosition().getY(),
                    "z", holder.blockPosition().getZ(),
                    "world", FactionBridge.dimensionOf((ServerLevel) holder.level())));
            return false;
        }

        // Is this somebody else's flag, being flown as a trophy? The name a taken standard carries
        // survives being placed, so vanilla's own data answers it and nothing has to track the
        // item across chests, hoppers and deaths.
        Optional<String> capturedFrom = named.flatMap(name -> whoseStandard(store, name));
        if (capturedFrom.map(faction.id()::equals).orElse(false)) {
            // Your own flag, recovered. It is yours again rather than a trophy.
            capturedFrom = Optional.empty();
        }

        store.setStandard(faction.id(), dim, pos, colour, patterns, capturedFrom);
        FLYING.put(faction.id(), true);

        // Name the banner itself, now rather than when it falls. Two reasons, and the second is
        // the load-bearing one:
        //  - it is visibly a standard while it stands, so nobody wonders which banner matters;
        //  - the name is what identifies it once it is an item in somebody's bag, and setting it
        //    at the moment of designation means every route the item can take out of the world
        //    starts from a block that already carries it.
        String owner = capturedFrom.flatMap(store::byId)
                .map(FactionStore.Faction::name).orElse(faction.name());
        if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
            // Through the component map: BlockEntity has no setCustomName, and CUSTOM_NAME is
            // exactly the component a broken banner carries into its dropped item.
            banner.setComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .addAll(banner.components())
                    .set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            Feedback.colored(
                                    Lang.fmt("msg.factions.standard_item", "name", owner)))
                    .build());
            banner.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        }
        if (capturedFrom.isPresent()) {
            String theirs = store.byId(capturedFrom.get())
                    .map(FactionStore.Faction::name).orElse("?");
            Feedback.chat(player, Lang.fmt("msg.factions.standard_planted_trophy", "name", theirs));
            // Told to them, because a trophy flown where they cannot see it is not a trophy, and
            // because taking it back is now their raid — which is the point.
            store.byId(capturedFrom.get()).ifPresent(loser -> {
                String where = Lang.fmt("msg.factions.standard_trophy_seen",
                        "name", faction.name(), "x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
                for (java.util.UUID member : loser.memberIds()) {
                    ServerPlayer online = level.getServer().getPlayerList().getPlayer(member);
                    if (online != null) {
                        Feedback.chat(online, where);
                    }
                }
            });
        } else {
            Feedback.chat(player, Lang.fmt("msg.factions.standard_set",
                    "colour", colour.getName().replace('_', ' ')));
        }
        return true;
    }

    /**
     * Whoever is carrying this faction's standard <b>in their hands</b>, if anybody.
     *
     * <h3>In hand, deliberately — not in a bag</h3>
     *
     * <p>This is what makes the journey home the dangerous part of a raid. A flag in a backpack is
     * invisible and costs its carrier nothing; a flag <em>in your hands</em> is a flag you are
     * holding instead of a sword. You cannot carry somebody's colours and fight properly at the
     * same time, so getting it home is something your friends have to make happen.</p>
     *
     * <p>And it means hiding is not a strategy: while you hold it you are denying its owner a new
     * one, and the refusal they get names <b>where you are</b>. Put it in a chest and you stop
     * being a target — but you also stop denying them anything, so they simply raise another.</p>
     *
     * <p>Cheap: online players only, two item stacks each. Run when somebody tries to raise a
     * standard, which is rare.</p>
     */
    public static Optional<ServerPlayer> carriedBy(net.minecraft.server.MinecraftServer server,
            FactionStore store, String factionId) {
        String wanted = store.byId(factionId)
                .map(f -> com.sablednah.standards.neoforge.Feedback.stripCodes(
                        Lang.fmt("msg.factions.standard_item", "name", f.name())))
                .orElse(null);
        if (wanted == null) {
            return Optional.empty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (holding(player.getMainHandItem(), wanted)
                    || holding(player.getOffhandItem(), wanted)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    private static boolean holding(ItemStack stack, String wantedName) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.BannerItem)) {
            return false;
        }
        net.minecraft.network.chat.Component name =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().equals(wantedName);
    }

    /** Which faction a banner's name says it belongs to, if any. */
    private static Optional<String> whoseStandard(FactionStore store,
            net.minecraft.network.chat.Component name) {
        String plain = name.getString();
        for (FactionStore.Faction f : store.all()) {
            String expected = com.sablednah.standards.neoforge.Feedback.stripCodes(
                    Lang.fmt("msg.factions.standard_item", "name", f.name()));
            if (plain.equals(expected)) {
                return Optional.of(f.id());
            }
        }
        return Optional.empty();
    }

    /**
     * Open sky above, with nothing in the way.
     *
     * <p>Deliberately the strict reading: not "the heightmap says this is the top", which a
     * glass roof or a single slab satisfies, but nothing solid in the whole column. A flag under
     * cover is a flag nobody can take, and the cover would be one block thick.</p>
     */
    public static boolean seesSky(ServerLevel level, BlockPos pos) {
        for (int y = pos.getY() + 1; y < level.getMaxY(); y++) {
            BlockPos above = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.getBlockState(above).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A standard has been broken. Work out whose, and by whom, and tell everybody.
     *
     * @return true if this was a standard, so the caller knows the break was meaningful
     */
    public static boolean onBroken(ServerLevel level, BlockPos pos, ServerPlayer breaker) {
        FactionStore store = FactionStore.get(level.getServer());
        String dim = FactionBridge.dimensionOf(level);
        Optional<String> flying = store.standardAt(dim, pos);
        if (flying.isEmpty()) {
            return false;
        }
        String flyerId = flying.get();
        Optional<FactionStore.Faction> flyer = store.byId(flyerId);
        // Whose identity it actually is: the faction that made it, which may not be the faction
        // that was flying it.
        String ownerId = store.standardCapturedFrom(flyerId).orElse(flyerId);
        store.clearStandard(flyerId);

        String flyerName = flyer.map(FactionStore.Faction::name).orElse("?");
        String ownerName = store.byId(ownerId).map(FactionStore.Faction::name).orElse(flyerName);
        Optional<FactionStore.Faction> taker = store.of(breaker.getUUID());

        // Announced to the whole server, because the entire value of taking a flag is that
        // everybody knows. A humiliation nobody witnessed is just a missing block.
        level.getServer().getPlayerList().broadcastSystemMessage(
                Feedback.colored(Lang.fmt("msg.factions.standard_taken",
                        "taker", taker.map(FactionStore.Faction::name)
                                .orElse(breaker.getName().getString()),
                        "name", ownerName)),
                false);

        // Remembered for the drop, which does not exist yet.
        JUST_TAKEN.put(pos.immutable(), ownerName);

        // And said plainly to the losers, because the consequence is not obvious from the
        // announcement: their power now comes back slower until they raise another.
        store.byId(flyerId).ifPresent(loser -> {
            for (java.util.UUID member : loser.memberIds()) {
                ServerPlayer online = level.getServer().getPlayerList().getPlayer(member);
                if (online != null) {
                    Feedback.chat(online, Lang.get("msg.factions.standard_lost"));
                }
            }
        });
        return true;
    }

    /**
     * Rename a broken standard's drop.
     *
     * <p>Vanilla's banner loot table does <b>not</b> carry a block entity's custom name onto the
     * item — verified by breaking one and finding a plain banner. So naming the block at
     * designation, which made it visibly a standard while it stood, was necessary and not
     * sufficient: the item has to be named again on the way out.</p>
     *
     * <p>Patterns survive on their own, so a taken flag still looks exactly like the one that was
     * flying.</p>
     *
     * @return true if this position was a standard
     */
    public static boolean renameDrops(BlockPos pos, java.util.List<?> drops) {
        String owner = JUST_TAKEN.remove(pos.immutable());
        if (owner == null) {
            return false;
        }
        for (Object entry : drops) {
            if (!(entry instanceof net.minecraft.world.entity.item.ItemEntity item)) {
                continue;
            }
            ItemStack stack = item.getItem();
            if (stack.getItem() instanceof net.minecraft.world.item.BannerItem) {
                item.setItem(asTrophy(stack, owner));
                // It does not despawn. A flag that quietly evaporated five minutes into the
                // journey home would end a raid with nobody having done anything, and neither
                // side would know why.
                item.setUnlimitedLifetime();
            }
        }
        return true;
    }

    /**
     * The item a broken standard leaves behind, carrying whose it was.
     *
     * <p>Written into the banner's custom name rather than a bespoke component, so it survives
     * every route an item can take through a vanilla world — a hopper, an ender chest, a death, a
     * trade — and so anybody looking at it in an inventory can see what they are holding.</p>
     */
    public static ItemStack asTrophy(ItemStack banner, String ownerName) {
        ItemStack copy = banner.copy();
        copy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Feedback.colored(Lang.fmt("msg.factions.standard_item", "name", ownerName)));
        // Fireproof, because a captured flag should be lost to somebody taking it off you rather
        // than to the lava you happened to fight over. The return journey is meant to be the
        // dangerous part of a raid; losing the prize to terrain is not danger, it is a shrug.
        copy.set(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT,
                new net.minecraft.world.item.component.DamageResistant(
                        net.minecraft.tags.DamageTypeTags.IS_FIRE));
        return copy;
    }

    /** Whether each faction's standard is currently valid, refreshed periodically. */
    private static final java.util.Map<String, Boolean> FLYING =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static int checkCounter;

    /**
     * Is this faction's flag actually flying?
     *
     * <p>A cached answer, refreshed on a timer — the scan itself walks a block column and is not
     * something to do for every player every few seconds.</p>
     */
    public static boolean flying(String factionId) {
        return FLYING.getOrDefault(factionId, true);
    }

    /**
     * Re-check every standard, because <b>the sky rule has to keep being true</b>.
     *
     * <p>Testing it once at designation would be an invitation: plant the flag in the open,
     * satisfy the rule, then roof it over. That is the first thing anybody would try, and it would
     * leave the feature looking intact while being entirely defeated.</p>
     *
     * <h3>Covering it stops the bonus rather than forfeiting the flag</h3>
     *
     * <p>The bonus is paid for flying a <em>visible</em> flag, so a covered one simply stops
     * earning, and starts again the moment the roof comes off. Confiscating it instead would
     * punish somebody whose neighbour built a bridge, and make an accident permanent.</p>
     *
     * <p><b>A destroyed banner is different</b> and does clear the standard: if the block is gone
     * there is nothing to uncover. That covers pistons, explosions, and every route out of the
     * world that is not an enemy breaking it by hand.</p>
     *
     * <p>Unloaded chunks are skipped rather than treated as failures. Nobody should lose a flag
     * because their base is not currently loaded, and force-loading chunks to check a decoration
     * is not a trade worth making.</p>
     */
    public static void revalidate(net.minecraft.server.MinecraftServer server) {
        // Every ten seconds. A roof takes longer than that to build, and this is a block column
        // per faction rather than something to run every tick.
        if (++checkCounter % 200 != 0) {
            return;
        }
        FactionStore store = FactionStore.get(server);
        for (FactionStore.Faction f : store.all()) {
            Optional<BlockPos> where = store.standardPos(f.id());
            if (where.isEmpty()) {
                FLYING.remove(f.id());
                continue;
            }
            ServerLevel level = store.standardDimension(f.id())
                    .flatMap(d -> FactionBridge.levelFor(server, d)).orElse(null);
            if (level == null || !level.isLoaded(where.get())) {
                continue; // not loaded: keep the last answer rather than inventing one
            }
            BlockPos pos = where.get();
            if (!isBanner(level, pos)) {
                store.clearStandard(f.id());
                FLYING.remove(f.id());
                announce(server, f, Lang.get("msg.factions.standard_gone"));
                continue;
            }
            boolean nowFlying = seesSky(level, pos);
            Boolean was = FLYING.put(f.id(), nowFlying);
            if (was != null && was != nowFlying) {
                // Said once on each change, never repeated every ten seconds.
                announce(server, f, Lang.get(nowFlying
                        ? "msg.factions.standard_uncovered" : "msg.factions.standard_covered"));
            }
        }
    }

    private static void announce(net.minecraft.server.MinecraftServer server,
            FactionStore.Faction f, String message) {
        for (java.util.UUID member : f.memberIds()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                Feedback.chat(online, message);
            }
        }
    }

    /**
     * The chat colour code for a dye colour.
     *
     * <p>Where a faction's identity comes from: it designed a banner, planted it, and now wears
     * that colour wherever its <em>name</em> is printed. Not where its <em>relation</em> is printed
     * — the map, the borders and the territory announcements stay green-for-yours,
     * blue-for-allied, red-for-hostile, because that answers "what does this mean to me" and no
     * amount of personalisation should be able to make an enemy's land look friendly.</p>
     *
     * <p>So identity and relation are separate palettes on purpose, and this is the identity
     * one.</p>
     */
    public static String chatColour(DyeColor colour) {
        return switch (colour) {
            case WHITE -> "&f";
            case ORANGE -> "&6";
            case MAGENTA, PINK -> "&d";
            case LIGHT_BLUE -> "&b";
            case YELLOW -> "&e";
            case LIME -> "&a";
            case GRAY -> "&8";
            case LIGHT_GRAY -> "&7";
            case CYAN -> "&3";
            case PURPLE -> "&5";
            case BLUE -> "&9";
            case BROWN -> "&6";
            case GREEN -> "&2";
            case RED -> "&c";
            case BLACK -> "&0";
        };
    }

    /** A faction's own colour, or plain white until it plants a flag. */
    public static String chatColour(FactionStore store, String factionId) {
        return chatColour(store.colourOf(factionId));
    }

    private FactionStandards() {}
}

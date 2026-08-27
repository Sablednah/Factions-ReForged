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

    /** Whether this block is a banner of any kind, standing or wall-mounted. */
    public static boolean isBanner(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof AbstractBannerBlock;
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

        // Is this somebody else's flag, being flown as a trophy? The name a taken standard carries
        // survives being placed, so vanilla's own data answers it and nothing has to track the
        // item across chests, hoppers and deaths.
        Optional<String> capturedFrom = named.flatMap(name -> whoseStandard(store, name));
        if (capturedFrom.map(faction.id()::equals).orElse(false)) {
            // Your own flag, recovered. It is yours again rather than a trophy.
            capturedFrom = Optional.empty();
        }

        store.setStandard(faction.id(), dim, pos, colour, patterns, capturedFrom);

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
        return copy;
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

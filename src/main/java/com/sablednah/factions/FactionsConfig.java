package com.sablednah.factions;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Everything a server owner may want to disagree with. */
public final class FactionsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue CLAIM_LIMIT_PER_MEMBER;
    public static final ModConfigSpec.BooleanValue REQUIRE_CONNECTED_CLAIMS;
    public static final ModConfigSpec.BooleanValue PVP_BETWEEN_FACTIONS;
    public static final ModConfigSpec.BooleanValue PVP_IN_OWN_LAND;
    public static final ModConfigSpec.IntValue BORDER_PARTICLE_TICKS;
    public static final ModConfigSpec.IntValue BORDER_RADIUS_CHUNKS;
    public static final ModConfigSpec.ConfigValue<String> BORDER_ITEM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Land.").push("claims");
        CLAIM_LIMIT_PER_MEMBER = b
                .comment("Chunks a faction may hold, per member. -1 for no limit.",
                        "Scaling with membership rather than a flat cap is what stops one person",
                        "fencing off a continent, and it gives recruiting a point beyond numbers.")
                .defineInRange("chunksPerMember", 16, -1, 100_000);
        REQUIRE_CONNECTED_CLAIMS = b
                .comment("New claims must touch land the faction already holds.",
                        "On by default. Territory that is one contiguous shape is territory you",
                        "can see the edge of; scattered single chunks across a whole world are a",
                        "land-grab tactic rather than a place.",
                        "The first claim is exempt, obviously.")
                .define("mustBeConnected", true);
        b.pop();

        b.comment("Fighting.").push("pvp");
        PVP_BETWEEN_FACTIONS = b
                .comment("Members of different factions may fight.",
                        "Turn off for a co-operative server; a faction is then a claim and a",
                        "chat tag rather than a side.")
                .define("betweenFactions", true);
        PVP_IN_OWN_LAND = b
                .comment("Members of the SAME faction may fight each other.",
                        "Off by default — friendly fire inside your own faction is almost always",
                        "an accident, and the one time it is not, there is a /f kick for that.")
                .define("withinAFaction", false);
        b.pop();

        b.comment("Showing the border.").push("borders");
        BORDER_PARTICLE_TICKS = b
                .comment("Ticks between particle refreshes while borders are shown. 0 disables",
                        "the particle display entirely.")
                .defineInRange("refreshTicks", 10, 0, 200);
        BORDER_RADIUS_CHUNKS = b
                .comment("How many chunks around you to outline. Larger is prettier and costs",
                        "more packets; 1 means the chunk you are stood in and its neighbours.")
                .defineInRange("radiusChunks", 1, 0, 8);
        BORDER_ITEM = b
                .comment("Hold this item and borders appear without toggling anything.",
                        "A block id, or blank for none. The point is that you pick up the tool,",
                        "see what you are doing, and put it down again — the same way vanilla's",
                        "own debug stick works.")
                .define("heldItem", "minecraft:compass");
        b.pop();

        SPEC = b.build();
    }

    private FactionsConfig() {}
}

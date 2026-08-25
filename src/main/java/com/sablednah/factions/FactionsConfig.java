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
    public static final ModConfigSpec.BooleanValue BORDER_FOLLOW_GROUND;
    public static final ModConfigSpec.IntValue MAP_PIXELS_PER_CHUNK;
    public static final ModConfigSpec.BooleanValue OFFICERS_MAY_ACCEPT;
    public static final ModConfigSpec.BooleanValue FIXTURES;

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
        BORDER_FOLLOW_GROUND = b
                .comment("Stand the border on the ground rather than at your own feet.",
                        "On by default. Drawn at a flat height it is buried in the first hill it",
                        "meets and floating over the first valley — and a border you cannot see",
                        "at the exact moment you are walking over it is the one case that",
                        "mattered. Costs a heightmap lookup per particle column.")
                .define("followGround", true);
        b.pop();

        b.comment("Asking to join.").push("requests");
        OFFICERS_MAY_ACCEPT = b
                .comment("Officers may accept requests to join, not only the leader.",
                        "On by default, because an officer can already /f invite whoever they",
                        "like — letting them invite a stranger but not accept one who asked",
                        "first is a rule nobody could explain. Turn it off on a server where",
                        "the leader wants to vet every member personally.")
                .define("officersMayAccept", true);
        b.pop();

        b.comment("The claims atlas.").push("map");
        MAP_PIXELS_PER_CHUNK = b
                .comment("How many map pixels one chunk occupies, for /f map item.",
                        "1 is a vanilla map at full zoom-out: 128 chunks across, the whole",
                        "region at once, and the default because that is what an atlas is for.",
                        "Raise it to zoom in — 2 shows 64 chunks, 4 shows 32, 8 shows 16 — for",
                        "a server whose factions hold a handful of chunks each and get lost on",
                        "the wide view. Must be a power of two: a map pixel covers 1 << scale",
                        "blocks and there is nothing in between.")
                .defineInRange("pixelsPerChunk", 1, 1, 8);
        b.pop();

        b.comment("Testing.").push("debug");
        FIXTURES = b
                .comment("Register /f fixture, which invents factions to have relations with.",
                        "OFF, and it unregisters the command rather than refusing it — a server",
                        "that will never run it should not offer it in tab-complete.",
                        "Two people cannot test a relation system: allied, hostile, offered-but-",
                        "not-returned and peaceful need four counterparties, and inviting six",
                        "friends to sit still while you declare war on them is not a test plan.")
                .define("fixtures", false);
        b.pop();

        SPEC = b.build();
    }

    private FactionsConfig() {}
}

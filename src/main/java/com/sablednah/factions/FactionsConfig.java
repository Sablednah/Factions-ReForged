package com.sablednah.factions;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Everything a server owner may want to disagree with. */
public final class FactionsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue CLAIM_LIMIT_PER_MEMBER;
    public static final ModConfigSpec.BooleanValue REQUIRE_CONNECTED_CLAIMS;
    public static final ModConfigSpec.BooleanValue PROTECT_INTERACTION;
    public static final ModConfigSpec.BooleanValue ALLIES_MAY_INTERACT;
    public static final ModConfigSpec.BooleanValue ALLIES_MAY_BUILD;
    public static final ModConfigSpec.BooleanValue ANYONE_MAY_ROTATE_FRAMES;
    public static final ModConfigSpec.BooleanValue BLOCK_MOB_EXPLOSIONS;
    public static final ModConfigSpec.BooleanValue BLOCK_TNT;
    public static final ModConfigSpec.BooleanValue PVP_BETWEEN_FACTIONS;
    public static final ModConfigSpec.BooleanValue PVP_IN_OWN_LAND;
    public static final ModConfigSpec.BooleanValue PVP_BETWEEN_ALLIES;
    public static final ModConfigSpec.IntValue BORDER_PARTICLE_TICKS;
    public static final ModConfigSpec.IntValue BORDER_RADIUS_CHUNKS;
    public static final ModConfigSpec.ConfigValue<String> BORDER_ITEM;
    public static final ModConfigSpec.BooleanValue BORDER_FOLLOW_GROUND;
    public static final ModConfigSpec.IntValue MAP_PIXELS_PER_CHUNK;
    public static final ModConfigSpec.BooleanValue OFFICERS_MAY_ACCEPT;
    public static final ModConfigSpec.BooleanValue FIXTURES;
    public static final ModConfigSpec.ConfigValue<String> POWER_MODE;
    public static final ModConfigSpec.DoubleValue POWER_MAX;
    public static final ModConfigSpec.DoubleValue POWER_MIN;
    public static final ModConfigSpec.DoubleValue POWER_PER_DEATH;
    public static final ModConfigSpec.DoubleValue POWER_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue POWER_PER_XP;
    public static final ModConfigSpec.BooleanValue POWER_START_AT_ZERO;
    public static final ModConfigSpec.BooleanValue OVERCLAIM_ENEMIES_ONLY;
    public static final ModConfigSpec.IntValue POWER_FREEZE_SECONDS;
    public static final ModConfigSpec.DoubleValue CLAIM_COST;
    public static final ModConfigSpec.DoubleValue CLAIM_COST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CLAIM_REFUND;
    public static final ModConfigSpec.BooleanValue OFFICERS_MAY_WITHDRAW;

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
        PROTECT_INTERACTION = b
                .comment("Right-clicking a block in somebody's claim does nothing.",
                        "On by default, and it is the protection that matters: guarding only",
                        "block-breaking protects the walls and leaves everything behind them",
                        "open. A stranger who cannot mine your chest can still OPEN it.",
                        "Deliberately not a list of protected blocks — a list is something",
                        "somebody has to maintain, and every modded block is missing from it.",
                        "Pressure plates still work, because standing on one is not a",
                        "right-click. Put a plate outside the door for visitors: protection you",
                        "can open a hole in beats protection you have to switch off.")
                .define("protectInteraction", true);
        ALLIES_MAY_INTERACT = b
                .comment("Allies may open your doors, chests and buttons.",
                        "On by default — an ally who cannot open your gate stands outside it.")
                .define("alliesMayInteract", true);
        ALLIES_MAY_BUILD = b
                .comment("Allies may build and break in your land.",
                        "OFF by default, and separate from interaction on purpose. An alliance",
                        "is a diplomatic position and those change; your walls should not be",
                        "hostage to the week somebody fell out.")
                .define("alliesMayBuild", false);
        ANYONE_MAY_ROTATE_FRAMES = b
                .comment("Anybody may turn an item frame that already holds something.",
                        "Off by default. Rotation carries meaning where frames are used as",
                        "signage or sorting labels, and a stranger spinning those is petty",
                        "griefing rather than fun.",
                        "Turn it ON for a roleplay server, where it is the opposite: coming back",
                        "to find your frames turned is proof somebody walked through your base,",
                        "and a way to say 'I was here' that breaks nothing and starts wars.",
                        "Taking the item is still refused either way — this permits turning it,",
                        "never emptying it, and never filling an empty frame.")
                .define("anyoneMayRotateFrames", false);
        BLOCK_MOB_EXPLOSIONS = b
                .comment("Creepers, ghasts, withers and end crystals cannot crater claimed land.",
                        "On by default. A creeper is not a raid — nobody decided it, nobody",
                        "gains from it, and a claim that stops a person walking in but not a mob",
                        "wandering in is protection with a hole shaped like a Tuesday evening.",
                        "Players still take the damage. Only the blocks are spared.")
                .define("blockMobExplosions", true);
        BLOCK_TNT = b
                .comment("TNT cannot break blocks in claimed land either.",
                        "On by default, and this one is a genuine choice rather than an obvious",
                        "one. On a PvP faction server TNT IS the raid tool: it is how a siege",
                        "gets through a wall, and turning it off means a well-built base can",
                        "never be taken. On a PvE or build server it is purely how somebody",
                        "erases your evening.",
                        "Default reflects this being a PvE-leaning mod. A war server should turn",
                        "it off and expect cannons — literally: placing a block in somebody's",
                        "claim is refused whatever this says, so with TNT allowed the way",
                        "through a wall is to deliver a charge rather than stack one against it.")
                .define("blockTnt", true);
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
        PVP_BETWEEN_ALLIES = b
                .comment("Allied factions may fight each other.",
                        "Off by default. Friendly fire between allies is off in every version of",
                        "this game anybody remembers, and an alliance that stops you being",
                        "overclaimed while doing nothing to stop you being shot is not much of",
                        "an alliance.")
                .define("betweenAllies", false);
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

        b.comment("The faction bank.").push("money");
        CLAIM_COST = b
                .comment("What the first chunk costs. 0 turns claim costs off entirely, which is",
                        "the default — a server with no economy mod must not find claiming",
                        "silently impossible.",
                        "Paid by the FACTION, not the claimer. That is what makes the bank",
                        "something a faction uses together rather than a shared piggy bank",
                        "nobody touches.")
                .defineInRange("claimCost", 0.0D, 0.0D, 1_000_000.0D);
        CLAIM_COST_MULTIPLIER = b
                .comment("How much more each further chunk costs, as a fraction of the first.",
                        "0.5 means the 2nd costs 1.5x the 1st, the 3rd 2x, and so on.",
                        "Rising prices are the point: a flat price means the largest faction —",
                        "the one that needs land least — buys it most easily.")
                .defineInRange("claimCostGrowth", 0.5D, 0.0D, 100.0D);
        CLAIM_REFUND = b
                .comment("Fraction of the original price returned when a chunk is released.",
                        "Priced at the position the chunk occupied, never today's price, or a",
                        "faction could buy cheap while small, grow, and release the same chunk",
                        "for a profit — a money printer fuelled by claiming and unclaiming one",
                        "square.",
                        "Priced by POSITION, not by receipt — nothing records what an individual",
                        "chunk cost, so land claimed while this was 0 still refunds once you",
                        "turn it on. Best set on a fresh world: an established faction on 67",
                        "chunks at cost 30 / growth 0.5 can release its way to about 25,000.")
                .defineInRange("claimRefund", 0.7D, 0.0D, 1.0D);
        OFFICERS_MAY_WITHDRAW = b
                .comment("Officers may take money out, not only the leader.",
                        "Depositing is never gated: a member funding the next claim should not",
                        "need permission to give money away, and the griefing direction is out.")
                .define("officersMayWithdraw", true);
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

        b.comment("Power: how much of its entitlement a faction is actually holding onto.",
                        "",
                        "FIXED IS THE CEILING; POWER IS THE EROSION. chunksPerMember still decides",
                        "what a faction is entitled to, and power decides how much of that it is",
                        "currently holding. Power can only ever take entitlement AWAY — never",
                        "grant more than the fixed rule would. So turning this on does not hand",
                        "out or confiscate anybody's land, it adds a way to lose land by playing",
                        "badly. It also means farming power cannot inflate land, because the",
                        "ceiling is membership.",
                        "",
                        "AND THE POINT IS THE OVERCLAIMING. A faction holding more land than its",
                        "power covers can have the difference taken by an enemy. Without that,",
                        "power is a claim limit with extra steps and you already have one.")
                .push("power");
        POWER_MODE = b
                .comment("What drains power. The modes differ in NOTHING else — restoration is",
                        "the same rule throughout, so a mode only says what counts as losing.",
                        "  fixed - power is inert. Today's behaviour, and the default, so an",
                        "          upgrade never rearranges a running server.",
                        "  pvp   - being killed by a player drains.",
                        "  pve   - being killed by a mob drains. Territory shrinks when the WORLD",
                        "          beats you, which no faction plugin has ever expressed and is",
                        "          the actual fiction of a survival or apocalypse server.",
                        "  both  - either.")
                .define("mode", "fixed");
        POWER_MAX = b
                .comment("A player's maximum power. The original used 10.")
                .defineInRange("maxPerPlayer", 10.0D, 1.0D, 10_000.0D);
        POWER_MIN = b
                .comment("The floor. Negative is allowed and is a real state: a player who keeps",
                        "dying can drag their faction's entitlement below what its membership",
                        "alone would give, which is the difference between a bad night and a",
                        "liability.")
                .defineInRange("minPerPlayer", 0.0D, -10_000.0D, 10_000.0D);
        POWER_PER_DEATH = b
                .comment("Power lost per qualifying death. The original took 4 of 10.",
                        "Environmental deaths NEVER count — falling in your own lava is not a",
                        "raid, nobody decided it and nobody gains from it. Standards' combat API",
                        "answers who was really behind a kill, through arrows and pets.")
                .defineInRange("perDeath", 2.0D, 0.0D, 10_000.0D);
        POWER_PER_MINUTE = b
                .comment("Power regained per minute ONLINE. The original gave 0.2 — fifty minutes",
                        "from empty to full.",
                        "This single number sets how the server feels more than any other: fifty",
                        "minutes makes a raid window something you exploit that evening, five",
                        "hours makes it something you plan a week around.")
                .defineInRange("perMinuteOnline", 0.2D, 0.0D, 100.0D);
        POWER_PER_XP = b
                .comment("Power regained per point of experience a mob DROPS when you kill it.",
                        "The elegant half: the drop value is already Minecraft's own opinion of",
                        "how hard something was to kill, maintained by Mojang and extended for",
                        "free by every mod on the server. A ZombieMod tank outweighs a walker",
                        "without ZombieMod telling us anything, with no registry of mob ids to",
                        "keep and nothing to be wrong about the day a modpack adds a boss.",
                        "Read from the mob's drop, never from your XP balance — otherwise",
                        "smelting is a land claim and enchanting costs you territory.",
                        "0 disables it and leaves recovery purely time-based.")
                .defineInRange("perExperience", 0.02D, 0.0D, 100.0D);
        POWER_FREEZE_SECONDS = b
                .comment("Seconds after a death during which that player regains no power.",
                        "Stops a raid being outrun by the clock. Resets on each further death",
                        "rather than stacking.")
                .defineInRange("freezeSecondsAfterDeath", 30, 0, 3600);
        POWER_START_AT_ZERO = b
                .comment("New players start at zero power rather than full.",
                        "OFF, unlike the original. Starting at zero means a brand-new faction can",
                        "claim nothing for the best part of an hour, which is a ritual on a server",
                        "built around it and simply baffling on one where somebody installed this",
                        "expecting it to behave like the claim limit it replaces.")
                .define("startAtZero", false);
        OVERCLAIM_ENEMIES_ONLY = b
                .comment("Only a DECLARED ENEMY may take land from an over-extended faction.",
                        "On by default, and a deliberate divergence: the original let anybody who",
                        "was not your ally do it. Requiring a declaration makes /f peaceful follow",
                        "from one rule instead of two, and makes a raid something somebody said",
                        "out loud — a warning, a public record, and a line in the victim's",
                        "/f status before anything is taken.")
                .define("overclaimEnemiesOnly", true);
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

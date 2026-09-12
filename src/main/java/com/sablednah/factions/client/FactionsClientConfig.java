package com.sablednah.factions.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The two numbers only the person at the keyboard can answer.
 *
 * <h2>⚠ Why these are client-side when almost nothing else here is</h2>
 *
 * <p>Nearly every setting in this pair belongs to the server, because nearly every setting decides
 * a <b>fact</b> — what a claim costs, who may take one, how fast power falls. A fact must be the
 * same for everybody or the mod is lying to somebody.</p>
 *
 * <p>These two decide neither. They decide how much <i>drawing</i> a particular machine is willing
 * to do, and that answer differs between a laptop and a desktop, and nobody but the owner of the
 * machine knows it. The server still holds the ceiling ({@code borders.maxRadiusChunks}), because
 * the packets are its cost to pay — so this is a <b>request</b>, and what comes back is what gets
 * drawn.</p>
 *
 * <p>And it costs a vanilla client nothing: {@code /f borders radius} is a real command, so a
 * typist gets the same reach in particles. This file only saves the typing.</p>
 */
public final class FactionsClientConfig {

    public static final ModConfigSpec SPEC;

    /** How far to ask the server to send claims for, in chunks. Bounded by the server's ceiling. */
    public static final ModConfigSpec.IntValue BORDER_RADIUS;

    /** How far the tinted ground reaches. Much smaller, deliberately: it is 256 quads a chunk. */
    public static final ModConfigSpec.IntValue FLOOR_RADIUS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("How much of the faction border this machine draws.").push("borders");
        BORDER_RADIUS = b
                .comment("How many chunks each way to draw walls for. The server has the final",
                        "say — it clamps anything past its own maxRadiusChunks — so raising this",
                        "past what a server allows costs nothing and does nothing.",
                        "Defaults to the maximum, measured on a real GPU rather than guessed: at",
                        "8 walls and 2 of floor the whole display costs under ten frames a second,",
                        "and each extra chunk of reach is a large jump in the ground you can see",
                        "the edge of.")
                .defineInRange("radiusChunks", 8, 0, 8);
        FLOOR_RADIUS = b
                .comment("How many chunks each way to tint the ground under. Much smaller than",
                        "the walls on purpose: this is one quad per column, 256 per chunk, every",
                        "frame. The walls answer 'where does it end'; the floor answers 'am I",
                        "standing in it', and that question is only ever about here. 0 leaves the",
                        "walls and turns the tinted ground off.",
                        "2 is where the cost stops being free. Measured from 100fps idle: 8 walls",
                        "with 2 of floor sits in the low 90s, and 4 of floor drops it to 70.")
                .defineInRange("floorRadiusChunks", 2, 0, 4);
        b.pop();
        SPEC = b.build();
    }

    private FactionsClientConfig() {}
}

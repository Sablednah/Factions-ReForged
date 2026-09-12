package com.sablednah.factions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The claims around one player, so a modded client can draw them as geometry instead of particles.
 *
 * <h2>⚠ Why this is allowed to exist at all</h2>
 *
 * <p>Standards' decision 2 says a vanilla client gets everything, and this payload does not break
 * it: the particle border already shows exactly this — which chunks are claimed, and what each
 * owner is to you. A vanilla client keeps the particles and learns the same facts. What the modded
 * client gets is a <b>better surface for the same answer</b>, which is the rule the whole client
 * half of this pair runs on: a button instead of a typed command, a solid wall instead of a dotted
 * line of particles. Never a fact a typist could not have.</p>
 *
 * <p>It carries the <b>relation</b> rather than the faction id, for the same reason the JourneyMap
 * border does: the colour is a property of the pair, the client has no business knowing who owns
 * what beyond that, and an id would invite a client to draw things about factions it has never
 * met.</p>
 *
 * <h2>Bounded by construction</h2>
 *
 * <p>The radius is the server's, from config, and the chunk coordinates are sent relative to the
 * centre — so the whole thing is two bytes per chunk and cannot grow with the size of the world or
 * the number of factions. A radius of 4 is 81 chunks; the packet is smaller than this comment.</p>
 */
public record ClaimsNearbyPayload(int centreX, int centreZ, int radius, byte[] relations)
        implements CustomPacketPayload {

    /** What a chunk is to the viewer. Ordinals travel on the wire, so do not reorder them. */
    public static final byte WILDERNESS = 0;
    public static final byte OWN = 1;
    public static final byte ALLY = 2;
    public static final byte ENEMY = 3;
    public static final byte OTHER = 4;

    public static final Type<ClaimsNearbyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Factions.MODID, "claims_nearby"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimsNearbyPayload> CODEC =
            StreamCodec.of(ClaimsNearbyPayload::encode, ClaimsNearbyPayload::decode);

    /** Matches the config's own ceiling, so a bad radius cannot make an enormous packet. */
    private static final int MAX_RADIUS = 8;

    /** The relation of one chunk, by absolute chunk coordinate. */
    public byte at(int chunkX, int chunkZ) {
        int dx = chunkX - centreX + radius;
        int dz = chunkZ - centreZ + radius;
        int side = radius * 2 + 1;
        if (dx < 0 || dz < 0 || dx >= side || dz >= side) {
            return WILDERNESS;
        }
        return relations[dz * side + dx];
    }

    /** Every claimed chunk in here, as {@code {x, z}} pairs — the input ClaimOutline wants. */
    public List<int[]> claimedChunks() {
        List<int[]> out = new ArrayList<>();
        int side = radius * 2 + 1;
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                if (relations[dz * side + dx] != WILDERNESS) {
                    out.add(new int[] {centreX - radius + dx, centreZ - radius + dz});
                }
            }
        }
        return out;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClaimsNearbyPayload p) {
        buf.writeVarInt(p.centreX());
        buf.writeVarInt(p.centreZ());
        buf.writeVarInt(p.radius());
        buf.writeByteArray(p.relations());
    }

    private static ClaimsNearbyPayload decode(RegistryFriendlyByteBuf buf) {
        int x = buf.readVarInt();
        int z = buf.readVarInt();
        int r = buf.readVarInt();
        // ⚠ Clamped on the READ. A payload is whatever arrived, and a hostile or simply broken
        // radius would otherwise size an array before anything had a chance to object.
        if (r < 0 || r > MAX_RADIUS) {
            r = 0;
        }
        int side = r * 2 + 1;
        byte[] rel = buf.readByteArray(side * side);
        if (rel.length != side * side) {
            rel = new byte[side * side];
        }
        return new ClaimsNearbyPayload(x, z, r, rel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

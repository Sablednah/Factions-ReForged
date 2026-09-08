package com.sablednah.factions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Everything the faction panel draws, in one message.
 *
 * <h2>Sent in answer to a command, never pushed</h2>
 *
 * <p>{@code /f panel} asks for it. That is deliberate and it is what keeps the promise: a vanilla
 * client typing the same command gets the same facts as text, and this payload is the nicer surface
 * for the identical answer. There is no serverbound payload — the request is a command like every
 * other, so permissions and config switches apply without a second code path.</p>
 *
 * <p>It also means a panel nobody has open costs nothing. Nothing here is pushed on a timer; the
 * screen asks again when the player reopens it.</p>
 */
public record FactionPanelPayload(
        String name, String tag, boolean peaceful,
        double power, double maxPower, int claims, int entitlement,
        double bank, String standardState, int trophies,
        int raidsWon, int raidsFought,
        List<String> allies, List<String> enemies,
        List<Member> members,
        String yourRank) implements CustomPacketPayload {

    /** One row of the member list. */
    public record Member(String name, String rank, boolean online) {}

    public static final Type<FactionPanelPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Factions.MODID, "panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionPanelPayload> CODEC =
            StreamCodec.of(FactionPanelPayload::encode, FactionPanelPayload::decode);

    /** A cap, because a payload with no bound becomes one the first time a faction gets popular. */
    private static final int MAX = 512;

    private static void encode(RegistryFriendlyByteBuf buf, FactionPanelPayload p) {
        buf.writeUtf(p.name(), 64);
        buf.writeUtf(p.tag(), 32);
        buf.writeBoolean(p.peaceful());
        buf.writeDouble(p.power());
        buf.writeDouble(p.maxPower());
        buf.writeVarInt(p.claims());
        buf.writeVarInt(p.entitlement());
        buf.writeDouble(p.bank());
        buf.writeUtf(p.standardState(), 128);
        buf.writeVarInt(p.trophies());
        buf.writeVarInt(p.raidsWon());
        buf.writeVarInt(p.raidsFought());
        writeNames(buf, p.allies());
        writeNames(buf, p.enemies());
        int count = Math.min(p.members().size(), MAX);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(p.members().get(i).name(), 64);
            buf.writeUtf(p.members().get(i).rank(), 32);
            buf.writeBoolean(p.members().get(i).online());
        }
        buf.writeUtf(p.yourRank(), 32);
    }

    private static FactionPanelPayload decode(RegistryFriendlyByteBuf buf) {
        String name = buf.readUtf(64);
        String tag = buf.readUtf(32);
        boolean peaceful = buf.readBoolean();
        double power = buf.readDouble();
        double maxPower = buf.readDouble();
        int claims = buf.readVarInt();
        int entitlement = buf.readVarInt();
        double bank = buf.readDouble();
        String standard = buf.readUtf(128);
        int trophies = buf.readVarInt();
        int won = buf.readVarInt();
        int fought = buf.readVarInt();
        List<String> allies = readNames(buf);
        List<String> enemies = readNames(buf);
        int count = Math.min(buf.readVarInt(), MAX);
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            members.add(new Member(buf.readUtf(64), buf.readUtf(32), buf.readBoolean()));
        }
        return new FactionPanelPayload(name, tag, peaceful, power, maxPower, claims, entitlement,
                bank, standard, trophies, won, fought, allies, enemies, members, buf.readUtf(32));
    }

    private static void writeNames(RegistryFriendlyByteBuf buf, List<String> names) {
        int count = Math.min(names.size(), MAX);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(names.get(i), 64);
        }
    }

    private static List<String> readNames(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(buf.readUtf(64));
        }
        return List.copyOf(out);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

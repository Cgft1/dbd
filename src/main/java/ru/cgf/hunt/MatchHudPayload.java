package ru.cgf.hunt;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MatchHudPayload(int repairedGenerators, int deadSurvivors) implements CustomPayload {
    public static final Id<MatchHudPayload> ID = new Id<>(Identifier.of("hunt_generators", "match_hud"));
    public static final PacketCodec<RegistryByteBuf, MatchHudPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.repairedGenerators());
            buf.writeVarInt(value.deadSurvivors());
        },
        buf -> new MatchHudPayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
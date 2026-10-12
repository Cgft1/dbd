package ru.cgf.hunt;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GeneratorHudPayload(int progress, int skillTicks, boolean repairing) implements CustomPayload {
    public static final Id<GeneratorHudPayload> ID = new Id<>(Identifier.of("hunt_generators", "generator_hud"));
    public static final PacketCodec<RegistryByteBuf, GeneratorHudPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.progress());
            buf.writeVarInt(value.skillTicks());
            buf.writeBoolean(value.repairing());
        },
        buf -> new GeneratorHudPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

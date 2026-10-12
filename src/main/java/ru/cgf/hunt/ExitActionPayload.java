package ru.cgf.hunt;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ExitActionPayload(String action) implements CustomPayload {
    public static final Id<ExitActionPayload> ID =
        new Id<>(Identifier.of("hunt_generators", "exit_action"));
    public static final PacketCodec<RegistryByteBuf, ExitActionPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.action()),
        buf -> new ExitActionPayload(buf.readString())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

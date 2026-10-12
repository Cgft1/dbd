package ru.cgf.hunt;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RolePayload(String role) implements CustomPayload {
    public static final Id<RolePayload> ID = new Id<>(Identifier.of("hunt_generators", "role"));
    public static final PacketCodec<RegistryByteBuf, RolePayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.role()),
        buf -> new RolePayload(buf.readString())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
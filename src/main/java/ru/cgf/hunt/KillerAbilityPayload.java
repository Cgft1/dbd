package ru.cgf.hunt;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KillerAbilityPayload(String ability) implements CustomPayload {
    public static final Id<KillerAbilityPayload> ID = new Id<>(Identifier.of("hunt_generators", "killer_ability"));
    public static final PacketCodec<RegistryByteBuf, KillerAbilityPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.ability()),
        buf -> new KillerAbilityPayload(buf.readString())
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

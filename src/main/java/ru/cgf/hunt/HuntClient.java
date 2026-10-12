package ru.cgf.hunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.Perspective;

public class HuntClient implements ClientModInitializer {
    private static String role = "";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) ->
            context.client().execute(() -> {
                role = payload.role();
                applyPerspective(context.client());
            })
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !role.isEmpty()) {
                applyPerspective(client);
                if (client.options.sneakKey.wasPressed()) {
                    ClientPlayNetworking.send(new ExitActionPayload("shift"));
                }
            }
        });
    }

    private static void applyPerspective(net.minecraft.client.MinecraftClient client) {
        Perspective required = "killer".equals(role) ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK;
        if (client.options.getPerspective() != required) client.options.setPerspective(required);
    }
}
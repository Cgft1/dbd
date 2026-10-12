package ru.cgf.hunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.KeyBinding;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class HuntClient implements ClientModInitializer {
    private static String role = "";
    private static KeyBinding killerYKey;
    private static KeyBinding killerUKey;
    private static KeyBinding killerIKey;

    @Override
    public void onInitializeClient() {
        killerYKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_y", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.hunt_generators.killer"));
        killerUKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_u", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.hunt_generators.killer"));
        killerIKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_i", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.hunt_generators.killer"));
        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) ->
            context.client().execute(() -> {
                role = payload.role();
                applyPerspective(context.client());
                if ("survivor".equals(role)) {
                    context.client().setScreen(new SurvivorGuideScreen());
                }
            })
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !role.isEmpty()) {
                applyPerspective(client);
                if ("killer".equals(role)) {
                    if (killerYKey.wasPressed()) ClientPlayNetworking.send(new KillerAbilityPayload("y"));
                    if (killerUKey.wasPressed()) ClientPlayNetworking.send(new KillerAbilityPayload("u"));
                    if (killerIKey.wasPressed()) ClientPlayNetworking.send(new KillerAbilityPayload("i"));
                }
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
package ru.cgf.hunt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
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
    private static KeyBinding openGuideKey;
    private static int generatorProgress;
    private static int skillTicks;
    private static boolean repairing;

    @Override
    public void onInitializeClient() {
        killerYKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_y", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.hunt_generators.killer"));
        killerUKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_u", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.hunt_generators.killer"));
        killerIKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_i", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.hunt_generators.killer"));
        openGuideKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.open_guide", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.hunt_generators.general"));
        ClientPlayNetworking.registerGlobalReceiver(GeneratorHudPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                generatorProgress = payload.progress();
                skillTicks = payload.skillTicks();
                repairing = payload.repairing();
            })
        );
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!repairing || clientPlayerMissing()) return;
            drawGeneratorHud(drawContext);
        });
        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) ->
            context.client().execute(() -> {
                String newRole = payload.role();
                boolean roleChanged = !newRole.equals(role);
                role = newRole;
                applyPerspective(context.client());
                // The server currently sends the role payload every tick. Only open the guide
                // when the player first becomes a survivor, never on every repeated payload.
                if (roleChanged && "survivor".equals(role)) {
                    context.client().setScreen(new SurvivorGuideScreen());
                }
            })
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && openGuideKey.wasPressed() && "survivor".equals(role)) {
                client.setScreen(new SurvivorGuideScreen());
            }
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

    private static boolean clientPlayerMissing() {
        return net.minecraft.client.MinecraftClient.getInstance().player == null;
    }

    private static void drawGeneratorHud(DrawContext context) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        int center = client.getWindow().getScaledWidth() / 2;
        int top = client.getWindow().getScaledHeight() - 76;
        int width = 210;
        int left = center - width / 2;
        context.fill(left - 8, top - 8, left + width + 8, top + 52, 0xDD101820);
        context.fill(left - 8, top - 8, left + width + 8, top - 6, 0xFF42C8D5);
        context.drawCenteredTextWithShadow(client.textRenderer, "ПОЧИНКА ГЕНЕРАТОРА", center, top, 0xFFFFFFFF);
        context.fill(left, top + 15, left + width, top + 25, 0xFF333D46);
        context.fill(left, top + 15, left + width * Math.max(0, Math.min(100, generatorProgress)) / 100, top + 25, 0xFF36C98F);
        context.drawCenteredTextWithShadow(client.textRenderer, generatorProgress + "%", center, top + 15, 0xFFFFFFFF);
        if (skillTicks > 0) {
            int barTop = top + 34;
            context.fill(left, barTop, left + width, barTop + 8, 0xFF333D46);
            int zoneLeft = left + width * 62 / 100;
            int zoneRight = left + width * 76 / 100;
            context.fill(zoneLeft, barTop, zoneRight, barTop + 8, 0xFF42D879);
            int cursor = left + width * ((40 - Math.max(0, Math.min(40, skillTicks))) * 5 / 2) / 100;
            context.fill(cursor - 2, barTop - 2, cursor + 2, barTop + 10, 0xFFFFD45A);
            context.drawCenteredTextWithShadow(client.textRenderer, "ПРОВЕРКА! ПКМ ПО ГЕНЕРАТОРУ", center, barTop + 10, 0xFFFFD45A);
        }
    }

    private static void applyPerspective(net.minecraft.client.MinecraftClient client) {
        Perspective required = "killer".equals(role) ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK;
        if (client.options.getPerspective() != required) client.options.setPerspective(required);
    }
}
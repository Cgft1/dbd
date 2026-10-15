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
import net.minecraft.particle.ParticleTypes;
import org.lwjgl.glfw.GLFW;

public class HuntClient implements ClientModInitializer {
    private static String role = "";
    private static KeyBinding killerYKey;
    private static KeyBinding killerUKey;
    private static KeyBinding killerIKey;
    private static KeyBinding killerDashKey;
    private static int generatorProgress;
    private static int skillTicks;
    private static boolean repairing;
    private static int repairAnimationTicks;
    private static int runAnimationTicks;
    private static int repairedGenerators;
    private static int deadSurvivors;

    public static boolean isInMatch() {
        return !role.isEmpty();
    }

    @Override
    public void onInitializeClient() {
        killerYKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_y", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.hunt_generators.killer"));
        killerUKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_u", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.hunt_generators.killer"));
        killerIKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_ability_i", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.hunt_generators.killer"));
        killerDashKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunt_generators.killer_dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.hunt_generators.killer"));
        ClientPlayNetworking.registerGlobalReceiver(GeneratorHudPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                generatorProgress = payload.progress();
                skillTicks = payload.skillTicks();
                repairing = payload.repairing();
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(MatchHudPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                repairedGenerators = payload.repairedGenerators();
                deadSurvivors = payload.deadSurvivors();
            })
        );
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (clientPlayerMissing() || role.isEmpty()) return;
            drawMatchHud(drawContext);
            if (repairing) drawGeneratorHud(drawContext);
        });
        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) ->
            context.client().execute(() -> {
                role = payload.role();
                if (role.isEmpty()) {
                    repairing = false;
                    repairAnimationTicks = 0;
                    runAnimationTicks = 0;
                }
                applyPerspective(context.client());
            })
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || role.isEmpty()) return;
            applyPerspective(client);

            // Repeated hand movements make generator repair look like active work.
            if (repairing) {
                repairAnimationTicks++;
                if (repairAnimationTicks >= 9) {
                    client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    repairAnimationTicks = 0;
                }
            } else {
                repairAnimationTicks = 0;
            }

            // A light trail at the feet gives sprinting a more dynamic visual feel.
            if (client.player.isSprinting() && client.world != null && !client.player.isFallFlying()) {
                runAnimationTicks++;
                if (runAnimationTicks % 3 == 0) {
                    double side = (client.world.random.nextDouble() - 0.5) * 0.45;
                    client.world.addParticle(ParticleTypes.CLOUD,
                        client.player.getX() + side, client.player.getY() + 0.08,
                        client.player.getZ() + side, 0.0, 0.015, 0.0);
                    if (runAnimationTicks % 9 == 0) {
                        client.world.addParticle(ParticleTypes.CRIT,
                            client.player.getX(), client.player.getY() + 0.2,
                            client.player.getZ(), 0.0, 0.025, 0.0);
                    }
                }
            } else {
                runAnimationTicks = 0;
            }

            if ("killer".equals(role)) {
                if (killerYKey.wasPressed()) ClientPlayNetworking.send(new KillerAbilityPayload("y"));
                if (killerUKey.wasPressed()) ClientPlayNetworking.send(new KillerAbilityPayload("u"));
                if (killerIKey.wasPressed()) ClientPlayNetworking.send(new KillerAbilityPayload("i"));
                if (killerDashKey.wasPressed() && client.getNetworkHandler() != null) client.getNetworkHandler().sendChatCommand("hunt dash");
            }
            if (client.options.sneakKey.wasPressed()) {
                ClientPlayNetworking.send(new ExitActionPayload("shift"));
            }
        });
    }

    private static boolean clientPlayerMissing() {
        return net.minecraft.client.MinecraftClient.getInstance().player == null;
    }

    private static void drawMatchHud(DrawContext context) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        context.fill(8, 8, 154, 43, 0xC9101720);
        context.fill(8, 8, 11, 43, 0xFF42C8D5);
        context.drawTextWithShadow(client.textRenderer, "Генераторы: " + repairedGenerators + "/5", 18, 15, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, "Погибло: " + deadSurvivors, 18, 29, 0xFFFF7777);
        if ("killer".equals(role)) {
            int x = sw - 146;
            int y = sh - 82;
            context.fill(x - 6, y - 6, sw - 8, sh - 8, 0xD9101720);
            context.drawTextWithShadow(client.textRenderer, "СПОСОБНОСТИ", x, y, 0xFFFF7777);
            context.drawTextWithShadow(client.textRenderer, "[Y] Кровавое чутьё", x, y + 15, 0xFFFFFFFF);
            context.drawTextWithShadow(client.textRenderer, "[U] Ярость", x, y + 28, 0xFFFFFFFF);
            context.drawTextWithShadow(client.textRenderer, "[I] Третья способность", x, y + 41, 0xFFFFFFFF);
            context.drawTextWithShadow(client.textRenderer, "[V] Рывок", x, y + 54, 0xFFB8D9FF);
        }
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
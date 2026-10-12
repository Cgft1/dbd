package ru.cgf.hunt;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Story items and first playable slice of the Night Shift investigation.
 * Compatible with the existing Hunt: Generators mod on Minecraft 1.21.1 / Fabric.
 */
public final class NightShiftStory implements ModInitializer {
    public static final Item GUARD_LOG = register("guard_log", new Item(new Item.Settings().maxCount(1)));
    public static final Item LAB_REPORT = register("lab_report", new Item(new Item.Settings().maxCount(1)));
    public static final Item INCIDENT_NOTE = register("incident_note", new Item(new Item.Settings().maxCount(1)));
    public static final Item LAB_KEYCARD = register("lab_keycard", new Item(new Item.Settings().maxCount(1)));
    public static final Item UV_LAMP = register("uv_lamp", new Item(new Item.Settings().maxDamage(96)));

    private static final Map<UUID, Set<String>> READ_CLUES = new HashMap<>();
    private static final Set<UUID> ARCHIVE_UNLOCKED = new HashSet<>();

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of("hunt_generators", path), item);
    }

    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient) return TypedActionResult.pass(stack);

            String clue = null;
            String title = null;
            String body = null;

            if (stack.isOf(GUARD_LOG)) {
                clue = "guard_log";
                title = "Журнал охранника — 02:17";
                body = "На камере № 4 снова стоял силуэт. Я проверил коридор — никого. Когда вернулся, камера уже смотрела прямо на меня. Если услышишь три удара по стене — НЕ ОТВЕЧАЙ.";
            } else if (stack.isOf(LAB_REPORT)) {
                clue = "lab_report";
                title = "Секретный отчёт № 07";
                body = "Образец 07 реагирует на голос бывшего персонала. Не воспроизводить аудиозаписи рядом с камерой содержания. При отключении питания немедленно закрыть шлюз B.";
            } else if (stack.isOf(INCIDENT_NOTE)) {
                clue = "incident_note";
                title = "Записка учёного";
                body = "Код аварийного шлюза спрятан в датах испытаний. Лифт в лабораторию находится за старой генераторной. Карта доступа третьего уровня открывает нижний архив.";
            } else if (stack.isOf(LAB_KEYCARD)) {
                player.sendMessage(Text.literal("§cКАРТА ДОСТУПА — УРОВЕНЬ 3§r. На ней выгравировано: LAB-07. Ищи запечатанную дверь в нижнем архиве."), true);
                return TypedActionResult.success(stack, false);
            } else if (stack.isOf(UV_LAMP)) {
                player.sendMessage(Text.literal("§dУльтрафиолет включён.§r Осматривай стены и документы в тёмных помещениях: скрытые улики нужно будет искать вручную на карте."), true);
                return TypedActionResult.success(stack, false);
            }

            if (clue != null && player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("§6[" + title + "]§r"), false);
                serverPlayer.sendMessage(Text.literal(body), false);
                Set<String> found = READ_CLUES.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>());
                found.add(clue);
                if (found.size() >= 3 && ARCHIVE_UNLOCKED.add(player.getUuid())) {
                    serverPlayer.giveItemStack(new ItemStack(LAB_KEYCARD));
                    serverPlayer.sendMessage(Text.literal("§cАРХИВ СОБРАН§r: все три улики прочитаны. Ты получил ключ-карту уровня 3. Подземная лаборатория существует."), false);
                    serverPlayer.getWorld().playSound(null, serverPlayer.getBlockPos(),
                        net.minecraft.sound.SoundEvents.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER,
                        net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 0.65f);
                }
                return TypedActionResult.success(stack, false);
            }
            return TypedActionResult.pass(stack);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("nightshift")
                .then(literal("items").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    give(player, GUARD_LOG);
                    give(player, LAB_REPORT);
                    give(player, INCIDENT_NOTE);
                    give(player, UV_LAMP);
                    player.sendMessage(Text.literal("§cНочная смена§r: выданы три сюжетные улики и ультрафиолетовый фонарь. Прочитай улики ПКМ."), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("status").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    int count = READ_CLUES.getOrDefault(player.getUuid(), Set.of()).size();
                    player.sendMessage(Text.literal("§cНочная смена§r — улик изучено: " + count + "/3. Команда /nightshift items выдаёт предметы для теста."), false);
                    if (ARCHIVE_UNLOCKED.contains(player.getUuid())) {
                        player.sendMessage(Text.literal("Доступ к нижнему архиву открыт. Найди дверь лаборатории на карте."), false);
                    }
                    return Command.SINGLE_SUCCESS;
                }))
            )
        );
    }

    private static void give(ServerPlayerEntity player, Item item) {
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }
}

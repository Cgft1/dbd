package ru.cgf.hunt;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HuntMod implements ModInitializer {
    public static final Block GENERATOR_BLOCK = Registry.register(Registries.BLOCK,
        Identifier.of("hunt_generators", "generator"), new Block(Block.Settings.copy(Blocks.IRON_BLOCK).strength(4.0f)));
    public static final Item GENERATOR_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "generator"), new BlockItem(GENERATOR_BLOCK, new Item.Settings()));
    private static final Map<UUID, String> ROLES = new HashMap<>();
    private static final Set<BlockPos> GENERATORS = new LinkedHashSet<>();
    private static final Set<BlockPos> REPAIRED = new HashSet<>();
    private static final Map<BlockPos, Integer> REPAIR_PROGRESS = new HashMap<>();
    private static final Map<UUID, BlockPos> REPAIRING = new HashMap<>();
    private static final Map<UUID, Integer> REPAIR_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> SKILL_CHECK_WINDOW = new HashMap<>();
    private static BlockPos gatePos = null;
    private static boolean running = false;
    private static boolean gateOpened = false;
    private static boolean escapeAnnounced = false;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("hunt")
                .then(literal("join")
                    .then(argument("role", StringArgumentType.word()).executes(ctx -> {
                        var source = ctx.getSource();
                        ServerPlayerEntity player = source.getPlayerOrThrow();
                        String role = StringArgumentType.getString(ctx, "role").toLowerCase(Locale.ROOT);
                        if (running) {
                            source.sendError(Text.literal("Раунд уже идёт."));
                            return 0;
                        }
                        if (!role.equals("killer") && !role.equals("survivor")) {
                            source.sendError(Text.literal("Используй: /hunt join killer или /hunt join survivor"));
                            return 0;
                        }
                        long killers = ROLES.values().stream().filter("killer"::equals).count();
                        long survivors = ROLES.values().stream().filter("survivor"::equals).count();
                        if (role.equals("killer") && killers >= 1 && !"killer".equals(ROLES.get(player.getUuid()))) {
                            source.sendError(Text.literal("Убийца уже выбран. Убийца может быть только один."));
                            return 0;
                        }
                        if (role.equals("survivor") && survivors >= 5 && !"survivor".equals(ROLES.get(player.getUuid()))) {
                            source.sendError(Text.literal("Максимум выживших — 5."));
                            return 0;
                        }
                        ROLES.put(player.getUuid(), role);
                        source.sendFeedback(() -> Text.literal("Твоя команда: " + (role.equals("killer") ? "Убийцы" : "Выжившие")), false);
                        return 1;
                    })))
                .then(literal("start").executes(ctx -> start(ctx.getSource().getServer(), ctx.getSource().getPlayerOrThrow())))
                .then(literal("status").executes(ctx -> {
                    var source = ctx.getSource();
                    source.sendFeedback(() -> Text.literal("Генераторы: " + REPAIRED.size() + "/5. Ворота: " + (gateOpened ? "открыты" : "закрыты") + ". Раунд: " + (running ? "идёт" : "не запущен") + "."), false);
                    return 1;
                }))
                .then(literal("generator").then(literal("add").executes(ctx -> {
                    var source = ctx.getSource();
                    BlockPos pos = BlockPos.ofFloored(source.getPosition()).offset(source.getPlayerOrThrow().getHorizontalFacing()).toImmutable();
                    source.getWorld().setBlockState(pos, GENERATOR_BLOCK.getDefaultState());
                    if (GENERATORS.contains(pos)) {
                        source.sendError(Text.literal("Этот генератор уже добавлен."));
                        return 0;
                    }
                    if (GENERATORS.size() >= 5) {
                        source.sendError(Text.literal("Уже добавлено 5 генераторов. Для новой настройки используй /hunt resetconfig."));
                        return 0;
                    }
                    GENERATORS.add(pos);
                    source.sendFeedback(() -> Text.literal("Блок генератора установлен перед тобой на " + pos.toShortString() + " (" + GENERATORS.size() + "/5)."), false);
                    return 1;
                })))
                .then(literal("gate").then(literal("add").executes(ctx -> {
                    gatePos = BlockPos.ofFloored(ctx.getSource().getPosition()).toImmutable();
                    gateOpened = false;
                    escapeAnnounced = false;
                    ctx.getSource().sendFeedback(() -> Text.literal("Ворота настроены на " + gatePos.toShortString() + ". Встань на блок ворот и выполни команду."), false);
                    return 1;
                })))
                .then(literal("reset").executes(ctx -> {
                    running = false;
                    gateOpened = false;
                    escapeAnnounced = false;
                    REPAIRED.clear();
                    ROLES.clear();
                    ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal("Раунд сброшен. Конфигурация генераторов и ворот сохранена."), false);
                    return 1;
                }))
                .then(literal("resetconfig").executes(ctx -> {
                    running = false;
                    gateOpened = false;
                    escapeAnnounced = false;
                    REPAIRED.clear();
                    ROLES.clear();
                    GENERATORS.clear();
                    gatePos = null;
                    ctx.getSource().sendFeedback(() -> Text.literal("Настройки генераторов и ворот очищены."), false);
                    return 1;
                }))
            );
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(world instanceof ServerWorld serverWorld) || !running) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos().toImmutable();
            if (!GENERATORS.contains(pos)) return ActionResult.PASS;
            UUID id = player.getUuid();
            if (!"survivor".equals(ROLES.get(id))) {
                player.sendMessage(Text.literal("Только выжившие могут чинить генераторы."), true);
                return ActionResult.FAIL;
            }
            if (REPAIRED.contains(pos)) {
                player.sendMessage(Text.literal("Этот генератор уже починен."), true);
                return ActionResult.FAIL;
            }
            if (pos.equals(REPAIRING.get(id))) {
                if (SKILL_CHECK_WINDOW.getOrDefault(id, 0) > 0) {
                    SKILL_CHECK_WINDOW.put(id, 0);
                    player.sendMessage(Text.literal("Отлично! Проверка пройдена."), true);
                } else {
                    player.sendMessage(Text.literal("Мини-игра ещё не активна — дождись сигнала!"), true);
                }
                return ActionResult.SUCCESS;
            }
            REPAIRING.put(id, pos);
            REPAIR_TICKS.put(id, 0);
            SKILL_CHECK_WINDOW.put(id, 0);
            player.sendMessage(Text.literal("Начата починка. Прогресс: " + REPAIR_PROGRESS.getOrDefault(pos, 0) + "%"), true);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID id = p.getUuid();
                BlockPos target = REPAIRING.get(id);
                if (target == null) continue;
                if (!"survivor".equals(ROLES.get(id)) || p.squaredDistanceTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 25) {
                    REPAIRING.remove(id);
                    REPAIR_TICKS.remove(id);
                    SKILL_CHECK_WINDOW.remove(id);
                    p.sendMessage(Text.literal("Починка прервана."), true);
                    continue;
                }
                int window = SKILL_CHECK_WINDOW.getOrDefault(id, 0);
                if (window > 0) {
                    SKILL_CHECK_WINDOW.put(id, window - 1);
                    if (window == 1) {
                        REPAIR_PROGRESS.put(target, Math.max(0, REPAIR_PROGRESS.getOrDefault(target, 0) - 10));
                        p.sendMessage(Text.literal("Не успел! Потеряно 10% прогресса."), true);
                    }
                }
                int ticks = REPAIR_TICKS.getOrDefault(id, 0) + 1;
                REPAIR_TICKS.put(id, ticks);
                if (ticks >= 20) {
                    REPAIR_TICKS.put(id, 0);
                    int progress = Math.min(100, REPAIR_PROGRESS.getOrDefault(target, 0) + 1);
                    REPAIR_PROGRESS.put(target, progress);
                    p.sendMessage(Text.literal("Прогресс генератора: " + progress + "%"), true);
                    if (progress >= 100) {
                        REPAIRED.add(target);
                        REPAIRING.remove(id);
                        REPAIR_TICKS.remove(id);
                        SKILL_CHECK_WINDOW.remove(id);
                        server.getPlayerManager().broadcast(Text.literal("Генератор починен! Прогресс матча: " + REPAIRED.size() + "/5"), false);
                        if (REPAIRED.size() == 5) {
                            gateOpened = true;
                            if (gatePos != null) server.getOverworld().setBlockState(gatePos, Blocks.AIR.getDefaultState());
                            server.getPlayerManager().broadcast(Text.literal("Все генераторы починены! Ворота открыты — выжившие, бегите!"), false);
                        }
                    } else if (progress % 10 == 0 && SKILL_CHECK_WINDOW.getOrDefault(id, 0) == 0) {
                        SKILL_CHECK_WINDOW.put(id, 40);
                        p.sendMessage(Text.literal("МИНИ-ИГРА! Нажми ПКМ по генератору в течение 2 секунд!"), true);
                    }
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running || !gateOpened || gatePos == null || escapeAnnounced) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if ("survivor".equals(ROLES.get(player.getUuid())) &&
                    player.getBlockPos().isWithinDistance(gatePos, 3.0)) {
                    escapeAnnounced = true;
                    running = false;
                    server.getPlayerManager().broadcast(Text.literal("Выжившие сбежали! Победа выживших!"), false);
                    break;
                }
            }
        });
    }

    private static int start(MinecraftServer server, ServerPlayerEntity commandPlayer) {
        if (running) {
            commandPlayer.sendMessage(Text.literal("Раунд уже идёт."), false);
            return 0;
        }
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (players.size() < 2 || players.size() > 6) {
            commandPlayer.sendMessage(Text.literal("Для игры нужно от 2 до 6 игроков: 1 убийца и 1–5 выживших."), false);
            return 0;
        }
        if (GENERATORS.size() != 5 || gatePos == null) {
            commandPlayer.sendMessage(Text.literal("Сначала настрой 5 генераторов и ворота командами /hunt generator add и /hunt gate add."), false);
            return 0;
        }
        Collections.shuffle(players);
        ROLES.clear();
        ROLES.put(players.get(0).getUuid(), "killer");
        for (int i = 1; i < players.size(); i++) ROLES.put(players.get(i).getUuid(), "survivor");
        REPAIRED.clear();
        REPAIR_PROGRESS.clear(); REPAIRING.clear(); REPAIR_TICKS.clear(); SKILL_CHECK_WINDOW.clear();
        gateOpened = false;
        escapeAnnounced = false;
        running = true;
        server.getPlayerManager().broadcast(Text.literal("Раунд начался! Убийца: " + players.get(0).getName().getString() + ". Выжившие: " + (players.size() - 1) + ". Почините 5 генераторов и доберитесь до ворот!"), false);
        for (ServerPlayerEntity p : players) {
            p.sendMessage(Text.literal("Твоя роль: " + (ROLES.get(p.getUuid()).equals("killer") ? "УБИЙЦА" : "ВЫЖИВШИЙ")), false);
        }
        return 1;
    }
}

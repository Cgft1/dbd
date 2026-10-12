package ru.cgf.hunt;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.TypedActionResult;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.world.GameMode;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HuntMod implements ModInitializer {
    public static final Block GENERATOR_BLOCK = Registry.register(Registries.BLOCK,
        Identifier.of("hunt_generators", "generator"), new Block(Block.Settings.copy(Blocks.IRON_BLOCK).strength(4.0f)));
    public static final Item GENERATOR_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "generator"), new BlockItem(GENERATOR_BLOCK, new Item.Settings()));
    public static final Block LOCKER_BLOCK = Registry.register(Registries.BLOCK,
        Identifier.of("hunt_generators", "locker"), new Block(Block.Settings.copy(Blocks.BARREL).strength(3.0f)));
    public static final Item LOCKER_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "locker"), new BlockItem(LOCKER_BLOCK, new Item.Settings()));
    public static final Item TOOLBOX_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "toolbox"), new Item(new Item.Settings().maxDamage(200)));
    public static final Item MEDKIT_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "medkit"), new Item(new Item.Settings().maxDamage(4)));
    public static final Item FLASHLIGHT_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "flashlight"), new Item(new Item.Settings().maxDamage(3)));
    public static final Block PALLET_BLOCK = Registry.register(Registries.BLOCK,
        Identifier.of("hunt_generators", "pallet"), new Block(Block.Settings.copy(Blocks.OAK_PLANKS).strength(2.0f)));
    public static final Item PALLET_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "pallet"), new BlockItem(PALLET_BLOCK, new Item.Settings()));
    public static final Block TRAP_BLOCK = Registry.register(Registries.BLOCK,
        Identifier.of("hunt_generators", "trap"), new TrapBlock(Block.Settings.copy(Blocks.IRON_BLOCK).strength(1.0f).nonOpaque()));
    public static final Item TRAP_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "trap"), new BlockItem(TRAP_BLOCK, new Item.Settings()));
    public static final Item CLUB_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "club"), new Item(new Item.Settings().maxDamage(1)));
    public static final Item MACHETE_ITEM = Registry.register(Registries.ITEM,
        Identifier.of("hunt_generators", "machete"), new SwordItem(ToolMaterials.IRON,
            new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.IRON, 3, -2.4f))));
    private static final Map<UUID, String> ROLES = new HashMap<>();
    private static final Set<BlockPos> GENERATORS = new LinkedHashSet<>();
    private static final Set<BlockPos> REPAIRED = new HashSet<>();
    private static final Map<BlockPos, Integer> REPAIR_PROGRESS = new HashMap<>();
    private static final Map<UUID, BlockPos> REPAIRING = new HashMap<>();
    private static final Map<UUID, Integer> REPAIR_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> SKILL_CHECK_WINDOW = new HashMap<>();
    private static final Map<UUID, Integer> SKILL_CHECK_COOLDOWN = new HashMap<>();
    private static final Set<BlockPos> LOCKERS = new LinkedHashSet<>();
    private static final Map<UUID, BlockPos> HIDING = new HashMap<>();
    private static final Map<UUID, Integer> LIVES = new HashMap<>();
    private static final Map<UUID, Long> DASH_READY_AT = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> KILLER_ABILITY_READY = new HashMap<>();
    private static final Map<UUID, String> KILLER_SELECTED_ABILITY = new HashMap<>();
    private static final Map<UUID, Long> BLINDED_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> TOOLBOX_USE_TICKS = new HashMap<>();
    private static final Map<BlockPos, Integer> PALLET_BREAK_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> PALLET_BREAKING = new HashMap<>();
    private static final Map<BlockPos, Integer> TRAP_POSITIONS = new HashMap<>();
    private static final Map<UUID, Integer> TRAPPED_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> STUNNED_TICKS = new HashMap<>();
    private static final Map<UUID, BlockPos> TRAPPED_AT = new HashMap<>();
    private static final Map<UUID, Integer> TRAP_ESCAPE_CLICKS = new HashMap<>();
    private static final Map<UUID, Double> ORIGINAL_JUMP_STRENGTH = new HashMap<>();
    private static final Set<UUID> ELIMINATED = new HashSet<>();
    private static final long DASH_COOLDOWN_TICKS = 12000L;
    private static BlockPos gatePos = null;
    private static boolean running = false;
    private static boolean gateOpened = false;
    private static boolean escapeAnnounced = false;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GeneratorHudPayload.ID, GeneratorHudPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExitActionPayload.ID, ExitActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KillerAbilityPayload.ID, KillerAbilityPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KillerAbilityPayload.ID, (payload, context) ->
            context.player().getServer().execute(() -> {
                ServerPlayerEntity killer = context.player();
                UUID killerId = killer.getUuid();
                if (!running || !"killer".equals(ROLES.get(killerId))) return;
                String ability = payload.ability();
                String selectedAbility = KILLER_SELECTED_ABILITY.getOrDefault(killerId, "y");
                if (!selectedAbility.equals(ability)) {
                    killer.sendMessage(Text.literal("До раунда выбрана другая способность: " + selectedAbility.toUpperCase(Locale.ROOT)), true);
                    return;
                }
                long now = killer.getServerWorld().getTime();
                long cooldown = switch (ability) {
                    case "y" -> 900L;
                    case "u" -> 700L;
                    case "i" -> 800L;
                    default -> 0L;
                };
                if (cooldown == 0L) return;
                Map<String, Long> cooldowns = KILLER_ABILITY_READY.computeIfAbsent(killerId, key -> new HashMap<>());
                long readyAt = cooldowns.getOrDefault(ability, 0L);
                if (now < readyAt) {
                    killer.sendMessage(Text.literal("Способность перезаряжается: " + ((readyAt - now + 19) / 20) + " сек."), true);
                    return;
                }
                cooldowns.put(ability, now + cooldown);
                switch (ability) {
                    case "y" -> {
                        for (ServerPlayerEntity target : killer.getServerWorld().getPlayers()) {
                            if ("survivor".equals(ROLES.get(target.getUuid()))) {
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false, true));
                            }
                        }
                        killer.sendMessage(Text.literal("Кровавое чутьё! Все выжившие подсвечены на 5 секунд. Перезарядка: 45 сек."), true);
                        killer.getServerWorld().playSound(null, killer.getBlockPos(), SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY, SoundCategory.PLAYERS, 0.8f, 1.3f);
                    }
                    case "u" -> {
                        killer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1, false, true, true));
                        killer.sendMessage(Text.literal("Ярость! Скорость повышена на 5 секунд. Перезарядка: 35 сек."), true);
                        killer.getServerWorld().playSound(null, killer.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.8f, 1.0f);
                    }
                    case "i" -> {
                        for (ServerPlayerEntity target : killer.getServerWorld().getPlayers()) {
                            if ("survivor".equals(ROLES.get(target.getUuid())) && target.squaredDistanceTo(killer) <= 64.0) {
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, true, true));
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, true, true));
                                target.sendMessage(Text.literal("Ударная волна маньяка!"), true);
                            }
                        }
                        killer.sendMessage(Text.literal("Ударная волна! Выжившие в радиусе 8 блоков замедлены и ослеплены. Перезарядка: 40 сек."), true);
                        killer.getServerWorld().playSound(null, killer.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6f, 1.4f);
                    }
                }
            })
        );
        ServerPlayNetworking.registerGlobalReceiver(ExitActionPayload.ID, (payload, context) ->
            context.player().getServer().execute(() -> {
                ServerPlayerEntity player = context.player();
                UUID id = player.getUuid();
                if (!running || !"survivor".equals(ROLES.get(id))) return;

                BlockPos hidingSpot = HIDING.get(id);
                if (hidingSpot != null) {
                    HIDING.remove(id);
                    player.setInvisible(false);
                    player.teleport(player.getServerWorld(), hidingSpot.getX() + 0.5, hidingSpot.getY(),
                        hidingSpot.getZ() + 1.5, java.util.Set.of(), player.getYaw(), player.getPitch());
                    player.sendMessage(Text.literal("Ты вышел из шкафа."), true);
                    return;
                }

                BlockPos generator = REPAIRING.remove(id);
                if (generator != null) {
                    REPAIR_TICKS.remove(id);
                    SKILL_CHECK_WINDOW.remove(id);
                    TOOLBOX_USE_TICKS.remove(id);
                    player.sendMessage(Text.literal("Ты прекратил ремонт. Прогресс сохранён: "
                        + REPAIR_PROGRESS.getOrDefault(generator, 0) + "%."), true);
                }
            })
        );
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
                .then(literal("ability").then(argument("ability", StringArgumentType.word()).executes(ctx -> {
                    var source = ctx.getSource();
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    if (running) {
                        source.sendError(Text.literal("Способность можно выбрать только до начала раунда."));
                        return 0;
                    }
                    if (!"killer".equals(ROLES.get(player.getUuid()))) {
                        source.sendError(Text.literal("Сначала выбери роль командой /hunt join killer."));
                        return 0;
                    }
                    String ability = StringArgumentType.getString(ctx, "ability").toLowerCase(Locale.ROOT);
                    if (!Set.of("y", "u", "i").contains(ability)) {
                        source.sendError(Text.literal("Выбери: /hunt ability y, /hunt ability u или /hunt ability i"));
                        return 0;
                    }
                    KILLER_SELECTED_ABILITY.put(player.getUuid(), ability);
                    String description = switch (ability) {
                        case "y" -> "Кровавое чутьё — подсвечивает всех выживших на 5 сек. (перезарядка 45 сек.)";
                        case "u" -> "Ярость — ускорение на 5 сек. (перезарядка 35 сек.)";
                        default -> "Ударная волна — замедляет и ослепляет выживших рядом (перезарядка 40 сек.)";
                    };
                    source.sendFeedback(() -> Text.literal("Выбрана способность: " + description), false);
                    return 1;
                })))
                .then(literal("start").executes(ctx -> start(ctx.getSource().getServer(), ctx.getSource().getPlayerOrThrow())))
                .then(literal("dash").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    UUID id = player.getUuid();
                    if (!running || !"killer".equals(ROLES.get(id))) {
                        player.sendMessage(Text.literal("Рывок доступен только убийце во время раунда."), true);
                        return 0;
                    }
                    long now = player.getServerWorld().getTime();
                    long ready = DASH_READY_AT.getOrDefault(id, 0L);
                    if (now < ready) {
                        long seconds = (ready - now + 19) / 20;
                        player.sendMessage(Text.literal("Рывок перезаряжается: " + (seconds / 60) + " мин. " + (seconds % 60) + " сек."), true);
                        return 0;
                    }
                    var direction = player.getRotationVec(1.0F);
                    player.addVelocity(direction.x * 1.8, 0.25, direction.z * 1.8);
                    player.velocityModified = true;
                    DASH_READY_AT.put(id, now + DASH_COOLDOWN_TICKS);
                    player.sendMessage(Text.literal("Рывок использован! Перезарядка — 10 минут."), true);
                    return 1;
                }))
                .then(literal("test").then(argument("role", StringArgumentType.word()).executes(ctx -> {
                    var source = ctx.getSource();
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    String role = StringArgumentType.getString(ctx, "role").toLowerCase(Locale.ROOT);
                    if (!role.equals("killer") && !role.equals("survivor")) {
                        source.sendError(Text.literal("Используй: /hunt test killer или /hunt test survivor"));
                        return 0;
                    }

                    // Одиночный тестовый режим: создаём тренировочную арену вокруг игрока.
                    running = false;
                    for (ServerPlayerEntity online : source.getServer().getPlayerManager().getPlayerList()) {
                        Double originalJump = ORIGINAL_JUMP_STRENGTH.remove(online.getUuid());
                        if (originalJump != null) {
                            var jump = online.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
                            if (jump != null) jump.setBaseValue(originalJump);
                        }
                        online.setInvisible(false);
                    }
                    ROLES.clear();
                    GENERATORS.clear();
                    REPAIRED.clear();
                    REPAIR_PROGRESS.clear();
                    REPAIRING.clear();
                    REPAIR_TICKS.clear();
                    SKILL_CHECK_WINDOW.clear();
                    SKILL_CHECK_COOLDOWN.clear();
                    LOCKERS.clear();
                    HIDING.clear();
                    LIVES.clear();
                    ELIMINATED.clear();
                    BLINDED_UNTIL.clear();
                    gateOpened = false;
                    escapeAnnounced = false;

                    ServerWorld world = player.getServerWorld();
                    BlockPos base = player.getBlockPos();
                    int[][] generatorOffsets = {{3, 0}, {6, 0}, {9, 0}, {12, 0}, {15, 0}};
                    for (int[] offset : generatorOffsets) {
                        BlockPos pos = base.add(offset[0], 0, offset[1]).toImmutable();
                        world.setBlockState(pos, GENERATOR_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                        GENERATORS.add(pos);
                    }
                    gatePos = base.add(-3, 0, 0).toImmutable();
                    world.setBlockState(gatePos, Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                    BlockPos locker = base.add(0, 0, -2).toImmutable();
                    world.setBlockState(locker, LOCKER_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                    LOCKERS.add(locker);
                    BlockPos pallet = base.add(0, 0, 2).toImmutable();
                    world.setBlockState(pallet, PALLET_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                    BlockPos trap = base.add(2, 0, 2).toImmutable();
                    world.setBlockState(trap, TRAP_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                    TRAP_POSITIONS.put(trap, 0);

                    ROLES.put(player.getUuid(), role);
                    running = true;
                    if (role.equals("survivor")) LIVES.put(player.getUuid(), 2);
                    var jump = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
                    if (jump != null) {
                        ORIGINAL_JUMP_STRENGTH.put(player.getUuid(), jump.getBaseValue());
                        jump.setBaseValue(0.0);
                    }
                    var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                    if (speed != null) speed.setBaseValue(role.equals("killer") ? 0.11 : 0.1);

                    player.getInventory().insertStack(new ItemStack(TOOLBOX_ITEM, 1));
                    player.getInventory().insertStack(new ItemStack(MEDKIT_ITEM, 1));
                    player.getInventory().insertStack(new ItemStack(FLASHLIGHT_ITEM, 1));
                    player.getInventory().insertStack(new ItemStack(PALLET_ITEM, 1));
                    player.getInventory().insertStack(new ItemStack(TRAP_ITEM, 3));
                    player.getInventory().insertStack(new ItemStack(CLUB_ITEM, 1));
                    if (role.equals("killer")) player.getInventory().insertStack(new ItemStack(MACHETE_ITEM, 1));
                    player.getInventory().insertStack(new ItemStack(LOCKER_ITEM, 1));
                    player.getInventory().insertStack(new ItemStack(GENERATOR_ITEM, 1));
                    player.sendMessage(Text.literal("ТЕСТОВЫЙ РЕЖИМ: роль " + (role.equals("killer") ? "УБИЙЦА" : "ВЫЖИВШИЙ") + ". Арена и предметы созданы. Прыжок отключён."), false);
                    player.sendMessage(Text.literal("Проверь генераторы, шкаф, палету, предметы и /hunt status. Для смены роли снова выполни /hunt test killer или survivor."), false);
                    return 1;
                })))
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
                .then(literal("locker").then(literal("add").executes(ctx -> {
                    var source = ctx.getSource();
                    BlockPos pos = BlockPos.ofFloored(source.getPosition()).offset(source.getPlayerOrThrow().getHorizontalFacing()).toImmutable();
                    if (LOCKERS.size() >= 20) {
                        source.sendError(Text.literal("Максимум 20 шкафов."));
                        return 0;
                    }
                    source.getWorld().setBlockState(pos, LOCKER_BLOCK.getDefaultState());
                    LOCKERS.add(pos);
                    source.sendFeedback(() -> Text.literal("Шкаф для пряток установлен: " + pos.toShortString()), false);
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
                    BLINDED_UNTIL.clear();
                    ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal("Раунд сброшен. Конфигурация генераторов и ворот сохранена."), false);
                    return 1;
                }))
                .then(literal("resetconfig").executes(ctx -> {
                    running = false;
                    gateOpened = false;
                    escapeAnnounced = false;
                    REPAIRED.clear();
                    ROLES.clear();
                    BLINDED_UNTIL.clear();
                    GENERATORS.clear();
                    LOCKERS.clear(); HIDING.clear(); gatePos = null;
                    ctx.getSource().sendFeedback(() -> Text.literal("Настройки генераторов и ворот очищены."), false);
                    return 1;
                }))
            );
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (world.isClient || !(player instanceof ServerPlayerEntity user) || !running
                    || !"survivor".equals(ROLES.get(user.getUuid()))) return TypedActionResult.pass(stack);
            if (stack.isOf(MEDKIT_ITEM)) {
                if (user.getHealth() >= user.getMaxHealth()) {
                    user.sendMessage(Text.literal("Здоровье уже полное."), true);
                    return TypedActionResult.fail(stack);
                }
                user.heal(2.0F);
                stack.damage(1, user, hand == net.minecraft.util.Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                user.sendMessage(Text.literal("Аптечка восстановила одно здоровье."), true);
                return TypedActionResult.success(stack);
            }
            if (stack.isOf(FLASHLIGHT_ITEM)) {
                ServerPlayerEntity target = null;
                double best = 144.0;
                var look = user.getRotationVec(1.0F);
                for (var entity : world.getPlayers()) {
                    if (!(entity instanceof ServerPlayerEntity candidate)) continue;
                    if (candidate == user || !"killer".equals(ROLES.get(candidate.getUuid()))) continue;
                    var delta = candidate.getEyePos().subtract(user.getEyePos());
                    double distance = delta.length();
                    if (distance > 12.0 || distance < 0.1 || look.dotProduct(delta.normalize()) < 0.88) continue;
                    if (distance * distance < best) { best = distance * distance; target = candidate; }
                }
                if (target == null) {
                    user.sendMessage(Text.literal("Убийца не попал в луч фонарика."), true);
                    return TypedActionResult.fail(stack);
                }
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, true, true));
                BLINDED_UNTIL.put(target.getUuid(), target.getServerWorld().getTime() + 40L);
                stack.damage(1, user, hand == net.minecraft.util.Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                user.sendMessage(Text.literal("Ты ослепил убийцу на 2 секунды!"), true);
                target.sendMessage(Text.literal("Тебя ослепили на 2 секунды!"), true);
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        AttackEntityCallback.EVENT.register((attacker, world, hand, target, hitResult) -> {
            if (world.isClient || !running || !(attacker instanceof ServerPlayerEntity player)
                    || !(target instanceof ServerPlayerEntity other)) return ActionResult.PASS;
            String attackerRole = ROLES.get(player.getUuid());
            String targetRole = ROLES.get(other.getUuid());
            if ("survivor".equals(attackerRole) && "killer".equals(targetRole)
                    && player.getStackInHand(hand).isOf(CLUB_ITEM)) {
                if (STUNNED_TICKS.containsKey(other.getUuid())) {
                    player.sendMessage(Text.literal("Маньяк уже оглушён!"), true);
                    return ActionResult.SUCCESS;
                }
                STUNNED_TICKS.put(other.getUuid(), 60);
                ItemStack club = player.getStackInHand(hand);
                club.damage(1, player, hand == net.minecraft.util.Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                player.sendMessage(Text.literal("Ты оглушил маньяка на 3 секунды! Дубина сломалась."), true);
                other.sendMessage(Text.literal("Тебя оглушили на 3 секунды!"), true);
                other.getServerWorld().playSound(null, other.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 0.7f);
                return ActionResult.SUCCESS;
            }
            if (!"killer".equals(attackerRole) || !"survivor".equals(targetRole)
                    || ELIMINATED.contains(other.getUuid())) return ActionResult.PASS;
            int remaining = LIVES.getOrDefault(other.getUuid(), 2) - 1;
            LIVES.put(other.getUuid(), remaining);
            if (remaining <= 0) {
                ELIMINATED.add(other.getUuid());
                other.changeGameMode(GameMode.SPECTATOR);
                other.sendMessage(Text.literal("Ты потерял обе жизни и выбыл из раунда."), false);
                player.sendMessage(Text.literal("Выживший выбыл!"), true);
            } else {
                other.setHealth(other.getMaxHealth());
                other.setFireTicks(0);
                other.setInvulnerable(true);
                other.getServerWorld().getServer().execute(() -> other.setInvulnerable(false));
                other.sendMessage(Text.literal("Тебя поймали! Осталась 1 жизнь."), false);
                player.sendMessage(Text.literal("У выжившего осталась 1 жизнь."), true);
            }
            return ActionResult.SUCCESS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(world instanceof ServerWorld serverWorld) || !running) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos().toImmutable();
            UUID id = player.getUuid();
            if ("survivor".equals(ROLES.get(id)) && TRAPPED_TICKS.containsKey(id)) {
                player.sendMessage(Text.literal("Ты обездвижен! Подожди, пока закончится действие капкана."), true);
                return ActionResult.FAIL;
            }
            if ("killer".equals(ROLES.get(id)) && world.getBlockState(pos).isOf(TRAP_BLOCK)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                TRAP_POSITIONS.remove(pos);
                TRAPPED_AT.entrySet().removeIf(entry -> entry.getValue().equals(pos));
                if (!player.getInventory().insertStack(new ItemStack(TRAP_ITEM, 1))) player.dropItem(new ItemStack(TRAP_ITEM, 1), false);
                player.sendMessage(Text.literal("Ты подобрал капкан."), true);
                return ActionResult.SUCCESS;
            }
            if (LOCKERS.contains(pos)) {
                if ("killer".equals(ROLES.get(id))) {
                    boolean found = false;
                    for (Map.Entry<UUID, BlockPos> entry : new ArrayList<>(HIDING.entrySet())) {
                        if (!entry.getValue().equals(pos)) continue;
                        ServerPlayerEntity survivor = serverWorld.getServer().getPlayerManager().getPlayer(entry.getKey());
                        HIDING.remove(entry.getKey());
                        if (survivor != null) {
                            survivor.setInvisible(false);
                            survivor.teleport(serverWorld, pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.2, java.util.Set.of(), survivor.getYaw(), survivor.getPitch());
                            survivor.sendMessage(Text.literal("Убийца вытащил тебя из шкафа!"), false);
                        }
                        found = true;
                    }
                    player.sendMessage(Text.literal(found ? "Ты вытащил выжившего из шкафа!" : "В этом шкафу никого нет."), true);
                    return ActionResult.SUCCESS;
                }
                if (!"survivor".equals(ROLES.get(id))) {
                    player.sendMessage(Text.literal("Только выжившие могут прятаться в шкафах."), true);
                    return ActionResult.FAIL;
                }
                if (HIDING.containsKey(id)) {
                    player.sendMessage(Text.literal("Чтобы выйти из шкафа, нажми SHIFT."), true);
                } else {
                    HIDING.put(id, pos);
                    player.setInvisible(true);
                    player.teleport(serverWorld, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), player.getYaw(), player.getPitch());
                    player.sendMessage(Text.literal("Ты спрятался в шкафу. Нажми SHIFT, чтобы выйти."), true);
                }
                return ActionResult.SUCCESS;
            }
            if (world.getBlockState(pos).isOf(PALLET_BLOCK)) {
                String role = ROLES.get(id);
                if ("killer".equals(role)) {
                    if (PALLET_BREAK_TICKS.containsKey(pos)) {
                        player.sendMessage(Text.literal("Ты уже ломаешь эту палету..."), true);
                        return ActionResult.SUCCESS;
                    }
                    PALLET_BREAK_TICKS.put(pos, 40);
                    PALLET_BREAKING.put(id, 40);
                    player.sendMessage(Text.literal("Начал ломать палету. Это займёт 2 секунды!"), true);
                    return ActionResult.SUCCESS;
                }
                if (!"survivor".equals(role)) return ActionResult.FAIL;
                var facing = player.getHorizontalFacing();
                double x = pos.getX() + 0.5 + facing.getOffsetX() * 1.8;
                double z = pos.getZ() + 0.5 + facing.getOffsetZ() * 1.8;
                player.teleport(serverWorld, x, pos.getY(), z, java.util.Set.of(), player.getYaw(), player.getPitch());
                player.sendMessage(Text.literal("Ты перелез через доски!"), true);
                return ActionResult.SUCCESS;
            }
            if ("killer".equals(ROLES.get(id)) && player.getStackInHand(hand).isOf(TRAP_ITEM)) {
                BlockPos trapPos = pos.up();
                if (!world.getBlockState(trapPos).isAir()) {
                    player.sendMessage(Text.literal("Поставь капкан на свободный блок."), true);
                    return ActionResult.FAIL;
                }
                world.setBlockState(trapPos, TRAP_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                TRAP_POSITIONS.put(trapPos.toImmutable(), 0);
                player.getStackInHand(hand).decrement(1);
                player.sendMessage(Text.literal("Капкан установлен! Выживший, наступивший на него, будет пойман на 3 секунды."), true);
                return ActionResult.SUCCESS;
            }
            if (!GENERATORS.contains(pos)) return ActionResult.PASS;
            String role = ROLES.get(id);
            if ("killer".equals(role)) {
                if (REPAIRED.contains(pos)) {
                    player.sendMessage(Text.literal("Этот генератор уже починен — его нельзя сломать."), true);
                    return ActionResult.FAIL;
                }
                int damage = 5 + new Random().nextInt(6);
                int oldProgress = REPAIR_PROGRESS.getOrDefault(pos, 0);
                int newProgress = Math.max(0, oldProgress - damage);
                REPAIR_PROGRESS.put(pos, newProgress);
                player.sendMessage(Text.literal("Сломал генератор! Прогресс уменьшен на " + (oldProgress - newProgress) + "% (" + newProgress + "% осталось)."), true);
                return ActionResult.SUCCESS;
            }
            if (!"survivor".equals(role)) {
                player.sendMessage(Text.literal("Сначала начни матч командой /hunt start."), true);
                return ActionResult.FAIL;
            }
            if (REPAIRED.contains(pos)) {
                player.sendMessage(Text.literal("Этот генератор уже починен."), true);
                return ActionResult.FAIL;
            }
            if (pos.equals(REPAIRING.get(id))) {
                int skillTicks = SKILL_CHECK_WINDOW.getOrDefault(id, 0);
                if (skillTicks > 0) {
                    SKILL_CHECK_WINDOW.put(id, 0);
                    if (skillTicks >= 10 && skillTicks <= 15) {
                        REPAIR_PROGRESS.put(pos, Math.min(100, REPAIR_PROGRESS.getOrDefault(pos, 0) + 5));
                        player.sendMessage(Text.literal("ТОЧНО В ЦЕЛЬ! +5% прогресса!"), true);
                        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.9f, 1.8f);
                    } else {
                        REPAIR_PROGRESS.put(pos, Math.max(0, REPAIR_PROGRESS.getOrDefault(pos, 0) - 10));
                        player.sendMessage(Text.literal("Промах! Потеряно 10% прогресса."), true);
                        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.8f, 0.7f);
                    }
                } else {
                    player.sendMessage(Text.literal("Мини-игра ещё не активна — дождись сигнала!"), true);
                }
                return ActionResult.SUCCESS;
            }
            REPAIRING.put(id, pos);
            world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.7f, 1.2f);
            REPAIR_TICKS.put(id, 0);
            SKILL_CHECK_WINDOW.put(id, 0);
            player.sendMessage(Text.literal("Начата починка. Прогресс: " + REPAIR_PROGRESS.getOrDefault(pos, 0) + "%"), true);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    Double originalJumpStrength = ORIGINAL_JUMP_STRENGTH.remove(player.getUuid());
                    if (originalJumpStrength != null) {
                        var jump = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
                        if (jump != null) jump.setBaseValue(originalJumpStrength);
                    }
                }
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String role = ROLES.get(player.getUuid());
                if (role != null) {
                    ServerPlayNetworking.send(player, new RolePayload(role));
                    Long blindUntil = BLINDED_UNTIL.get(player.getUuid());
                    if (blindUntil != null && player.getServerWorld().getTime() >= blindUntil) {
                        player.removeStatusEffect(StatusEffects.BLINDNESS);
                        BLINDED_UNTIL.remove(player.getUuid());
                    }
                    var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                    if (speed != null) speed.setBaseValue("killer".equals(role) ? 0.11 : 0.1);
                    var jump = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
                    if (jump != null) {
                        ORIGINAL_JUMP_STRENGTH.putIfAbsent(player.getUuid(), jump.getBaseValue());
                        jump.setBaseValue(0.0);
                    }
                }
            }
            for (Map.Entry<BlockPos, Integer> trapEntry : new ArrayList<>(TRAP_POSITIONS.entrySet())) {
                BlockPos trapPos = trapEntry.getKey();
                for (ServerPlayerEntity survivor : server.getPlayerManager().getPlayerList()) {
                    UUID survivorId = survivor.getUuid();
                    if ("survivor".equals(ROLES.get(survivorId))
                            && !TRAPPED_TICKS.containsKey(survivorId)
                            && survivor.getBlockPos().isWithinDistance(trapPos, 1.25)) {
                        int remainingLives = LIVES.getOrDefault(survivorId, 2) - 1;
                        LIVES.put(survivorId, remainingLives);
                        if (remainingLives <= 0) {
                            ELIMINATED.add(survivorId);
                            survivor.changeGameMode(GameMode.SPECTATOR);
                            survivor.sendMessage(Text.literal("Капкан забрал последнюю жизнь. Ты выбыл из раунда."), false);
                        } else {
                            TRAPPED_TICKS.put(survivorId, 60);
                            TRAPPED_AT.put(survivorId, trapPos.toImmutable());
                            TRAP_ESCAPE_CLICKS.remove(survivorId);
                            survivor.sendMessage(Text.literal("Ты попался в капкан и потерял 1 жизнь! Капкан сломан. Ты обездвижен на 3 секунды."), false);
                        }
                        for (ServerPlayerEntity killer : server.getPlayerManager().getPlayerList()) {
                            if ("killer".equals(ROLES.get(killer.getUuid()))) killer.sendMessage(Text.literal("Выживший попался в твой капкан и потерял жизнь!"), true);
                        }
                        for (ServerWorld candidateWorld : server.getWorlds()) {
                            if (candidateWorld.getBlockState(trapPos).isOf(TRAP_BLOCK)) {
                                candidateWorld.setBlockState(trapPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                break;
                            }
                        }
                        TRAP_POSITIONS.remove(trapPos);
                        break;
                    }
                }
            }
            Iterator<Map.Entry<BlockPos, Integer>> palletIterator = PALLET_BREAK_TICKS.entrySet().iterator();
            while (palletIterator.hasNext()) {
                Map.Entry<BlockPos, Integer> entry = palletIterator.next();
                BlockPos palletPos = entry.getKey();
                ServerWorld palletWorld = null;
                for (ServerWorld candidateWorld : server.getWorlds()) {
                    if (candidateWorld.getBlockState(palletPos).isOf(PALLET_BLOCK)) {
                        palletWorld = candidateWorld;
                        break;
                    }
                }
                if (palletWorld == null) {
                    palletIterator.remove();
                    continue;
                }
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    palletWorld.setBlockState(palletPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    palletIterator.remove();
                    for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                        online.sendMessage(Text.literal("Палета сломана!"), true);
                    }
                } else {
                    entry.setValue(remaining);
                }
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID id = p.getUuid();
                BlockPos hidingSpot = HIDING.get(id);
                if (hidingSpot != null) {
                    if (!"survivor".equals(ROLES.get(id)) || !running) {
                        HIDING.remove(id);
                        p.setInvisible(false);
                    } else {
                        p.setInvisible(true);
                        p.teleport(p.getServerWorld(), hidingSpot.getX() + 0.5, hidingSpot.getY(), hidingSpot.getZ() + 0.5, java.util.Set.of(), p.getYaw(), p.getPitch());
                        continue;
                    }
                }
                Integer trapped = TRAPPED_TICKS.get(id);
                if (trapped != null) {
                    p.setVelocity(0, 0, 0);
                    p.velocityModified = true;
                    if (trapped <= 1) {
                        TRAPPED_TICKS.remove(id);
                        TRAPPED_AT.remove(id);
                        TRAP_ESCAPE_CLICKS.remove(id);
                        p.sendMessage(Text.literal("Ты освободился из капкана."), true);
                    } else TRAPPED_TICKS.put(id, trapped - 1);
                }
                Integer stunned = STUNNED_TICKS.get(id);
                if (stunned != null) {
                    p.setVelocity(0, 0, 0);
                    p.velocityModified = true;
                    if (stunned <= 1) {
                        STUNNED_TICKS.remove(id);
                        p.sendMessage(Text.literal("Оглушение закончилось."), true);
                    } else STUNNED_TICKS.put(id, stunned - 1);
                }
                Integer breaking = PALLET_BREAKING.get(id);
                if (breaking != null) {
                    p.setVelocity(0, 0, 0);
                    p.velocityModified = true;
                    if (breaking <= 1) PALLET_BREAKING.remove(id);
                    else PALLET_BREAKING.put(id, breaking - 1);
                }
                BlockPos target = REPAIRING.get(id);
                if (target == null) {
                    ServerPlayNetworking.send(p, new GeneratorHudPayload(0, 0, false));
                    continue;
                }
                ServerPlayNetworking.send(p, new GeneratorHudPayload(REPAIR_PROGRESS.getOrDefault(target, 0), SKILL_CHECK_WINDOW.getOrDefault(id, 0), true));
                if (!"survivor".equals(ROLES.get(id)) || p.squaredDistanceTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 25) {
                    REPAIRING.remove(id);
                    REPAIR_TICKS.remove(id);
                    SKILL_CHECK_WINDOW.remove(id);
                    SKILL_CHECK_COOLDOWN.remove(id);
                    p.sendMessage(Text.literal("Починка прервана."), true);
                    continue;
                }
                p.setVelocity(0, 0, 0);
                p.velocityModified = true;
                int window = SKILL_CHECK_WINDOW.getOrDefault(id, 0);
                if (window <= 0) {
                    int cooldown = SKILL_CHECK_COOLDOWN.getOrDefault(id, 100 + new Random().nextInt(201));
                    cooldown--;
                    if (cooldown <= 0) {
                        SKILL_CHECK_WINDOW.put(id, 40);
                        SKILL_CHECK_COOLDOWN.put(id, 100 + new Random().nextInt(201));
                        p.sendMessage(Text.literal("МИНИ-ИГРА! Нажми ПКМ по генератору в течение 2 секунд!"), true);
                    } else {
                        SKILL_CHECK_COOLDOWN.put(id, cooldown);
                    }
                }
                if (window > 0) {
                    SKILL_CHECK_WINDOW.put(id, window - 1);
                    if (window == 1) {
                        REPAIR_PROGRESS.put(target, Math.max(0, REPAIR_PROGRESS.getOrDefault(target, 0) - 10));
                        p.sendMessage(Text.literal("Не успел! Потеряно 10% прогресса."), true);
                    }
                }
                int ticks = REPAIR_TICKS.getOrDefault(id, 0) + 1;
                REPAIR_TICKS.put(id, ticks);
                boolean hasToolbox = p.getMainHandStack().isOf(TOOLBOX_ITEM) || p.getOffHandStack().isOf(TOOLBOX_ITEM);
                if (hasToolbox) {
                    int usedTicks = TOOLBOX_USE_TICKS.getOrDefault(id, 0) + 1;
                    TOOLBOX_USE_TICKS.put(id, usedTicks);
                    if (usedTicks >= 200) {
                        if (p.getMainHandStack().isOf(TOOLBOX_ITEM)) p.getMainHandStack().decrement(1);
                        else p.getOffHandStack().decrement(1);
                        TOOLBOX_USE_TICKS.remove(id);
                        p.sendMessage(Text.literal("Ящик с инструментами сломался после 10 секунд работы!"), true);
                        hasToolbox = false;
                    }
                } else {
                    TOOLBOX_USE_TICKS.remove(id);
                }
                int threshold = hasToolbox ? 10 : 20;
                if (ticks >= threshold) {
                    REPAIR_TICKS.put(id, 0);
                    int progress = Math.min(100, REPAIR_PROGRESS.getOrDefault(target, 0) + (hasToolbox ? 2 : 1));
                    if (hasToolbox) {
                        var toolbox = p.getMainHandStack().isOf(TOOLBOX_ITEM) ? p.getMainHandStack() : p.getOffHandStack();
                        toolbox.damage(1, p, p.getMainHandStack().isOf(TOOLBOX_ITEM) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }
                    REPAIR_PROGRESS.put(target, progress);
                    p.getServerWorld().playSound(null, target, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.45f, hasToolbox ? 1.6f : 1.2f);
                    p.sendMessage(Text.literal("Прогресс генератора: " + progress + "%"), true);
                    if (progress >= 100) {
                        p.getServerWorld().playSound(null, target, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.9f, 1.0f);
                        REPAIRED.add(target);
                        SKILL_CHECK_COOLDOWN.remove(id);
                        // Generator completion reveals the generator to the killer for 4 seconds.
                        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                            if ("killer".equals(ROLES.get(online.getUuid()))) {
                                online.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0, false, false, true));
                                online.sendMessage(Text.literal("Генератор починен: " + target.getX() + ", " + target.getY() + ", " + target.getZ()), true);
                            } else if ("survivor".equals(ROLES.get(online.getUuid()))
                                    && online.squaredDistanceTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 25.0) {
                                online.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false, true));
                            }
                        }
                        REPAIRING.remove(id);
                        REPAIR_TICKS.remove(id);
                        SKILL_CHECK_WINDOW.remove(id);
                        server.getPlayerManager().broadcast(Text.literal("Генератор починен! Прогресс матча: " + REPAIRED.size() + "/5"), false);
                        if (REPAIRED.size() == 5) {
                            gateOpened = true;
                            if (gatePos != null) server.getOverworld().setBlockState(gatePos, Blocks.AIR.getDefaultState());
                            server.getPlayerManager().broadcast(Text.literal("Все генераторы починены! Ворота открыты — выжившие, бегите!"), false);
                        }
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
        boolean hasChosenKiller = players.stream().anyMatch(p -> "killer".equals(ROLES.get(p.getUuid())));
        if (hasChosenKiller) {
            long killerCount = players.stream().filter(p -> "killer".equals(ROLES.get(p.getUuid()))).count();
            if (killerCount != 1) {
                commandPlayer.sendMessage(Text.literal("Перед стартом выбери ровно одного убийцу: /hunt join killer. Остальные игроки должны выбрать /hunt join survivor."), false);
                return 0;
            }
            for (ServerPlayerEntity p : players) {
                if (!"killer".equals(ROLES.get(p.getUuid())) && !"survivor".equals(ROLES.get(p.getUuid()))) {
                    commandPlayer.sendMessage(Text.literal("Все игроки должны выбрать роль командой /hunt join killer или /hunt join survivor."), false);
                    return 0;
                }
            }
        } else {
            Collections.shuffle(players);
            ROLES.clear();
            ROLES.put(players.get(0).getUuid(), "killer");
            for (int i = 1; i < players.size(); i++) ROLES.put(players.get(i).getUuid(), "survivor");
        }
        REPAIRED.clear();
        REPAIR_PROGRESS.clear(); REPAIRING.clear(); REPAIR_TICKS.clear(); SKILL_CHECK_WINDOW.clear(); SKILL_CHECK_COOLDOWN.clear();
        gateOpened = false;
        escapeAnnounced = false;
        running = true;
        for (ServerPlayerEntity p : players) {
            String role = ROLES.get(p.getUuid());
            if ("survivor".equals(role)) LIVES.put(p.getUuid(), 2);
            else if ("killer".equals(role)) {
                if (!p.getInventory().insertStack(new ItemStack(MACHETE_ITEM, 1))) p.dropItem(new ItemStack(MACHETE_ITEM, 1), false);
            }
            ServerPlayNetworking.send(p, new RolePayload(role));
            var speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue("killer".equals(role) ? 0.11 : 0.1);
            var jump = p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
            if (jump != null) {
                ORIGINAL_JUMP_STRENGTH.putIfAbsent(p.getUuid(), jump.getBaseValue());
                jump.setBaseValue(0.0);
            }
        }
        server.getPlayerManager().broadcast(Text.literal("Раунд начался! Убийца: " + players.get(0).getName().getString() + ". Выжившие: " + (players.size() - 1) + ". Почините 5 генераторов и доберитесь до ворот!"), false);
        for (ServerPlayerEntity p : players) {
            p.sendMessage(Text.literal("Твоя роль: " + (ROLES.get(p.getUuid()).equals("killer") ? "УБИЙЦА" : "ВЫЖИВШИЙ")), false);
        }
        return 1;
    }
}

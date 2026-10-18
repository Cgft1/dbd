package ru.cgf.hunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/** Generated lobby and a compact horror map for Hunt matches. */
public final class LobbyFeature implements ModInitializer {
    private static BlockPos lobbyCenter;
    private static BlockPos dayPad;
    private static BlockPos nightPad;
    private static BlockPos mapPad;
    private static BlockPos rolePad;
    private static BlockPos mapCenter;

    public LobbyFeature() {}

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("hunt")
                .then(literal("lobby")
                    .then(literal("create").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        if (HuntMod.isMatchRunning()) {
                            player.sendMessage(Text.literal("Нельзя перестроить лобби во время матча."), false);
                            return 0;
                        }

                        ServerWorld world = player.getServerWorld();
                        lobbyCenter = player.getBlockPos().toImmutable();
                        mapCenter = lobbyCenter.add(150, 0, 0).toImmutable();

                        buildLobby(world, lobbyCenter);
                        HuntMod.prepareGeneratedMap(world, mapCenter);
                        MapConfig config = buildMap(world, mapCenter);
                        applyMapConfig(config);

                        player.teleport(world, lobbyCenter.getX() + 0.5, lobbyCenter.getY() + 2,
                            lobbyCenter.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
                        player.sendMessage(Text.literal("Лобби и карта «Заброшенный лагерь» созданы."), false);
                        player.sendMessage(Text.literal("Жёлтая площадка — ДЕНЬ, синяя — НОЧЬ, аметистовая — СЛУЧАЙНАЯ РОЛЬ, изумрудная — ТЕЛЕПОРТ НА КАРТУ."), false);
                        player.sendMessage(Text.literal("После выбора ролей и перехода на карту запусти /hunt start."), false);
                        return 1;
                    })))
            )
        );

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos().toImmutable();

            if (dayPad != null && clicked.equals(dayPad)) {
                serverPlayer.getServerWorld().setTimeOfDay(1000L);
                serverPlayer.getServer().getPlayerManager().broadcast(Text.literal("В лобби выбран ДЕНЬ."), false);
                return ActionResult.SUCCESS;
            }
            if (nightPad != null && clicked.equals(nightPad)) {
                serverPlayer.getServerWorld().setTimeOfDay(18000L);
                serverPlayer.getServer().getPlayerManager().broadcast(Text.literal("В лобби выбрана НОЧЬ."), false);
                return ActionResult.SUCCESS;
            }
            if (rolePad != null && clicked.equals(rolePad)) {
                HuntMod.assignRandomLobbyRole(serverPlayer);
                return ActionResult.SUCCESS;
            }
            if (mapPad != null && clicked.equals(mapPad)) {
                BlockPos spawn = HuntMod.getGeneratedMapSpawn();
                if (spawn == null) {
                    serverPlayer.sendMessage(Text.literal("Карта ещё не создана. Используй /hunt lobby create."), true);
                    return ActionResult.FAIL;
                }
                serverPlayer.teleport(serverPlayer.getServerWorld(), spawn.getX() + 0.5, spawn.getY(),
                    spawn.getZ() + 0.5, Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch());
                serverPlayer.sendMessage(Text.literal("Ты на карте «Заброшенный лагерь». Дождись остальных и запусти /hunt start."), false);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    private static void buildLobby(ServerWorld world, BlockPos c) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = 0; y <= 7; y++) {
                    world.setBlockState(c.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
                world.setBlockState(c.add(x, -1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
                if (Math.abs(x) == 10 || Math.abs(z) == 10) {
                    world.setBlockState(c.add(x, 0, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(c.add(x, 1, z), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_ALL);
                    if ((Math.abs(x) + Math.abs(z)) % 4 == 0) {
                        world.setBlockState(c.add(x, 2, z), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
                    }
                } else {
                    world.setBlockState(c.add(x, 0, z), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        for (int x = -5; x <= 5; x++) {
            world.setBlockState(c.add(x, 5, -5), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(c.add(x, 5, 5), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
        }
        for (int z = -4; z <= 4; z++) {
            world.setBlockState(c.add(-5, 5, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(c.add(5, 5, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
        }

        world.setBlockState(c.add(0, 1, 0), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);

        dayPad = c.add(-5, 1, 0).toImmutable();
        nightPad = c.add(5, 1, 0).toImmutable();
        mapPad = c.add(0, 1, -5).toImmutable();
        rolePad = c.add(0, 1, 5).toImmutable();

        world.setBlockState(dayPad, Blocks.YELLOW_CONCRETE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(nightPad, Blocks.BLUE_CONCRETE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(mapPad, Blocks.EMERALD_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(rolePad, Blocks.AMETHYST_BLOCK.getDefaultState(), Block.NOTIFY_ALL);

        world.setBlockState(c.add(-5, 2, 0), Blocks.LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(5, 2, 0), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(0, 2, -5), Blocks.END_ROD.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(0, 2, 5), Blocks.REDSTONE_LAMP.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static MapConfig buildMap(ServerWorld world, BlockPos c) {
        int radius = 40;

        // Clear the play area and create a flat, dark forest floor.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= 8; y++) {
                    world.setBlockState(c.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }

                Block ground = ((x * 31 + z * 17) & 7) == 0 ? Blocks.PODZOL
                    : (((x * 13 - z * 19) & 15) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);
                world.setBlockState(c.add(x, -1, z), ground.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        // Solid border. The southern wall contains the escape gate.
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlockState(c.add(x, y, -radius), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                if (x < -2 || x > 1) {
                    world.setBlockState(c.add(x, y, radius), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        for (int z = -radius + 1; z < radius; z++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlockState(c.add(-radius, y, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(c.add(radius, y, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        // Main dirt paths make the layout readable even at night.
        for (int n = -34; n <= 34; n++) {
            for (int w = -2; w <= 2; w++) {
                world.setBlockState(c.add(w, -1, n), Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(c.add(n, -1, w), Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        buildCabin(world, c.add(0, 0, 2));
        buildRuin(world, c.add(-25, 0, 18));
        buildRuin(world, c.add(24, 0, -20));

        // Trees around the outer ring and between landmarks.
        int[][] trees = {
            {-33,-31},{-27,-33},{-18,-31},{18,-32},{27,-33},{33,-28},
            {-34,-18},{-31,-8},{-33,10},{-34,27},{-27,32},{-15,30},
            {15,31},{26,33},{33,25},{31,13},{34,1},{31,-12},
            {-18,-18},{-13,-25},{13,-24},{20,-13},{-22,7},{-16,14},
            {17,12},{23,7},{-10,25},{8,26}
        };
        for (int[] t : trees) buildTree(world, c.add(t[0], 0, t[1]));

        // Low ruined walls create chase loops without fully blocking vision.
        int[][] wallStarts = {
            {-15,-12,7,0},{-12,-12,0,7},{10,-12,8,0},{18,-12,0,7},
            {-16,10,8,0},{-16,10,0,7},{9,12,9,0},{18,6,0,7},
            {-30,-4,7,0},{24,4,7,0}
        };
        for (int[] w : wallStarts) {
            for (int i = 0; i < w[2] + w[3]; i++) {
                int dx = w[2] == 0 ? 0 : i;
                int dz = w[3] == 0 ? 0 : i;
                Block block = (i % 3 == 0) ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
                world.setBlockState(c.add(w[0] + dx, 0, w[1] + dz), block.getDefaultState(), Block.NOTIFY_ALL);
                if (i % 2 == 0) world.setBlockState(c.add(w[0] + dx, 1, w[1] + dz), block.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        Set<BlockPos> generators = new LinkedHashSet<>();
        int[][] generatorOffsets = {
            {-28,-24},{27,-23},{-27,25},{26,24},{0,2}
        };
        for (int[] offset : generatorOffsets) {
            BlockPos pos = c.add(offset[0], 0, offset[1]).toImmutable();
            world.setBlockState(pos, HuntMod.GENERATOR_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(pos.down(), Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            generators.add(pos);
        }

        Set<BlockPos> lockers = new LinkedHashSet<>();
        int[][] lockerOffsets = {
            {-7,-3},{7,-3},{-7,6},{7,6},{-29,17},{-21,19},{20,-20},{27,-18}
        };
        for (int[] offset : lockerOffsets) {
            BlockPos pos = c.add(offset[0], 0, offset[1]).toImmutable();
            world.setBlockState(pos, HuntMod.LOCKER_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(pos.up(), HuntMod.LOCKER_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            lockers.add(pos);
        }

        int[][] palletOffsets = {
            {-14,-8},{14,-8},{-14,8},{14,8},{-27,-9},{27,10},{-8,27},{9,-27}
        };
        for (int[] offset : palletOffsets) {
            world.setBlockState(c.add(offset[0], 0, offset[1]), HuntMod.PALLET_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        }

        BlockPos gate = c.add(-2, 0, radius).toImmutable();
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                world.setBlockState(gate.add(dx, dy, 0), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        // Start area near the north side, with lamps so the teleport point is easy to find.
        BlockPos spawn = c.add(0, 0, -32).toImmutable();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                world.setBlockState(spawn.add(x, -1, z), Blocks.POLISHED_ANDESITE.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        world.setBlockState(spawn.add(-3, 0, -3), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(spawn.add(3, 0, -3), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);

        return new MapConfig(generators, lockers, gate, spawn);
    }

    private static void buildCabin(ServerWorld world, BlockPos c) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -6; z <= 6; z++) {
                world.setBlockState(c.add(x, -1, z), Blocks.SPRUCE_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        for (int x = -8; x <= 8; x++) {
            for (int y = 0; y <= 3; y++) {
                if (Math.abs(x) <= 1) {
                    world.setBlockState(c.add(x, y, -6), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                } else {
                    world.setBlockState(c.add(x, y, -6), Blocks.DARK_OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
                }
                world.setBlockState(c.add(x, y, 6), Blocks.DARK_OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        for (int z = -5; z <= 5; z++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlockState(c.add(-8, y, z), Blocks.DARK_OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
                if (Math.abs(z) > 1) {
                    world.setBlockState(c.add(8, y, z), Blocks.DARK_OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        for (int x = -8; x <= 8; x++) {
            for (int z = -6; z <= 6; z++) {
                if ((Math.abs(x) + Math.abs(z)) % 4 != 0) {
                    world.setBlockState(c.add(x, 4, z), Blocks.DARK_OAK_SLAB.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        world.setBlockState(c.add(-6, 2, -5), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(6, 2, 5), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(0, 3, 0), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static void buildRuin(ServerWorld world, BlockPos c) {
        for (int x = -4; x <= 4; x++) {
            world.setBlockState(c.add(x, 0, -4), Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
            if (x != 0) world.setBlockState(c.add(x, 1, -4), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
        }
        for (int z = -3; z <= 4; z++) {
            world.setBlockState(c.add(-4, 0, z), Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(c.add(4, 0, z), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
            if ((z & 1) == 0) world.setBlockState(c.add(-4, 1, z), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
        }
        world.setBlockState(c.add(-3, 0, 3), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(3, 0, 2), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static void buildTree(ServerWorld world, BlockPos base) {
        int height = 4 + Math.floorMod(base.getX() + base.getZ(), 3);
        for (int y = 0; y < height; y++) {
            world.setBlockState(base.up(y), Blocks.DARK_OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
        }
        BlockPos crown = base.up(height - 1);
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(z) + Math.max(0, y) <= 4) {
                        BlockPos leaf = crown.add(x, y, z);
                        if (world.getBlockState(leaf).isAir()) {
                            world.setBlockState(leaf, Blocks.DARK_OAK_LEAVES.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
                }
            }
        }
    }

    /**
     * HuntMod keeps match configuration private, so this feature installs the generated
     * positions after building the map. Keeping this in one method makes the generated
     * map compatible with the existing generator/gate game logic without duplicating it.
     */
    @SuppressWarnings("unchecked")
    private static void applyMapConfig(MapConfig config) {
        try {
            Field generatorsField = HuntMod.class.getDeclaredField("GENERATORS");
            generatorsField.setAccessible(true);
            Set<BlockPos> generators = (Set<BlockPos>) generatorsField.get(null);
            generators.clear();
            generators.addAll(config.generators());

            Field lockersField = HuntMod.class.getDeclaredField("LOCKERS");
            lockersField.setAccessible(true);
            Set<BlockPos> lockers = (Set<BlockPos>) lockersField.get(null);
            lockers.clear();
            lockers.addAll(config.lockers());

            Field gateField = HuntMod.class.getDeclaredField("gatePos");
            gateField.setAccessible(true);
            gateField.set(null, config.gate());

            Field spawnField = HuntMod.class.getDeclaredField("generatedMapSpawn");
            spawnField.setAccessible(true);
            spawnField.set(null, config.spawn());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось подключить сгенерированную карту к HuntMod", e);
        }
    }

    private record MapConfig(Set<BlockPos> generators, Set<BlockPos> lockers, BlockPos gate, BlockPos spawn) {}
}

package ru.cgf.hunt;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/** Small generated lobby with clickable pads and a generated horror map. */
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
                        lobbyCenter = player.getBlockPos().add(0, 0, 0).toImmutable();
                        mapCenter = lobbyCenter.add(150, 0, 0).toImmutable();
                        buildLobby(world, lobbyCenter);
                        HuntMod.prepareGeneratedMap(world, mapCenter);
                        player.teleport(world, lobbyCenter.getX() + 0.5, lobbyCenter.getY() + 2,
                            lobbyCenter.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
                        player.sendMessage(Text.literal("Лобби и заброшенная больница созданы! Настрой день/ночь, затем нажми «На карту» и «Случайная роль»."), false);
                        return 1;
                    })))
            )
        );

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos().toImmutable();
            if (dayPad != null && clicked.equals(dayPad)) {
                world.getServer().getOverworld().setTimeOfDay(6000);
                serverPlayer.sendMessage(Text.literal("Выбран день."), true);
                return ActionResult.SUCCESS;
            }
            if (nightPad != null && clicked.equals(nightPad)) {
                world.getServer().getOverworld().setTimeOfDay(18000);
                serverPlayer.sendMessage(Text.literal("Выбрана ночь."), true);
                return ActionResult.SUCCESS;
            }
            if (rolePad != null && clicked.equals(rolePad)) {
                HuntMod.assignRandomLobbyRole(serverPlayer);
                return ActionResult.SUCCESS;
            }
            if (mapPad != null && clicked.equals(mapPad)) {
                if (mapCenter == null) return ActionResult.PASS;
                if (HuntMod.assignRandomLobbyRole(serverPlayer)) {
                    BlockPos spawn = HuntMod.getGeneratedMapSpawn();
                    if (spawn != null) {
                        serverPlayer.teleport(serverPlayer.getServerWorld(), spawn.getX() + 0.5, spawn.getY(),
                            spawn.getZ() + 0.5, Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch());
                        serverPlayer.sendMessage(Text.literal("Ты на карте «Заброшенная больница». Дождись остальных и запусти /hunt start."), false);
                    }
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    private static void buildLobby(ServerWorld world, BlockPos c) {
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            for (int y = 0; y <= 6; y++) world.setBlockState(c.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(c.add(x, -1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
            if (Math.abs(x) == 9 || Math.abs(z) == 9) {
                world.setBlockState(c.add(x, 0, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(c.add(x, 1, z), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_ALL);
                if ((x + z) % 3 == 0) world.setBlockState(c.add(x, 2, z), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
            } else {
                world.setBlockState(c.add(x, 0, z), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        for (int x = -4; x <= 4; x++) {
            world.setBlockState(c.add(x, 5, -4), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(c.add(x, 5, 4), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
        }
        world.setBlockState(c.add(0, 1, 0), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        dayPad = c.add(-5, 1, 0).toImmutable();
        nightPad = c.add(5, 1, 0).toImmutable();
        mapPad = c.add(0, 1, -5).toImmutable();
        rolePad = c.add(0, 1, 5).toImmutable();
        world.setBlockState(dayPad, Blocks.YELLOW_GLAZED_TERRACOTTA.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(nightPad, Blocks.BLUE_GLAZED_TERRACOTTA.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(mapPad, Blocks.EMERALD_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(rolePad, Blocks.AMETHYST_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(-5, 2, 0), Blocks.LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(5, 2, 0), Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(0, 2, -5), Blocks.END_ROD.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(c.add(0, 2, 5), Blocks.REDSTONE_LAMP.getDefaultState(), Block.NOTIFY_ALL);
    }
}

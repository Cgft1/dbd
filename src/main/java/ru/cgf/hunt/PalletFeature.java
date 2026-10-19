package ru.cgf.hunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DBD-style pallet flow:
 * survivor drops a standing pallet first, then can vault it;
 * killer can break only a dropped pallet and it takes two seconds.
 */
public final class PalletFeature implements ModInitializer {
    public static final Block DROPPED_PALLET_BLOCK = Registry.register(
        Registries.BLOCK,
        Identifier.of("hunt_generators", "dropped_pallet"),
        new DroppedPalletBlock(Block.Settings.copy(Blocks.OAK_PLANKS).strength(2.0f).nonOpaque())
    );

    private static final Map<UUID, BlockPos> BREAKING = new HashMap<>();
    private static final Map<UUID, Integer> BREAK_TICKS = new HashMap<>();

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !HuntMod.isMatchRunning() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            BlockPos pos = hit.getBlockPos().toImmutable();
            BlockState state = world.getBlockState(pos);
            boolean standing = state.isOf(HuntMod.PALLET_BLOCK);
            boolean dropped = state.isOf(DROPPED_PALLET_BLOCK);
            if (!standing && !dropped) return ActionResult.PASS;

            boolean killer = isKiller(serverPlayer);

            if (standing) {
                if (killer) {
                    serverPlayer.sendMessage(Text.literal("Палета ещё стоит. Сломать её можно только после того, как выживший её сбросит."), true);
                    return ActionResult.SUCCESS;
                }

                world.setBlockState(pos, DROPPED_PALLET_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0f, 0.75f);
                serverPlayer.sendMessage(Text.literal("Палета сброшена! Теперь через неё можно перелезть."), true);
                return ActionResult.SUCCESS;
            }

            if (killer) {
                UUID id = serverPlayer.getUuid();
                if (BREAKING.containsKey(id)) {
                    serverPlayer.sendMessage(Text.literal("Ты уже ломаешь палету."), true);
                    return ActionResult.SUCCESS;
                }
                BREAKING.put(id, pos);
                BREAK_TICKS.put(id, 40);
                serverPlayer.sendMessage(Text.literal("Ломаешь сброшенную палету... 2 секунды."), true);
                return ActionResult.SUCCESS;
            }

            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            double dx = serverPlayer.getX() - centerX;
            double dz = serverPlayer.getZ() - centerZ;
            double targetX = centerX;
            double targetZ = centerZ;

            if (Math.abs(dx) > Math.abs(dz)) {
                targetX = centerX + (dx >= 0 ? -1.55 : 1.55);
            } else {
                targetZ = centerZ + (dz >= 0 ? -1.55 : 1.55);
            }

            serverPlayer.teleport(serverPlayer.getServerWorld(), targetX, pos.getY() + 0.1, targetZ,
                Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch());
            serverPlayer.getServerWorld().playSound(null, pos, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.55f, 1.4f);
            serverPlayer.sendMessage(Text.literal("Ты перелез через сброшенную палету."), true);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID id : new ArrayList<>(BREAKING.keySet())) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                BlockPos pos = BREAKING.get(id);
                if (player == null || pos == null || !isKiller(player)
                        || !player.getServerWorld().getBlockState(pos).isOf(DROPPED_PALLET_BLOCK)
                        || player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12.25) {
                    clearBreak(id);
                    continue;
                }

                player.setVelocity(0, 0, 0);
                player.velocityModified = true;
                int ticks = BREAK_TICKS.getOrDefault(id, 1) - 1;
                if (ticks <= 0) {
                    ServerWorld world = player.getServerWorld();
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    world.playSound(null, pos, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
                    player.sendMessage(Text.literal("Сброшенная палета сломана."), true);
                    clearBreak(id);
                } else {
                    BREAK_TICKS.put(id, ticks);
                }
            }
        });
    }

    private static boolean isKiller(ServerPlayerEntity player) {
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.WITHER_SKELETON_SKULL)) return true;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(HuntMod.MACHETE_ITEM)) return true;
        }
        return false;
    }

    private static void clearBreak(UUID id) {
        BREAKING.remove(id);
        BREAK_TICKS.remove(id);
    }

    private static final class DroppedPalletBlock extends Block {
        private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 4, 16);

        private DroppedPalletBlock(Settings settings) {
            super(settings);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
        }
    }
}

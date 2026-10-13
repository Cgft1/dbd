package ru.cgf.hunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PortalGunFeature implements ModInitializer {
    public static final Item PORTAL_GUN = Registry.register(Registries.ITEM,
            Identifier.of("hunt_generators", "portal_gun"),
            new Item(new Item.Settings().maxCount(1)));
    public static final Block BLUE_PORTAL = Registry.register(Registries.BLOCK,
            Identifier.of("hunt_generators", "blue_portal"),
            new Block(Block.Settings.copy(Blocks.BLUE_STAINED_GLASS).noCollision().nonOpaque().strength(-1.0f)));
    public static final Block YELLOW_PORTAL = Registry.register(Registries.BLOCK,
            Identifier.of("hunt_generators", "yellow_portal"),
            new Block(Block.Settings.copy(Blocks.YELLOW_STAINED_GLASS).noCollision().nonOpaque().strength(-1.0f)));

    private static final Map<String, BlockPos> PORTALS = new HashMap<>();
    // Игрок не телепортируется обратно, пока не отойдёт от портала.
    private static final java.util.Set<UUID> PORTAL_LOCK = new java.util.HashSet<>();

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(PORTAL_GUN)) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            BlockPos target = hit.getBlockPos().offset(hit.getSide());
            if (!world.getBlockState(target).isAir()) {
                player.sendMessage(Text.literal("Для портала нужно свободное место."), true);
                return ActionResult.FAIL;
            }
            String key = player.getUuidAsString() + ":" + world.getRegistryKey().getValue();
            BlockPos oldBlue = PORTALS.get(key + ":blue");
            BlockPos oldYellow = PORTALS.get(key + ":yellow");
            boolean placeBlue = oldBlue == null || oldYellow != null;
            if (placeBlue) {
                if (oldBlue != null) world.setBlockState(oldBlue, Blocks.AIR.getDefaultState());
                if (oldYellow != null) world.setBlockState(oldYellow, Blocks.AIR.getDefaultState());
                PORTALS.put(key + ":blue", target.toImmutable());
                PORTALS.remove(key + ":yellow");
                world.setBlockState(target, BLUE_PORTAL.getDefaultState());
                player.sendMessage(Text.literal("Синий портал открыт. Выстрели ещё раз, чтобы открыть жёлтый."), true);
            } else {
                PORTALS.put(key + ":yellow", target.toImmutable());
                world.setBlockState(target, YELLOW_PORTAL.getDefaultState());
                player.sendMessage(Text.literal("Пара порталов соединена! Проходи через любой."), true);
            }
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String key = player.getUuidAsString() + ":" + player.getWorld().getRegistryKey().getValue();
                BlockPos blue = PORTALS.get(key + ":blue");
                BlockPos yellow = PORTALS.get(key + ":yellow");
                if (blue == null || yellow == null || !(player.getWorld() instanceof ServerWorld world)) {
                    PORTAL_LOCK.remove(player.getUuid());
                    continue;
                }

                BlockPos feet = player.getBlockPos();
                boolean atBlue = nearPortal(feet, blue);
                boolean atYellow = nearPortal(feet, yellow);

                // После переноса ждём, пока игрок действительно выйдет из зоны портала.
                if (PORTAL_LOCK.contains(player.getUuid())) {
                    if (!atBlue && !atYellow) PORTAL_LOCK.remove(player.getUuid());
                    continue;
                }

                BlockPos destination = atBlue ? yellow : (atYellow ? blue : null);
                if (destination != null) {
                    // Появляемся над блоком портала, чтобы не застрять внутри него.
                    Vec3d pos = Vec3d.ofCenter(destination).add(0, 2.0, 0);
                    player.teleport(world, pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
                    PORTAL_LOCK.add(player.getUuid());
                }
            }
        });
    }

    private static boolean nearPortal(BlockPos player, BlockPos portal) {
        return Math.abs(player.getX() - portal.getX()) <= 1
                && Math.abs(player.getY() - portal.getY()) <= 1
                && Math.abs(player.getZ() - portal.getZ()) <= 1;
    }
}
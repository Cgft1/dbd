package ru.cgf.hunt;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A simple two-block-tall locker placed with one item. */
public class LockerBlock extends Block {
    public LockerBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        BlockPos upper = pos.up();
        if (world.isClient || !world.getBlockState(upper).isReplaceable()) return;
        world.setBlockState(upper, state, Block.NOTIFY_ALL);
    }
}

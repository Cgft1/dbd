package ru.cgf.hunt;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.block.ShapeContext;

public class TrapBlock extends Block {
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.08, 0.0, 0.08, 0.92, 0.28, 0.92);
    private static final VoxelShape COLLISION_SHAPE = VoxelShapes.cuboid(0.18, 0.0, 0.18, 0.82, 0.08, 0.82);

    public TrapBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }
}

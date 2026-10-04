package com.dsh.domainexpansion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The "air" that fills the inside of the domain sphere.
 *
 * This block is the central idea borrowed from Cursed Fate, and it is what makes a
 * hundred thousand block domain affordable and completely reversible at the same time.
 *
 * Instead of deleting the terrain inside the sphere - which is both destructive and
 * impossible to undo reliably - the interior is *replaced* with this block. It behaves
 * like air for everything that matters:
 *
 *   - no collision shape and no visual shape, so entities and sight pass straight through
 *   - no occlusion, so it does not darken or cull anything
 *   - not suffocating, not view blocking, not a redstone conductor, invalid spawn
 *
 * but because it is a real block, the state it displaced can be recorded and put back
 * exactly when the domain closes. Emitting light level 15 is what keeps the inside of an
 * otherwise sealed opaque sphere visible.
 */
public class DomainAir extends Block {

    public DomainAir() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GLASS)
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> 15)
                .noCollission()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .pushReaction(PushReaction.BLOCK));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}

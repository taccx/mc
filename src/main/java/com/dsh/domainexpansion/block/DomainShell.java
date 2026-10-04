package com.dsh.domainexpansion.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The visible outer wall of the domain sphere.
 *
 * Modelled on Cursed Fate's {@code DomainExpansionBlockBlock}, which is known to render
 * correctly in this exact modpack: a solid, opaque cube. No {@code noOcclusion}, no
 * {@code noCollission}, no {@code getLightBlock} override, no {@code render_type} - none of
 * the cleverness that was added here over several attempts and that coincided with the dome
 * becoming invisible in game.
 *
 * Being a real solid block is also what makes the domain a barrier: nothing walks or falls
 * through the wall.
 *
 * The one addition is {@code lightLevel}. A sealed opaque sphere blocks the sky, so without
 * it the inside of the domain goes dark within seconds. Cursed Fate solves that by filling
 * the entire interior with light-emitting air blocks, which costs tens of thousands of extra
 * writes; making the wall itself glow like glowstone lights the same space for the cost of
 * the wall alone. Emitting light is a lighting property and cannot affect how the block is
 * drawn, so it carries none of the risk the earlier changes did.
 */
public class DomainShell extends Block {

    public DomainShell() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GLASS)
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> 15));
    }
}

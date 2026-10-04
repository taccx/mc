package com.dsh.domainexpansion.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The floor block of the domain, laid one level below the sphere's centre so it replaces the
 * block the caster is standing on and can be walked on.
 *
 * A separate block from {@link DomainShell} so the two can carry different textures while
 * sharing the same behaviour, including the light emission: the floor is the surface the
 * caster stands on, so lighting it is what keeps the area around them visible once the dome
 * has sealed the sky out.
 */
public class DomainFloor extends Block {

    public DomainFloor() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GLASS)
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> 15));
    }
}

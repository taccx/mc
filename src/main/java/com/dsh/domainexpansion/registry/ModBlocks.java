package com.dsh.domainexpansion.registry;

import com.dsh.domainexpansion.DomainExpansion;
import com.dsh.domainexpansion.block.DomainAir;
import com.dsh.domainexpansion.block.DomainFloor;
import com.dsh.domainexpansion.block.DomainShell;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block registration.
 *
 * Three blocks, mirroring Cursed Fate's set:
 *   - domain_shell: the visible opaque wall of the sphere
 *   - domain_floor: the visible floor under the caster
 *   - domain_air:   the invisible interior filler that makes the sphere reversible
 *
 * None of them are obtainable; they only ever exist while a domain is open.
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DomainExpansion.MODID);

    public static final RegistryObject<Block> DOMAIN_SHELL =
            BLOCKS.register("domain_shell", DomainShell::new);

    public static final RegistryObject<Block> DOMAIN_FLOOR =
            BLOCKS.register("domain_floor", DomainFloor::new);

    public static final RegistryObject<Block> DOMAIN_AIR =
            BLOCKS.register("domain_air", DomainAir::new);
}

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
 * Two sets of three, mirroring Cursed Fate's arrangement, one set per domain:
 *
 *   - domain_shell / crimson_domain_shell: the visible wall of the sphere
 *   - domain_floor / crimson_domain_floor: the visible floor under the caster
 *   - domain_air   / crimson_domain_air:   the invisible interior filler that makes the
 *                                          sphere reversible
 *
 * The block classes are shared; only the texture differs, which comes from the registry name.
 * Two sets rather than one is a deliberate cost: a single shell block would have to pick its
 * texture from blockstate, and the two domains must be able to exist at once (two casters, two
 * overlapping spheres) with each restoring its own blocks.
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

    /** The crimson domain's wall: entirely black, so it reads as a void rather than as blocks. */
    public static final RegistryObject<Block> CRIMSON_DOMAIN_SHELL =
            BLOCKS.register("crimson_domain_shell", DomainShell::new);

    /** The crimson domain's floor: solid, surfaced like cyan-blue water. */
    public static final RegistryObject<Block> CRIMSON_DOMAIN_FLOOR =
            BLOCKS.register("crimson_domain_floor", DomainFloor::new);

    public static final RegistryObject<Block> CRIMSON_DOMAIN_AIR =
            BLOCKS.register("crimson_domain_air", DomainAir::new);
}

package com.dsh.domainexpansion;

import com.dsh.domainexpansion.handler.DomainHitHandler;
import com.dsh.domainexpansion.handler.DomainTargetingHandler;
import com.dsh.domainexpansion.handler.DomainTickHandler;
import com.dsh.domainexpansion.registry.ModBlocks;
import com.dsh.domainexpansion.registry.ModEntities;
import com.dsh.domainexpansion.registry.ModSpells;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Addon for T.O Magic 'n Extras (traveloptics) adding the Aqua school ultimate
 * "Domain Expansion" (领域展开).
 *
 * While a domain is open:
 *   - a sphere of real blocks seals the area off; nothing inside can leave and nothing
 *     outside can enter,
 *   - damage the caster deals inside is applied to every other entity inside,
 *   - the caster's thrown spells curve towards whatever is trapped with them, and lock-on
 *     spells are handed a target instead of needing the crosshair pointed at one,
 *   - Overflow and Rainfall are maintained for the full two minutes at the same spell level
 *     as the domain.
 */
@Mod(DomainExpansion.MODID)
public class DomainExpansion {

    public static final String MODID = "domain_expansion";

    private static final Logger LOGGER = LogUtils.getLogger();

    public DomainExpansion(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModSpells.SPELLS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITIES.register(modBus);

        // gameplay rule hooks live on the forge bus
        MinecraftForge.EVENT_BUS.register(DomainHitHandler.class);
        MinecraftForge.EVENT_BUS.register(DomainTickHandler.class);
        MinecraftForge.EVENT_BUS.register(DomainTargetingHandler.class);

        LOGGER.info("[DomainExpansion] registered spell, domain blocks, entity and gameplay hooks");
    }
}

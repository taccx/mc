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

    /**
     * Sound events this mod declares.
     *
     * The file itself lives in a resource pack in the modpack rather than in this jar. A resource
     * pack is searched for sound files alongside mod jars, so declaring the event here and putting
     * the ogg there behaves exactly like shipping it - while leaving the public repository free of
     * audio that is not ours to publish.
     */
    public static final net.minecraftforge.registries.DeferredRegister<
            net.minecraft.sounds.SoundEvent> SOUNDS =
            net.minecraftforge.registries.DeferredRegister.create(
                    net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS, MODID);

    public static final net.minecraftforge.registries.RegistryObject<
            net.minecraft.sounds.SoundEvent> SLASH_SOUND = SOUNDS.register("slash_custom",
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    MODID, "slash_custom")));

    public DomainExpansion(FMLJavaModLoadingContext context) {

        IEventBus modBus = context.getModEventBus();
        SOUNDS.register(modBus);

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

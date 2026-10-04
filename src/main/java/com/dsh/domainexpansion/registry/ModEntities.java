package com.dsh.domainexpansion.registry;

import com.dsh.domainexpansion.DomainExpansion;
import com.dsh.domainexpansion.entity.DomainEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity type registration for the domain itself.
 */
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DomainExpansion.MODID);

    public static final RegistryObject<EntityType<DomainEntity>> DOMAIN =
            ENTITIES.register("domain", () -> EntityType.Builder
                    .<DomainEntity>of(DomainEntity::new, MobCategory.MISC)
                    // the entity is only a logical anchor for the sphere; it needs a
                    // small box because it must never collide with anything
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build("domain"));
}

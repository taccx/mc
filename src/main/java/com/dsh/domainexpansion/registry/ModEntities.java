package com.dsh.domainexpansion.registry;

import com.dsh.domainexpansion.DomainExpansion;
import com.dsh.domainexpansion.entity.DomainDecorationEntity;
import com.dsh.domainexpansion.entity.DomainEntity;
import com.dsh.domainexpansion.entity.SlashLineEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity type registration: the domain itself, and the slash lines it draws.
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

    /**
     * A slash line: a textured quad and nothing else.
     *
     * The tracking range covers the whole sphere, since a line anywhere inside it should be
     * drawn. Updates are slow because the entity never moves - the renderer animates it from its
     * own age, which both sides count independently, so no per-tick packets are needed.
     */
    public static final RegistryObject<EntityType<SlashLineEntity>> SLASH_LINE =
            ENTITIES.register("slash_line", () -> EntityType.Builder
                    .<SlashLineEntity>of(SlashLineEntity::new, MobCategory.MISC)
                    .sized(30.0F, 1.0F)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .noSummon()
                    .fireImmune()
                    .noSave()
                    .build("slash_line"));

    /**
     * Domain scenery: the ram skull, or one of the four torii.
     *
     * Sized for the larger of the two, since one entity type covers both and the bounding box is
     * what the client culls against. Updates are slow because it never moves - the rise is worked
     * out in the renderer from the entity's own age, which both sides count independently.
     */
    public static final RegistryObject<EntityType<DomainDecorationEntity>> DOMAIN_DECORATION =
            ENTITIES.register("domain_decoration", () -> EntityType.Builder
                    .<DomainDecorationEntity>of(DomainDecorationEntity::new, MobCategory.MISC)
                    .sized(4.0F, 5.5F)
                    .clientTrackingRange(24)
                    .updateInterval(20)
                    .noSummon()
                    .fireImmune()
                    .noSave()
                    .build("domain_decoration"));
}

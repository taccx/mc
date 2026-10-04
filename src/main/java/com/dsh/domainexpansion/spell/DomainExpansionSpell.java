package com.dsh.domainexpansion.spell;

import com.dsh.domainexpansion.DomainConfig;
import com.dsh.domainexpansion.entity.DomainEntity;
import com.dsh.domainexpansion.registry.ModSpells;
import com.gametechbc.traveloptics.api.init.TravelopticsSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 领域展开 / Domain Expansion - an Aqua (源流) ultimate.
 *
 * Casting it opens a 30 block radius domain around the caster for two minutes.
 * See {@link DomainEntity} for the rules it enforces while open, and
 * {@code DomainHitHandler} for the forced-hit rule.
 */
public class DomainExpansionSpell extends AbstractSpell {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath("domain_expansion", "domain_expansion");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(TravelopticsSchools.AQUA_RESOURCE)
            .setMaxLevel(DomainConfig.MAX_LEVEL)
            .setCooldownSeconds(DomainConfig.COOLDOWN_SECONDS)
            .build();

    public DomainExpansionSpell() {
        this.baseManaCost = DomainConfig.BASE_MANA_COST;
        this.manaCostPerLevel = DomainConfig.MANA_COST_PER_LEVEL;
        this.castTime = DomainConfig.CAST_TIME;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
    }

    /**
     * Instant: the domain opens the moment the spell is cast, with no wind-up. Iron's
     * short-circuits {@code getCastTime} for INSTANT casts, so this is the authoritative
     * "no warm-up" declaration rather than {@code castTime} alone.
     */
    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && level instanceof ServerLevel server) {
            // A domain is a claim on the area: one caster, one domain at a time.
            // Casting again while one is open would stack two full block spheres on top
            // of each other (which is exactly what happened when the log showed two
            // "domain opened" lines and doubled build work). The old domain is closed
            // cleanly - restoring its blocks - before the new one opens.
            DomainEntity existing = DomainEntity.activeFor(entity);
            if (existing != null) {
                existing.closeForRecast(server);
            }

            DomainEntity domain = new DomainEntity(server, entity, spellLevel);
            server.addFreshEntity(domain);
            // Initialise explicitly rather than relying on the loader's onAddedToWorld
            // callback. Registration with the tick handler happens inside, and if that step
            // is ever missed the spell silently does nothing at all - the exact symptom that
            // wasted several attempts. onAddedToWorld still calls the same idempotent method,
            // so whichever runs first wins.
            domain.initialize(server);
            LOGGER.info("[DomainExpansion] cast by {} at level {} from {}",
                    entity.getName().getString(), spellLevel, castSource);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * The lines a scroll shows for this spell.
     *
     * The support spells are listed with the level they actually run at, which is one above the
     * domain and therefore not obvious from the spell's own level. Their wet-effect amplifiers
     * are included too, since that is what those levels translate into in practice: a level 3
     * domain runs a level 4 Overflow, which is its maximum.
     */
    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int supportLevel = SupportSpells.levelFor(spellLevel);
        int[] overflowWet = SupportSpells.overflowWetRange(spellLevel, caster);
        int rainfallWet = SupportSpells.rainfallWet(spellLevel, caster);

        return List.of(
                Component.translatable("ui.domain_expansion.domain_main"),
                Component.translatable("ui.domain_expansion.radius",
                        Utils.stringTruncation(DomainConfig.DOMAIN_RADIUS, 0)),
                Component.translatable("ui.domain_expansion.duration",
                        Utils.timeFromTicks(DomainConfig.DOMAIN_DURATION_TICKS, 2)),
                Component.translatable("ui.domain_expansion.forced_hit"),
                Component.translatable("ui.domain_expansion.overflow_level",
                        supportLevel, overflowWet[0], overflowWet[1]),
                Component.translatable("ui.domain_expansion.rainfall_level",
                        supportLevel, rainfallWet));
    }

    @Override
    public boolean stopSoundOnCancel() {
        return true;
    }
}

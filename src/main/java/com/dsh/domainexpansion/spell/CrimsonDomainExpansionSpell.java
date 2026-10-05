package com.dsh.domainexpansion.spell;

import com.dsh.domainexpansion.DomainConfig;
import com.dsh.domainexpansion.domain.DomainKind;
import com.dsh.domainexpansion.entity.DomainEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 领域展开, the crimson (猩红 / Blood) variant.
 *
 * Everything the Aqua domain does, this does too - a radius 30 sphere, a barrier that lets
 * nothing in or out, forced hits inside, two minutes, and the ground restored afterwards - with
 * three differences:
 *
 *   - the wall is entirely black and the floor is surfaced like cyan-blue water,
 *   - there is no Overflow or Rainfall; not every domain maintains support spells,
 *   - everything inside that is not the caster is cut by 猩红斩击 five times a second, for the
 *     whole duration, without the caster doing anything.
 *
 * The two domains share one entity class and differ by {@link DomainKind}; see
 * {@link CrimsonSlash} for the attack and {@link DomainExpansionSpell} for the Aqua original.
 */
public class CrimsonDomainExpansionSpell extends AbstractSpell {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath("domain_expansion", "crimson_domain_expansion");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(DomainConfig.MAX_LEVEL)
            .setCooldownSeconds(DomainConfig.COOLDOWN_SECONDS)
            .build();

    public CrimsonDomainExpansionSpell() {
        this.baseManaCost = DomainConfig.BASE_MANA_COST;
        this.manaCostPerLevel = DomainConfig.MANA_COST_PER_LEVEL;
        this.castTime = DomainConfig.CAST_TIME;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
    }

    /** Long, for the same hand seal and the same reason as the Aqua domain. */
    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    /** Pinned, so the seal animation cannot be cut short by cast-time-reduction gear. */
    @Override
    public int getEffectiveCastTime(int spellLevel, LivingEntity entity) {
        return DomainConfig.CAST_TIME;
    }

    /** The same seal as the Aqua domain: a domain is cast the same way whoever owns it. */
    @Override
    public AnimationHolder getCastStartAnimation() {
        return DomainExpansionSpell.sealAnimation();
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return DomainExpansionSpell.sealFinishAnimation();
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
            // Recasting this spell inside your own crimson domain releases it, and does not open
            // a second one. Asked for so the domain does not have to be waited out: two minutes is
            // a long time to be unable to turn off your own effect. A domain of the other kind is
            // still replaced rather than released, since that is a change of domain, not a
            // dismissal.
            DomainEntity existing = DomainEntity.activeFor(entity);
            if (existing != null && existing.kind() == DomainKind.CRIMSON) {
                existing.closeForRecast(server);
                // the mana was already spent by the time onCast runs, and releasing should not cost
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    // addMana clamps to the player's maximum itself
                    io.redspace.ironsspellbooks.api.magic.MagicData
                            .getPlayerMagicData(player).addMana(getManaCost(spellLevel));
                }
                LOGGER.info("[DomainExpansion] crimson domain released early by {}",
                        entity.getName().getString());
                super.onCast(level, spellLevel, entity, castSource, playerMagicData);
                return;
            }
            if (existing != null) {
                existing.closeForRecast(server);
            }

            DomainEntity domain = new DomainEntity(server, entity, spellLevel, DomainKind.CRIMSON);
            server.addFreshEntity(domain);
            domain.initialize(server);
            LOGGER.info("[DomainExpansion] crimson cast by {} at level {} from {}",
                    entity.getName().getString(), spellLevel, castSource);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * The scroll lines.
     *
     * The slash is listed with the level it runs at and the damage that works out to, because
     * neither is obvious: the level runs above the domain's own, and the damage comes from Blood
     * Slash's scaling rather than anything this spell declares.
     */
    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int slashLevel = CrimsonSlash.levelFor(spellLevel);
        return List.of(
                Component.translatable("ui.domain_expansion.crimson_main"),
                Component.translatable("ui.domain_expansion.radius",
                        Utils.stringTruncation(DomainConfig.DOMAIN_RADIUS, 0)),
                Component.translatable("ui.domain_expansion.duration",
                        Utils.timeFromTicks(DomainConfig.DOMAIN_DURATION_TICKS, 2)),
                Component.translatable("ui.domain_expansion.forced_hit"),
                Component.translatable("ui.domain_expansion.crimson_slash",
                        slashLevel,
                        Utils.stringTruncation(CrimsonSlash.damage(spellLevel, caster), 2)));
    }

    @Override
    public boolean stopSoundOnCancel() {
        return true;
    }

}

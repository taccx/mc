package com.dsh.domainexpansion.spell;

import com.dsh.domainexpansion.DomainConfig;
import com.gametechbc.traveloptics.entity.projectiles.RainfallAoe;
import com.gametechbc.traveloptics.entity.projectiles.overflow.FloodPoolEntity;
import com.gametechbc.traveloptics.spells.aqua.OverflowSpell;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Builds the domain's Overflow and Rainfall to match what those spells would produce if cast
 * at one level above the domain.
 *
 * The support spells are created directly rather than by casting them, because casting them
 * would also charge mana, start cooldowns and fire Iron's cast events - which this addon's own
 * handlers listen to. Reproducing the numbers avoids all of that, at the cost of having to
 * mirror a few private formulas, each of which is quoted below so it can be checked against
 * the source it came from.
 *
 * The level relationship was requested explicitly: a level 3 domain runs a level 4 Overflow, a
 * level 2 domain a level 3 Overflow, and so on. Overflow's own maximum is level 4, so a fully
 * levelled domain runs it at its cap.
 *
 * Durations are deliberately NOT taken from the spells. Both normally expire on their own
 * schedule - Overflow after 200 + level*100 ticks, Rainfall after a flat 100 - but the domain
 * requires them to last exactly as long as it does, so that is what they are given.
 */
public final class SupportSpells {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** The support spells always run one level above the domain. */
    public static final int LEVEL_BONUS = 1;

    private static final ResourceLocation OVERFLOW_ID =
            ResourceLocation.fromNamespaceAndPath("traveloptics", "overflow");
    private static final ResourceLocation RAINFALL_ID =
            ResourceLocation.fromNamespaceAndPath("traveloptics", "rainfall");

    /**
     * The Howling Tempest, cast once by a top level Aqua domain.
     *
     * Asked for as a one-off on top of the two maintained spells: 溢流 and 雨落 are kept running
     * for the domain's whole life, and this is a single heavier cast after the domain opens.
     * Level three only, since it is the domain's top level and the spell is the strongest thing
     * the domain does.
     */
    private static final ResourceLocation HOWLING_TEMPEST_ID =
            ResourceLocation.fromNamespaceAndPath("traveloptics", "the_howling_tempest");

    private static boolean warnedMissingTempest;

    private SupportSpells() {
    }

    /**
     * Casts The Howling Tempest once, at the domain's own level.
     *
     * Goes through the spell's own {@code castSpell} rather than being rebuilt from its parts, the
     * way Overflow and Rainfall are, because this one is a single discrete effect rather than
     * something that has to be kept alive for the domain's duration - so letting the spell do
     * whatever it does is both simpler and more faithful.
     *
     * {@code castSpell} only accepts a server player, which the caster of a domain essentially
     * always is; a non-player caster is logged and skipped rather than silently doing nothing.
     *
     * @return whether it was cast
     */
    public static boolean castHowlingTempest(net.minecraft.server.level.ServerLevel server,
                                             LivingEntity caster, int domainLevel) {
        AbstractSpell spell = spell(HOWLING_TEMPEST_ID);
        if (spell == null) {
            if (!warnedMissingTempest) {
                warnedMissingTempest = true;
                LOGGER.warn("[DomainExpansion] traveloptics:the_howling_tempest is not in the spell "
                        + "registry; a level 3 aqua domain will not cast it");
            }
            return false;
        }
        if (!(caster instanceof net.minecraft.server.level.ServerPlayer player)) {
            LOGGER.info("[DomainExpansion] the_howling_tempest skipped: caster {} is not a player",
                    caster.getName().getString());
            return false;
        }
        int level = Math.max(1, Math.min(domainLevel, spell.getMaxLevel()));
        spell.castSpell(server, level, player, CastSource.COMMAND, false);
        LOGGER.info("[DomainExpansion] the_howling_tempest cast at level {} by {}",
                level, caster.getName().getString());
        return true;
    }

    /** The level the support spells run at, for a domain of the given level. */
    public static int levelFor(int domainLevel) {
        return domainLevel + LEVEL_BONUS;
    }

    public static AbstractSpell overflow() {
        return spell(OVERFLOW_ID);
    }

    public static AbstractSpell rainfall() {
        return spell(RAINFALL_ID);
    }

    private static AbstractSpell spell(ResourceLocation id) {
        var registry = SpellRegistry.REGISTRY.get();
        return registry == null ? null : registry.getValue(id);
    }

    /**
     * Spell power at the level the support spells will run at.
     *
     * Falls back to the raw level when the spell or caster is unavailable - the scroll tooltip
     * is built without a caster in some contexts, and a wrong-but-sane number is better there
     * than a crash.
     */
    private static float power(AbstractSpell spell, int domainLevel, LivingEntity caster) {
        int level = levelFor(domainLevel);
        if (spell == null || caster == null) {
            return level;
        }
        return spell.getSpellPower(level, caster);
    }

    /**
     * The wet-effect amplifier range Overflow applies.
     *
     * Mirrors {@code OverflowSpell.getMinWetAmplifier} / {@code getMaxWetAmplifier}, which are
     * private:
     * <pre>
     *   min = (int) (1.0 + spellPower * 2.25)
     *   max = (int) (3.0 + spellPower * 6.5)
     * </pre>
     */
    public static int[] overflowWetRange(int domainLevel, LivingEntity caster) {
        float power = power(overflow(), domainLevel, caster);
        return new int[]{(int) (1.0F + power * 2.25F), (int) (3.0F + power * 6.5F)};
    }

    /**
     * The wet-effect amplifier Rainfall applies.
     *
     * Mirrors {@code RainfallSpell.getWetAmplifier}, which is private:
     * <pre>
     *   (int) (spellPower * 2.0)
     * </pre>
     */
    public static int rainfallWet(int domainLevel, LivingEntity caster) {
        return (int) (power(rainfall(), domainLevel, caster) * 2.0F);
    }

    /**
     * Overflow's pool radius.
     *
     * Taken from the spell itself, since {@code OverflowSpell.getRadius} is public:
     * {@code 5.0 + level * 5.0}. Unlike the amplifiers this needs no mirroring, but the literal
     * is kept as a fallback in case the spell is not in the registry.
     */
    public static float overflowRadius(int domainLevel) {
        int level = levelFor(domainLevel);
        AbstractSpell spell = overflow();
        if (spell instanceof OverflowSpell concrete) {
            return concrete.getRadius(level);
        }
        return 5.0F + level * 5.0F;
    }

    /**
     * Overflow's contact damage.
     *
     * Mirrors {@code OverflowSpell.getDamage}, which is private and mixes in the caster's
     * lightning spell power:
     * <pre>
     *   base      = 6.0 + spellPower * 1.5
     *   lightning = spellPower * (lightningPower - 1.0) * 0.75
     * </pre>
     */
    public static float overflowDamage(int domainLevel, LivingEntity caster) {
        float power = power(overflow(), domainLevel, caster);
        float lightning = 1.0F;
        if (caster != null) {
            lightning = (float) caster.getAttributeValue((Attribute) AttributeRegistry.LIGHTNING_SPELL_POWER.get());
        }
        return 6.0F + power * 1.5F + power * (lightning - 1.0F) * 0.75F;
    }

    /** An Overflow pool configured for this domain, ready to be added to the level. */
    public static FloodPoolEntity createOverflow(net.minecraft.server.level.ServerLevel level,
                                                 LivingEntity caster, int domainLevel) {
        FloodPoolEntity pool = new FloodPoolEntity(level);
        pool.setOwner(caster);
        pool.setRadius(overflowRadius(domainLevel));
        // the domain's duration, not Overflow's own
        pool.setDuration(DomainConfig.DOMAIN_DURATION_TICKS);
        pool.setCircular();
        int[] wet = overflowWetRange(domainLevel, caster);
        pool.setMinWetAmplifier(wet[0]);
        pool.setMaxWetAmplifier(wet[1]);
        pool.setDamage(overflowDamage(domainLevel, caster));
        pool.setPos(caster.getX(), caster.getY(), caster.getZ());
        return pool;
    }

    /**
     * A Rainfall downpour configured for this domain.
     *
     * The radius is the domain's, not Rainfall's own {@code 2 + level * 2}: the point of casting
     * it inside a domain is for the rain to cover everything trapped in there.
     */
    public static RainfallAoe createRainfall(net.minecraft.server.level.ServerLevel level,
                                             LivingEntity caster, int domainLevel,
                                             double x, double y, double z) {
        RainfallAoe rain = new RainfallAoe(level);
        rain.setOwner(caster);
        rain.setRadius(DomainConfig.DOMAIN_RADIUS);
        rain.setDuration(DomainConfig.DOMAIN_DURATION_TICKS);
        rain.setCircular();
        rain.setWetEffectAmplifier(rainfallWet(domainLevel, caster));
        rain.setPos(x, y, z);
        return rain;
    }
}

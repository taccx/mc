package com.dsh.domainexpansion.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The crimson domain's automatic attack: 猩红斩击, Iron's Spellbooks' Blood Slash, applied
 * directly to everything inside that is not the caster, five times a second.
 *
 * Three things about this are deliberate and worth keeping in mind.
 *
 * <b>The damage is not attributed to the caster.</b> This addon's forced-hit rule broadcasts
 * damage the caster deals inside their domain to every other entity in it, and it identifies
 * that damage by requiring a {@code Player} as the source. A slash credited to the caster would
 * therefore be re-broadcast across everything inside on every one of the five hits a second -
 * five times N squared damage, and N squared the work. The slash is dealt through a plain magic
 * source with no attacker, so the rule skips it and the numbers stay sane. It also matches what
 * was asked for: these are not the caster's slashes.
 *
 * <b>The hurt cooldown has to be cleared.</b> A living entity ignores damage while its
 * {@code invulnerableTime} is above 10, which lasts 20 ticks after any hit. Left alone, five
 * hits a second would collapse to one. Clearing it immediately before each strike is what makes
 * the rate real.
 *
 * <b>The visual is a real Blood Slash projectile, but with zero damage.</b> That is what draws
 * the slash arc, and on contact it bursts blood particles on the victim - the effect appearing
 * on the entity rather than flying at it from the caster. Its damage is zero because the damage
 * has already been applied by then; letting the projectile deal it too would double every hit.
 */
public final class CrimsonSlash {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * The slash runs this many levels above the domain, so a level 3 domain uses Blood Slash's
     * maximum of 5 - which is what "the highest domain uses the highest slash" works out to,
     * given the domain tops out at 3 and the slash at 5.
     *
     * Levels are therefore domain 1 -> slash 3, 2 -> 4, 3 -> 5. If a different spread is wanted
     * - mapping 1 -> 1 and 3 -> 5 evenly, say - this is the number, and the clamp below.
     */
    public static final int LEVEL_BONUS = 2;

    /** Four ticks between strikes: five a second. */
    public static final int INTERVAL_TICKS = 4;

    /**
     * How many slash visuals may exist at once.
     *
     * The projectile lives 80 ticks and grows as it flies, so without a cap a crowded domain
     * would hold five times the entity count times eighty of them - thousands of rendered
     * models for nothing. Past the cap the damage still lands; only the drawing is skipped.
     */
    public static final int MAX_LIVE_VISUALS = 40;

    /** Long enough to see the arc, short enough that it is gone before the next strike. */
    public static final int VISUAL_LIFETIME_TICKS = 5;

    private static final ResourceLocation BLOOD_SLASH_ID =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood_slash");

    /** Fallback when the spell is not in the registry, so the mapping still reaches its top. */
    private static final int FALLBACK_MAX_LEVEL = 5;

    private static boolean warnedMissingSpell;

    private CrimsonSlash() {
    }

    public static AbstractSpell spell() {
        var registry = SpellRegistry.REGISTRY.get();
        return registry == null ? null : registry.getValue(BLOOD_SLASH_ID);
    }

    /** The level the slash runs at, never above the slash's own maximum. */
    public static int levelFor(int domainLevel) {
        AbstractSpell spell = spell();
        int max = spell != null ? spell.getMaxLevel() : FALLBACK_MAX_LEVEL;
        return levelAt(domainLevel, max);
    }

    /** The mapping itself, as arithmetic, so it can be tested without a running game. */
    public static int levelAt(int domainLevel, int slashMaxLevel) {
        return Math.max(1, Math.min(domainLevel + LEVEL_BONUS, slashMaxLevel));
    }

    /**
     * One strike's damage, taken from the Blood Slash spell itself so the scaling - including
     * the caster's spell power - is exactly what casting it would have produced at that level.
     */
    public static float damage(int domainLevel, LivingEntity caster) {
        AbstractSpell spell = spell();
        int level = levelFor(domainLevel);
        if (spell == null) {
            if (!warnedMissingSpell) {
                warnedMissingSpell = true;
                LOGGER.warn("[DomainExpansion] blood_slash is not in the spell registry; "
                        + "crimson domain slashes fall back to a flat 10 damage");
            }
            return 10.0F;
        }
        return spell.getSpellPower(level, caster);
    }

    /** Applies one strike, clearing the hurt cooldown so the five-per-second rate actually lands. */
    public static void strike(ServerLevel server, LivingEntity victim, float damage) {
        if (damage <= 0.0F || !victim.isAlive()) {
            return;
        }
        victim.invulnerableTime = 0;
        victim.hurt(server.damageSources().magic(), damage);
    }

    /**
     * Spawns the slash arc on the victim.
     *
     * Aimed in a random horizontal direction, because the point is the arc appearing on the
     * entity, not a projectile arriving from anywhere in particular. Damage is left at zero.
     */
    public static BloodSlashProjectile spawnVisual(ServerLevel server, LivingEntity caster,
                                                   LivingEntity victim) {
        BloodSlashProjectile slash = new BloodSlashProjectile(server, caster);
        slash.setDamage(0.0F);
        slash.moveTo(victim.getX(),
                victim.getY() + victim.getBbHeight() * 0.5D,
                victim.getZ());
        double angle = server.random.nextDouble() * Math.PI * 2.0D;
        slash.shoot(new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)));
        server.addFreshEntity(slash);
        return slash;
    }
}

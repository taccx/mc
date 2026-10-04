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
 * directly to everything inside that is not the caster, thirty times a second.
 *
 * The rate began at five a second and was raised once Blood Slash turned out to hit for very
 * little; see {@link #HITS_PER_TICK} for how thirty a second is built out of twenty ticks.
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

    /**
     * How often the slash lands.
     *
     * Thirty times a second was asked for, after Blood Slash turned out to hit for very little.
     * Thirty is not a whole number of ticks - a second is twenty - so it is built as one strike
     * every tick plus one more on every other tick, which averages 1.5 a tick and is exactly
     * thirty a second. {@link #hitsForTick} is that rule, kept as arithmetic so it can be
     * tested rather than counted by hand.
     */
    public static final int HITS_PER_TICK = 1;

    /** One extra strike on every Nth tick. Two, which is what turns 1 into 1.5. */
    public static final int EXTRA_HIT_EVERY_TICKS = 2;

    /** The resulting rate, for the scroll text and the log. */
    public static final double HITS_PER_SECOND =
            20.0D * (HITS_PER_TICK + 1.0D / EXTRA_HIT_EVERY_TICKS);

    /**
     * One strike in this many is allowed to make a sound.
     *
     * Every strike plays the victim's hurt sound, and thirty a second is not a sound effect, it
     * is a buzzsaw. The rest are applied with the victim silenced for the instant of the blow,
     * which is a server-side flag and does not reach the client, so nothing else about the
     * entity is affected. Two a second is enough to hear that something is happening.
     */
    public static final int AUDIBLE_EVERY_TICKS = 10;

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

    /** How many strikes land on a given domain tick. One, or two on every other tick. */
    public static int hitsForTick(int domainTick) {
        return HITS_PER_TICK
                + (Math.floorMod(domainTick, EXTRA_HIT_EVERY_TICKS) == 0 ? 1 : 0);
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

    /**
     * Applies one strike, clearing the hurt cooldown so the rate actually lands.
     *
     * @param audible whether this one is allowed to play the victim's hurt sound; see
     *                {@link #AUDIBLE_EVERY_TICKS}
     */
    public static void strike(ServerLevel server, LivingEntity victim, float damage, boolean audible) {
        if (damage <= 0.0F || !victim.isAlive()) {
            return;
        }
        victim.invulnerableTime = 0;
        if (audible) {
            victim.hurt(server.damageSources().magic(), damage);
            return;
        }
        boolean wasSilent = victim.isSilent();
        victim.setSilent(true);
        try {
            victim.hurt(server.damageSources().magic(), damage);
        } finally {
            victim.setSilent(wasSilent);
        }
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

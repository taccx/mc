package com.dsh.domainexpansion.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

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
     * Thirty times a second, which is where this started and where it has come back to. It went
     * to a hundred and the damage went <em>down</em> in play - more strikes than the hurt
     * pipeline and the pack's own damage handling can usefully process, so the extra hits cost
     * frame time without landing. Thirty is one strike a tick plus one on every other tick, which
     * averages 1.5 a tick and is exactly thirty a second; {@link #hitsForTick} is that rule as
     * arithmetic so it can be tested rather than counted by hand.
     */
    public static final int HITS_PER_TICK = 1;

    /** One extra strike on every Nth tick. Two, which is what turns 1 into 1.5. */
    public static final int EXTRA_HIT_EVERY_TICKS = 2;

    /** The resulting rate, for the scroll text and the log. */
    public static final double HITS_PER_SECOND =
            20.0D * (HITS_PER_TICK + 1.0D / EXTRA_HIT_EVERY_TICKS);

    /**
     * Blood bursts a second, at most, <em>per victim</em>.
     *
     * Per victim because that is what was asked for the lines, and the same reasoning applies:
     * each entity being cut should bleed. Thirty a second is a third of the strikes it is taking.
     */
    public static final int BLOOD_PER_VICTIM_PER_SECOND = 30;

    /**
     * Slash lines a second, at most, <em>per victim</em>. No longer used.
     *
     * Fifty a second each was asked for, then removed: the automatic attack is damage and blood
     * only now, with no entity of its own, and every line in the domain comes from the ambient
     * layer at {@code DomainEntity.AMBIENT_LINES_PER_SECOND}. Kept as a note so the number is not
     * re-invented if the per-victim lines are ever wanted back.
     */
    public static final int LINES_PER_VICTIM_PER_SECOND = 50;

    /**
     * A ceiling on blood bursts in one tick, across every victim.
     *
     * Twelve, which is two hundred and forty a second - roughly eight victims' worth. Beyond
     * that the bursts are skipped and counted rather than drawn; the damage is untouched.
     */
    public static final int MAX_BLOOD_BURSTS_PER_TICK = 12;

    /**
     * Particles in one blood burst.
     *
     * Twenty-four, up from ten. Ten was subtle enough that the blood was reported as missing
     * entirely, which is a reasonable reading of a spatter you have to look for.
     */
    public static final int BLOOD_PARTICLES_PER_BURST = 24;

    /**
     * One strike in this many is allowed to make a sound.
     *
     * Every strike plays the victim's hurt sound, and a hundred a second is not a sound effect,
     * it is a buzzsaw. The rest are applied with the victim silenced for the instant of the blow,
     * which is a server-side flag and does not reach the client, so nothing else about the
     * entity is affected. Two a second is enough to hear that something is happening.
     */
    public static final int AUDIBLE_EVERY_TICKS = 10;

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
     * How many times a per-second allowance may be spent on a given tick.
     *
     * A second is twenty ticks, so an allowance that does not divide evenly - thirty, or fifty -
     * has to be spread rather than divided. Whole spends go on every tick and the remainder goes
     * on the first ticks of each second, which lands exactly on the requested rate over any
     * whole second and never bunches the leftovers into one tick.
     *
     * Kept as arithmetic so the rates can be tested instead of counted by hand.
     */
    public static int allocationsThisTick(int perSecond, int domainTick) {
        int everyTick = perSecond / 20;
        int remainder = perSecond % 20;
        return everyTick + (Math.floorMod(domainTick, 20) < remainder ? 1 : 0);
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
     * Spatters blood on the victim.
     *
     * Emitted directly rather than left to a projectile. The Blood Slash projectile used to be
     * both the damage and the blood, but the damage is applied here, and now that the visible
     * slash is a line rather than that projectile the only thing it was still contributing was
     * this burst - which is a handful of particles at a position, and does not need an entity,
     * a lifetime, a hitbox and a tick to deliver.
     */
    public static void bloodBurst(ServerLevel server, LivingEntity victim) {
        server.sendParticles(ParticleHelper.BLOOD,
                victim.getX(),
                victim.getY() + victim.getBbHeight() * 0.5D,
                victim.getZ(),
                BLOOD_PARTICLES_PER_BURST,
                victim.getBbWidth() * 0.4D,
                victim.getBbHeight() * 0.4D,
                victim.getBbWidth() * 0.4D,
                0.0D);
    }
}

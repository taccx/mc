package com.dsh.domainexpansion.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
     * Slowness applied to everything being cut.
     *
     * The drag half of what was asked for - knockback or drag - and the half that suits a rate of
     * a hundred strikes a second: knockback at that frequency shakes a victim apart rather than
     * holding it. Three seconds of Slowness, refreshed whenever it drops below two, so it is
     * reapplied about once a second instead of every tick - a packet per tick per entity for no
     * visible difference.
     */
    public static final int DRAG_DURATION_TICKS = 60;
    public static final int DRAG_REFRESH_BELOW = 40;
    public static final int DRAG_AMPLIFIER = 1;

    /**
     * The shove applied on the audible strike, which is twice a second.
     *
     * Away from the domain's centre, so the pressure reads as being driven towards the wall
     * rather than as random jitter. Small on purpose: a flinch, not a launch.
     */
    public static final double FLINCH_STRENGTH = 0.35D;
    public static final double FLINCH_LIFT = 0.12D;

    /**
     * Volume and pitch of the slash sound.
     *
     * A vanilla sweep - the whoosh the game already uses for a sweep attack - because it is the
     * right noise and carries no licensing question at all, which the sounds inside the mod this
     * is modelled on would.
     */
    public static final float SLASH_SOUND_VOLUME = 0.9F;

    /**
     * Slash sounds a second made by the domain itself, with nothing in it.
     *
     * Asked for after the sound turned out to be audible only near a victim: the per-victim
     * sounds are positional, so an empty domain was silent, and a domain that goes quiet when
     * nothing is being cut does not read as one. These are played at random points in the cone
     * the viewer is looking at, so the domain has a voice of its own either way.
     *
     * Twenty, which is one a tick and therefore also the ceiling. Asking for more would put
     * several on the same tick, and sounds that share a tick and a nearby position do not read as
     * more slashes, they read as one louder one - the stacking that was asked to be capped. One a
     * tick cannot stack with itself, and the spread of positions and pitches keeps consecutive
     * ones from sounding like a repeat.
     */
    public static final int AMBIENT_SOUNDS_PER_SECOND = 20;

    /**
     * Slash marks drawn on a victim, per second, each.
     *
     * Removed once, when the automatic attack was asked to be damage and blood only, and asked for
     * again since: a victim being cut needs to look like it. Six a second each, which is enough to
     * read as repeated strikes without burying the victim in marks.
     *
     * They share {@link SlashLines#MAX_LIVE_LINES} with the ambient layer, and that is the cap -
     * once the sphere is full of marks, further ones are skipped and counted rather than drawn. The
     * damage is untouched by that: the ceiling is on the drawing, not on the hitting.
     */
    public static final int MARKS_PER_VICTIM_PER_SECOND = 12;

    /** How far above a victim's feet its marks are centred, as a fraction of its height. */
    public static final double MARK_HEIGHT_FRACTION = 0.6D;

    /**
     * Volume of an ambient slash, which is now the same as a victim's rather than quieter.
     *
     * At 0.65 of it the empty-domain layer was reported as too quiet to notice, which is fair: it
     * was mixed down to sit under the per-victim sounds, and then became the only layer whenever
     * the domain was empty.
     */
    public static final float AMBIENT_SOUND_VOLUME = 0.9F;

    /**
     * Damage as a fraction of what Blood Slash would deal.
     *
     * Cut to 0.4 at the same time the damage type stopped being resistible. Every one of the
     * thirty strikes a second now lands whatever the target's armour, protection, Resistance,
     * absorption or invulnerability says, so leaving the damage where it was would have been a
     * large increase rather than a change of kind. This is the reduction that pays for it.
     */
    public static final float TRUE_DAMAGE_FRACTION = 3.2F;

    /**
     * The damage type for the crimson slash: registered in data, and listed in every vanilla
     * bypass tag - armour, enchantments, effects, resistance, invulnerability and shields.
     *
     * Absorption is not among them. 1.20.1 has no bypass tag for it, so the absorption amount is
     * cleared before each strike instead, which comes to the same thing.
     *
     * Built on demand rather than kept in a field. {@code Registries.DAMAGE_TYPE} is only
     * populated once the game bootstraps, so a static field here throws during class
     * initialisation in a plain unit test and takes every test in the class with it - which is
     * exactly what happened. Nothing on the tested paths calls this.
     */
    private static ResourceKey<DamageType> trueSlashType() {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("domain_expansion", "true_slash"));
    }

    /**
     * How often a slash is heard, in ticks. Two, which is ten a second.
     *
     * It was ten ticks - twice a second - and was reported as far too sparse, with the ask being
     * to sound like being chopped into mince, which is fair: the effect the domain is following
     * is a continuous flurry, not a series of distinct hits.
     *
     * The victim's own hurt sound is no longer part of this at all. Every strike is silent now
     * and the slash itself is the sound, because ten hurt sounds a second is a generic grunt
     * repeated, where ten whooshes is a flurry.
     */
    public static final int SOUND_EVERY_TICKS = 1;

    /**
     * How often a victim is shoved, in ticks. Ten, so twice a second.
     *
     * Deliberately slower than the sound. The flurry should be audible continuously but the
     * shove at that rate would shake a victim apart rather than drive it anywhere.
     */
    public static final int FLINCH_EVERY_TICKS = 10;

    private static final ResourceLocation BLOOD_SLASH_ID =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood_slash");

    /** Fallback when the spell is not in the registry, so the mapping still reaches its top. */
    private static final int FALLBACK_MAX_LEVEL = 5;

    private static boolean warnedMissingSpell;

    /** Set once if the custom damage type cannot be resolved, so the log is not spammed. */
    private static boolean warnedMissingDamageType;

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
    /**
     * The raw spell power Blood Slash reports at the level a domain of this level runs it.
     *
     * Exposed only so the damage can be logged alongside what it was derived from: if this number
     * does not move when the caster's gear changes, then the caster's own bonuses are not reaching
     * it, and that is the question worth being able to answer without guessing.
     */
    public static float basePower(int domainLevel, LivingEntity caster) {
        AbstractSpell spell = spell();
        if (spell == null) {
            return 10.0F;
        }
        return spell.getSpellPower(levelFor(domainLevel), caster);
    }

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
     * Keeps the victim slowed while it is inside.
     *
     * Reapplied only when the effect is nearly gone, so this is about one packet per second per
     * entity rather than one per tick. The icon is shown, which is deliberate: it makes the drag
     * visible in a screenshot, and it is genuinely useful information for someone inside.
     */
    public static void applyDrag(LivingEntity victim) {
        if (!victim.isAlive()) {
            return;
        }
        MobEffectInstance current = victim.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (current != null && current.getDuration() > DRAG_REFRESH_BELOW
                && current.getAmplifier() >= DRAG_AMPLIFIER) {
            return;
        }
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                DRAG_DURATION_TICKS, DRAG_AMPLIFIER, false, true, true));
    }

    /**
     * The flurry: one slash heard, ten times a second, per victim.
     */
    public static void slashSound(ServerLevel server, LivingEntity victim) {
        if (!victim.isAlive()) {
            return;
        }
        server.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
                victim.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SLASH_SOUND_VOLUME, 0.75F + server.random.nextFloat() * 0.5F);
    }

    /**
     * The shove, twice a second: pushed away from the middle of the domain.
     *
     * Slower than the sound on purpose - at ten a second a victim would be shaken apart rather
     * than driven anywhere - and aimed outwards so the pressure reads as being forced towards the
     * wall rather than as random jitter.
     */
    public static void flinch(LivingEntity victim, Vec3 from) {
        if (!victim.isAlive()) {
            return;
        }
        Vec3 away = victim.position().subtract(from);
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize().scale(FLINCH_STRENGTH);
        victim.push(away.x, FLINCH_LIFT, away.z);
        // without this the client keeps drawing the victim on its old path
        victim.hurtMarked = true;
    }

    /**
     * Applies one strike, clearing the hurt cooldown so the rate actually lands.
     *
     * Always silent, and always true damage now. The victim's hurt sound used to be let through
     * occasionally and it is the wrong noise for this: a grunt repeated ten times a second is not
     * a flurry. The slash sound is played separately by {@link #slashSound}.
     *
     * The damage ignores armour, protection, Resistance, effects, shields and the invulnerability
     * that normally limits how often a target can be hit, because the damage type is listed in
     * every vanilla bypass tag. Absorption is cleared first, since 1.20.1 has no tag for it.
     */
    public static void strike(ServerLevel server, LivingEntity victim, float damage) {
        if (damage <= 0.0F || !victim.isAlive()) {
            return;
        }
        float amount = damage * TRUE_DAMAGE_FRACTION;
        float remaining = victim.getHealth() - amount;

        if (remaining <= 0.0F) {
            // Let the game handle the death, so the drops, the death message and the removal all
            // happen as they should. die() runs the whole death path directly rather than through
            // hurt(), which is the point.
            victim.setHealth(0.0F);
            victim.die(trueSlash(server));
            return;
        }

        // Health is written directly rather than going through hurt().
        //
        // This is the second attempt at true damage and the reason for it. The first used a custom
        // damage type listed in every vanilla bypass tag - armour, enchantments, effects, resistance,
        // invulnerability, shields - and that covers everything VANILLA does. What it does not cover
        // is other mods: most modded damage reduction is not a tag at all, it is a handler on
        // LivingHurtEvent or LivingDamageEvent, and no damage type can reach those. A pack with
        // enchantments called things like magic-breaking and adaptation has several, and enumerating
        // every mod's reduction is a losing game - the list can never be finished.
        //
        // Writing the health skips the entire pipeline those handlers hang off, so there is nothing
        // left to reduce it. The cost is that a victim gets no damage flash and no hurt reaction of
        // its own - but blood, sound and the shove are all applied here anyway, so the effect still
        // reads. The damage type is kept for the death path, where a source is still needed.
        victim.setHealth(remaining);
        victim.invulnerableTime = 0;
        if (victim.getAbsorptionAmount() > 0.0F) {
            victim.setAbsorptionAmount(0.0F);
        }
        // so the client redraws the health bar rather than only finding out when it next syncs
        victim.hurtMarked = true;
    }

    /**
     * The unresistible damage source.
     *
     * Built from the data-driven damage type registry rather than a vanilla source, because what
     * makes it unresistible is which tags the type is listed in, and those are data. Falls back to
     * magic if the type is somehow absent, which is resistible but still bypasses armour - a
     * weaker hit is a better failure than a crash.
     */
    public static DamageSource trueSlash(ServerLevel server) {
        Registry<DamageType> registry = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> holder = registry.getHolder(trueSlashType()).orElse(null);
        if (holder == null) {
            if (!warnedMissingDamageType) {
                warnedMissingDamageType = true;
                LOGGER.warn("[DomainExpansion] damage type domain_expansion:true_slash is missing; "
                        + "crimson slashes fall back to magic damage, which armour still resists");
            }
            return server.damageSources().magic();
        }
        return new DamageSource(holder);
    }

    /**
     * A slash sound at a point, for the domain's own ambient layer.
     *
     * Same sound as a victim's, quieter and with a wider pitch spread: it is there to fill the
     * space rather than to mark a hit, and a spread keeps ten a second from reading as one tone.
     */
    public static void ambientSlashSound(ServerLevel server, Vec3 at) {
        server.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, AMBIENT_SOUND_VOLUME,
                0.6F + server.random.nextFloat() * 0.8F);
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

package com.dsh.domainexpansion;

/**
 * Shared constants and tuning values for the Domain Expansion spell.
 */
public final class DomainConfig {

    private DomainConfig() {
    }

    /**
     * Radius of the domain in blocks.
     *
     * 30 as originally specified. Size is affordable here because of how the sphere is
     * built: the interior is only written where it is not already open space, so over
     * clear ground the cost is the one block thick wall plus the floor - roughly 11,000
     * writes - rather than the ~113,000 positions the sphere contains.
     *
     * Cursed Fate, which this build is modelled on, uses 27. Drop to that if anything
     * about this ever feels heavy; it is a single number.
     */
    public static final float DOMAIN_RADIUS = 30.0F;

    /**
     * How long the finished domain stands, in milliseconds of real time.
     *
     * Timing by game ticks alone is not good enough on a laggy world. An earlier build was
     * fully placed and then expired four real seconds later, because the server had fallen
     * 336 ticks behind and burned through the whole two minutes while catching up. Real
     * time is credited instead, capped per tick by {@link #MAX_TICK_MILLIS_CREDIT} so a
     * catch-up burst cannot skip the duration either. Pausing the game pauses the domain,
     * because no ticks run.
     */
    public static final long DOMAIN_DURATION_MILLIS = 2 * 60 * 1000L;

    /**
     * Ceiling on real time credited by a single tick, in milliseconds.
     *
     * Measured on the target machine: the server runs at roughly 17 ticks per second, so a
     * tick averages ~59ms, and with a 100ms ceiling enough slow ticks were being truncated
     * that a domain credited only ~50ms per tick and stood for 142 real seconds instead of
     * the requested 120. 250ms leaves ordinary lag fully accounted for while still stopping
     * a long freeze - a minimised window, say - from consuming the whole duration in a
     * single tick.
     */
    public static final long MAX_TICK_MILLIS_CREDIT = 250L;

    /** Two minutes in ticks; passed to the Overflow and Rainfall entities. */
    public static final int DOMAIN_DURATION_TICKS = 2 * 60 * 20;

    /** Max spell level of Domain Expansion. */
    public static final int MAX_LEVEL = 3;

    /**
     * Base mana cost, and the cost added by each level above the first.
     *
     * Iron's computes {@code baseManaCost + manaCostPerLevel * (level - 1)}, multiplied by the
     * spell's {@code MANA_MULTIPLIER} config value - which is 1.0 here, since neither the pack's
     * server config nor any datapack overrides it. These two numbers therefore give exactly:
     *
     *   level 1 -> 1000
     *   level 2 -> 2000
     *   level 3 -> 3000
     *
     * The domain is meant to be the Aqua school's ultimate, and at the original 200/200 it cost
     * 600 at maximum level, which is pocket change next to what it does.
     *
     * Worth knowing before tuning: a cost can only be paid if the caster's maximum mana can
     * reach it. Iron's base maximum is far below 3000 and is raised by attributes from gear, so
     * if a level 3 scroll cannot be cast at all, this is why - lower these together rather than
     * only the base, or the top level stays out of reach.
     */
    public static final int BASE_MANA_COST = 1000;

    /** Additional mana per spell level. */
    public static final int MANA_COST_PER_LEVEL = 1000;

    /**
     * The wind-up, in ticks: 30 ticks is 1.5 seconds - 1.0 of the hands drawing together, then
     * 0.5 holding still, and the domain opens when the cast completes. The last of the gap
     * between the hands is closed by the finish animation after that, over 0.4 seconds.
     *
     * The animation shipped at {@code player_animation/domain_seal.json} is authored for exactly
     * this timing. Changing this number without also changing the animation will desynchronise
     * the two - a shorter cast cuts the motion off before the hands have drawn together, a
     * longer one leaves the seal held after the sphere has already appeared.
     *
     * It was 40 (two seconds) until it was asked to be tighter: the drawing-together shortened
     * from 1.5s to 1.0s, the 0.5s hold left alone, and the closing contact slowed from 0.2s to
     * 0.4s. Before that it was 0, because the original four second wind-up felt slow in play.
     */
    public static final int CAST_TIME = 30;

    /**
     * Whether spells cast inside the domain are given a target automatically.
     *
     * OFF by default, deliberately, after testing it in game. Three reasons, in order of
     * weight:
     *
     *  - It is a large power increase. The domain already forces every hit to land on everyone
     *    inside; removing the need to aim as well makes every offensive spell in the game
     *    effectively point-and-click for as long as the domain lasts. The user's own words were
     *    that this would be 超模 (overpowered).
     *  - It has a side effect on CONTINUOUS spells. Those re-enter castSpell every tick while
     *    channelled, so a single 呼啸风暴 channel produced 21 auto-target events over 72 seconds,
     *    bending the caster's aim on every one of those ticks.
     *  - It only ever worked for part of the spell list. Iron's chooses targets in several
     *    places; a spell that targets inside {@code checkPreCastConditions} has already decided
     *    before any event a mod can hook, and {@code Utils.preCastTargetHelper} always raycasts
     *    and overwrites whatever was placed there. Spells that read the target back out of the
     *    cast data can be re-pointed, which is what the handler does, but that only covers some
     *    of them.
     *
     * The code is kept rather than deleted, because it does work where it works - a logged
     * lightning_lance cast was redirected onto the nearest entity - and turning it back on is
     * this one line. Nothing else in the mod is gated on it.
     */
    public static final boolean AUTO_TARGET_ENABLED = false;

    /** Cooldown in seconds. */
    public static final double COOLDOWN_SECONDS = 300.0D;
}

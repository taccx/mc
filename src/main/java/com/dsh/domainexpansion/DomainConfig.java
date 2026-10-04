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

    /** Base mana cost per cast. */
    public static final int BASE_MANA_COST = 200;

    /** Additional mana per spell level. */
    public static final int MANA_COST_PER_LEVEL = 200;

    /** Cast time in ticks; the spell also declares itself INSTANT. */
    public static final int CAST_TIME = 0;

    /** Cooldown in seconds. */
    public static final double COOLDOWN_SECONDS = 300.0D;
}

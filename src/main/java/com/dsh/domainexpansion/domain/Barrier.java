package com.dsh.domainexpansion.domain;

/**
 * The containment rule, as arithmetic.
 *
 * Kept out of the entity and free of Minecraft types so the numbers can be tested directly.
 * The barrier is the one requirement that cannot be seen in a log at all - a teleport either
 * happens or it does not, and "the mob walked through the wall" looks the same as "the mob
 * was never inside" - so the arithmetic is pinned down here instead.
 *
 * The sphere's wall occupies the band {@code (radius - 1)^2 < d^2 <= radius^2}, that is
 * distances from just above {@code radius - 1} out to {@code radius}. The rules are:
 *
 *   - an entity that was inside when the domain opened is held at {@link #innerLimit}, just
 *     clear of the wall's inner face. It is pulled back the moment it passes that distance,
 *     so it can never end up standing in the wall;
 *   - an entity that was outside is pushed to {@link #outerLimit}, just clear of the wall's
 *     outer face, but only once it is properly inside - merely touching the wall leaves it
 *     alone.
 *
 * The two thresholds are deliberately not the same distance. For an outsider the gap between
 * "pushed out" and "left alone" is hysteresis: an entity standing against the wall would
 * otherwise be teleported on every pass. For an insider there is no such gap, because letting
 * one drift into the wall is worse than correcting it every pass.
 */
public final class Barrier {

    private Barrier() {
    }

    /** Distance a captured entity is held at: just inside the wall's inner face. */
    public static double innerLimit(double radius) {
        return radius - 1.0D;
    }

    /** Distance an outsider is pushed back to: clear of the wall's outer face. */
    public static double outerLimit(double radius) {
        return radius + 0.5D;
    }

    /**
     * The distance an entity should be moved to, or a negative number when it is already on
     * the correct side of the barrier and must be left alone.
     *
     * @param distance current distance from the centre
     * @param captured true when the entity was inside the sphere when the domain opened
     * @param radius   sphere radius
     */
    public static double correctedDistance(double distance, boolean captured, double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive, got " + radius);
        }
        // exactly at the centre the "outward" direction is undefined, and there is nothing to
        // correct anyway
        if (distance < 1.0E-4D) {
            return -1.0D;
        }
        if (captured) {
            double limit = innerLimit(radius);
            return distance > limit ? limit : -1.0D;
        }
        // an outsider is only pushed once it is properly inside, not merely touching the wall
        return distance < radius - 0.5D ? outerLimit(radius) : -1.0D;
    }
}

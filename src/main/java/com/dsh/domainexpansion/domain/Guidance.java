package com.dsh.domainexpansion.domain;

/**
 * Steering for guided projectiles, as arithmetic.
 *
 * Free of Minecraft types so the behaviour can be tested directly - the numbers matter more
 * than they look. A projectile is turned a fraction of the way towards its target each tick,
 * and the obvious implementation of that is wrong in a way that is easy to miss:
 *
 * <pre>
 *   blended = motion * (1 - turn) + wanted * turn
 * </pre>
 *
 * Both inputs have the projectile's speed, and a sum of two vectors of equal length is
 * <em>shorter</em> than either unless they point the same way. At a turn of 0.3 and ninety
 * degrees between current heading and target, that blend is only 76% of the original length,
 * so the projectile would shed about a quarter of its speed every tick while turning and
 * visibly stall. Renormalising back to the original speed is what {@link #steer} does.
 */
public final class Guidance {

    private Guidance() {
    }

    /** An immutable vector, just enough of one for this calculation. */
    public record Vector(double x, double y, double z) {

        public double lengthSqr() {
            return x * x + y * y + z * z;
        }

        public double length() {
            return Math.sqrt(lengthSqr());
        }

        public Vector scale(double factor) {
            return new Vector(x * factor, y * factor, z * factor);
        }

        public Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        public Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        public Vector normalize() {
            double len = length();
            return len < 1.0E-9D ? new Vector(0, 0, 0) : scale(1.0D / len);
        }
    }

    /**
     * Turns {@code motion} a fraction of the way towards {@code aim}, keeping its speed.
     *
     * @param motion current velocity
     * @param aim    direction towards the target; its length is irrelevant
     * @param turn   how far to turn, 0 for not at all and 1 for straight onto the target
     * @return the steered velocity, at the same speed as {@code motion}
     */
    public static Vector steer(Vector motion, Vector aim, double turn) {
        if (turn < 0.0D || turn > 1.0D) {
            throw new IllegalArgumentException("turn must be between 0 and 1, got " + turn);
        }
        double speed = motion.length();
        // a projectile that has not been launched yet has no heading to correct
        if (speed < 1.0E-9D) {
            return motion;
        }
        Vector direction = aim.normalize();
        if (direction.lengthSqr() < 1.0E-18D) {
            // no target direction: leave the flight alone rather than divide by zero
            return motion;
        }

        Vector wanted = direction.scale(speed);
        Vector blended = motion.scale(1.0D - turn).add(wanted.scale(turn));
        double blendedLength = blended.length();
        if (blendedLength < 1.0E-9D) {
            // exactly opposed and turned exactly half way, so the blend cancels out.
            // nudge onto the wanted heading rather than losing the projectile.
            return wanted;
        }
        // the important line: without it the projectile loses speed on every steered tick
        return blended.normalize().scale(speed);
    }

    /** The angle between two directions, in radians. Used by the tests. */
    public static double angleBetween(Vector a, Vector b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0E-9D || lb < 1.0E-9D) {
            return 0.0D;
        }
        double cos = a.x * b.x + a.y * b.y + a.z * b.z;
        double clamped = Math.max(-1.0D, Math.min(1.0D, cos / (la * lb)));
        return Math.acos(clamped);
    }
}

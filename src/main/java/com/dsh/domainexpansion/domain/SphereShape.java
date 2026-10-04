package com.dsh.domainexpansion.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for the domain sphere, following Cursed Fate's two phase build.
 *
 * The sphere is assembled the way that mod does it, because that build is the one known
 * to work in the target pack:
 *
 *   1. OUTWARD - radius grows from 1 to the full radius. Each step lays the new annulus
 *      of the floor (one level below the centre) and the matching ring of the base plane
 *      (through the centre). The result reads as a ripple expanding from the caster.
 *   2. VERTICAL - height grows from 1 to the full radius, one horizontal slice of the
 *      sphere at a time. This is what closes the dome over the player's head.
 *
 * The centre column of the base plane is deliberately never emitted: that is where the
 * caster is standing, and a block there would trap them.
 *
 * Kept free of Minecraft types so it can be unit tested.
 */
public final class SphereShape {

    private SphereShape() {
    }

    /** A lattice offset from the domain centre. */
    public record Offset(int x, int y, int z) {
    }

    /**
     * Positions for one step of the outward phase: the annulus of the floor at
     * {@code y = -1} plus the matching ring of the base plane at {@code y = 0}.
     *
     * @param radius the current radius, starting at 1
     */
    public static List<Offset> outwardStep(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1, got " + radius);
        }
        int current = radius * radius;
        int previous = (radius - 1) * (radius - 1);
        // The centre column has distance zero, so it satisfies "within this radius" on every
        // step. It must only be laid once, on the first step; adding it every time wrote the
        // same floor block once per ring, which the duplicate-write test catches.
        boolean firstStep = radius == 1;
        List<Offset> points = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                if (d2 > current) {
                    continue;
                }
                boolean newGround = d2 > previous;
                boolean centre = x == 0 && z == 0;

                // the floor: the new annulus, plus the very centre on the first step
                if (newGround || (centre && firstStep)) {
                    points.add(new Offset(x, -1, z));
                }
                // the base plane: the new ring only, never the centre column the caster occupies
                if (newGround && !centre) {
                    points.add(new Offset(x, 0, z));
                }
            }
        }
        return points;
    }

    /**
     * Positions for one step of the vertical phase: the whole horizontal slice of the
     * sphere at height {@code y}. The lower half is not built; the floor handles support.
     *
     * @param y      the current height above the centre, starting at 1
     * @param radius the full sphere radius
     */
    public static List<Offset> verticalStep(int y, int radius) {
        if (y < 1) {
            throw new IllegalArgumentException("y must be at least 1, got " + y);
        }
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1, got " + radius);
        }
        int outer = radius * radius;
        int y2 = y * y;
        if (y2 > outer) {
            return List.of();
        }

        List<Offset> points = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + y2 + z * z <= outer) {
                    points.add(new Offset(x, y, z));
                }
            }
        }
        return points;
    }

    /**
     * True when the offset lies in the one block thick outer band of the sphere:
     * {@code (radius - 1)^2 < x^2 + y^2 + z^2 <= radius^2}. Those positions become the
     * visible wall; everything else inside becomes the invisible filler.
     */
    public static boolean isShell(int x, int y, int z, int radius) {
        int d2 = x * x + y * y + z * z;
        int outer = radius * radius;
        int inner = (radius - 1) * (radius - 1);
        return d2 > inner && d2 <= outer;
    }

    /** Number of steps in the outward phase, and of the vertical phase. */
    public static int steps(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1, got " + radius);
        }
        return radius;
    }

    /**
     * What a position of the sphere becomes.
     *
     * Kept here, and not in the entity, so the whole decision can be reasoned about and
     * tested without a running game. The entity only maps this onto block states.
     */
    public enum Placement {
        /** The visible opaque wall. */
        SHELL,
        /** The floor, one level below the centre. */
        FLOOR,
        /**
         * The invisible filler. Written only where the position is not already open space,
         * because filling existing air would be work with nothing to show for it and
         * nothing that needed recording.
         */
        FILLER
    }

    /**
     * Decides what the sphere writes at an offset from its centre.
     *
     * The order of the checks is what Cursed Fate does and it matters: the level below the
     * centre is the floor even though it also lies inside the sphere band, and only
     * positions above that can be wall.
     */
    public static Placement classify(int x, int y, int z, int radius) {
        if (y <= -1) {
            return Placement.FLOOR;
        }
        return isShell(x, y, z, radius) ? Placement.SHELL : Placement.FILLER;
    }

    /**
     * A sparse, evenly spread sample of the wall band, for drawing the boundary with
     * particles.
     *
     * This is a deliberate redundancy. The wall is real blocks and that is what makes the
     * domain a barrier, but block rendering has failed silently in this pack more than once,
     * while particles were always visible. Emitting a few particles on the same surface
     * means the boundary is legible either way: sparkle over a working wall, and the only
     * thing you can see if the wall is not drawn.
     *
     * @param radius    sphere radius
     * @param maxPoints upper bound on the returned sample size
     */
    public static List<Offset> shellSample(int radius, int maxPoints) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1, got " + radius);
        }
        if (maxPoints < 1) {
            throw new IllegalArgumentException("maxPoints must be at least 1, got " + maxPoints);
        }

        // count first so the stride gives a sample close to, but not over, maxPoints
        int total = 0;
        int outer = radius * radius;
        int inner = (radius - 1) * (radius - 1);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= radius; y++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > inner && d2 <= outer) {
                        total++;
                    }
                }
            }
        }
        if (total == 0) {
            return List.of();
        }

        // Round the stride UP. Rounding down (total / maxPoints) produces a stride small
        // enough that the sample overshoots the budget it was given, which is what the
        // budget assertion in the tests caught.
        int stride = Math.max(1, (total + maxPoints - 1) / maxPoints);
        List<Offset> sample = new ArrayList<>(Math.min(total, maxPoints) + 1);
        int seen = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= radius; y++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 <= inner || d2 > outer) {
                        continue;
                    }
                    if (seen++ % stride == 0) {
                        sample.add(new Offset(x, y, z));
                    }
                }
            }
        }
        return sample;
    }
}

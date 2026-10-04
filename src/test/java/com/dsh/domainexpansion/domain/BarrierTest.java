package com.dsh.domainexpansion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the containment arithmetic.
 *
 * The barrier is the requirement that cannot be confirmed from a log: a teleport either
 * happens or it does not, and an entity that walked through looks exactly like one that was
 * never inside. What can be proven is that the corrected distances really do put an entity on
 * the side it is supposed to be on, and that is what these tests check.
 */
class BarrierTest {

    private static final double RADIUS = 30.0;

    @Test
    @DisplayName("the limits straddle the wall band")
    void limitsStraddleTheWall() {
        double inner = Barrier.innerLimit(RADIUS);
        double outer = Barrier.outerLimit(RADIUS);

        // the wall occupies (radius-1)^2 < d^2 <= radius^2
        assertTrue(inner * inner <= (RADIUS - 1) * (RADIUS - 1) + 1e-9,
                "the inner limit " + inner + " is inside the wall band, not clear of it");
        assertTrue(outer * outer > RADIUS * RADIUS,
                "the outer limit " + outer + " is not clear of the sphere");
        assertTrue(inner < outer, "inner and outer limits are the wrong way round");
        System.out.println("=> radius " + (int) RADIUS + ": hold at " + inner + ", push out to " + outer);
    }

    @Test
    @DisplayName("a captured entity is held just inside the wall, never in it")
    void capturedIsHeldInside() {
        double limit = Barrier.innerLimit(RADIUS);

        // beyond the limit: corrected to the limit
        assertEquals(limit, Barrier.correctedDistance(RADIUS - 0.1D, true, RADIUS), 1e-9);
        assertEquals(limit, Barrier.correctedDistance(RADIUS, true, RADIUS), 1e-9);
        assertEquals(limit, Barrier.correctedDistance(RADIUS * 3, true, RADIUS), 1e-9);

        // the corrected position is not inside the wall band, so it cannot be stuck in a block
        for (double d : new double[]{RADIUS - 0.9D, RADIUS - 0.5D, RADIUS - 0.1D, RADIUS, 100.0D}) {
            double corrected = Barrier.correctedDistance(d, true, RADIUS);
            assertTrue(corrected * corrected <= (RADIUS - 1) * (RADIUS - 1) + 1e-9,
                    "a captured entity at " + d + " would be put at " + corrected + ", inside the wall");
        }
    }

    @Test
    @DisplayName("a captured entity already inside is left alone")
    void capturedInsideIsUntouched() {
        assertEquals(-1.0D, Barrier.correctedDistance(0.0D, true, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(1.0D, true, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(RADIUS - 1.0D, true, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(RADIUS - 1.5D, true, RADIUS), 1e-9);
    }

    @Test
    @DisplayName("an outsider is pushed clear of the sphere, never just into the wall")
    void outsiderIsPushedOut() {
        double limit = Barrier.outerLimit(RADIUS);

        assertEquals(limit, Barrier.correctedDistance(0.5D, false, RADIUS), 1e-9);
        assertEquals(limit, Barrier.correctedDistance(10.0D, false, RADIUS), 1e-9);
        assertEquals(limit, Barrier.correctedDistance(RADIUS - 5.0D, false, RADIUS), 1e-9);

        // and that position really is outside the sphere
        assertTrue(limit * limit > RADIUS * RADIUS, "the pushed-out position is still inside");
    }

    @Test
    @DisplayName("an outsider already outside, or merely touching the wall, is left alone")
    void outsiderOutsideIsUntouched() {
        assertEquals(-1.0D, Barrier.correctedDistance(RADIUS, false, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(RADIUS - 0.5D, false, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(RADIUS + 1.0D, false, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(1000.0D, false, RADIUS), 1e-9);
    }

    @Test
    @DisplayName("a captured entity never ends up standing in the wall")
    void capturedNeverEntersTheWall() {
        double inner = Barrier.innerLimit(RADIUS);
        double wall = (RADIUS - 1) * (RADIUS - 1);
        for (double d = inner; d <= RADIUS * 2; d += 0.05D) {
            double corrected = Barrier.correctedDistance(d, true, RADIUS);
            double settled = corrected < 0 ? d : corrected;
            assertTrue(settled * settled <= wall + 1e-9,
                    "a captured entity at " + d + " settles at " + settled + ", inside the wall");
        }
    }

    @Test
    @DisplayName("an outsider touching the wall is left alone")
    void outsiderHysteresisAtTheWall() {
        // this gap is the whole point of the asymmetric thresholds: without it an entity
        // resting against the wall would be teleported on every pass
        for (double d = RADIUS - 0.5D; d <= RADIUS + 2.0D; d += 0.05D) {
            assertEquals(-1.0D, Barrier.correctedDistance(d, false, RADIUS), 1e-9,
                    "outsider at " + d + " should sit still");
        }
    }

    @Test
    @DisplayName("the caster's own position is never a target for correction")
    void centreNeedsNoCorrection() {
        // the caster stands at the centre and is skipped by the entity before this is called;
        // at the centre the outward direction is undefined, so the arithmetic must decline
        // rather than produce a NaN or an arbitrary direction
        assertEquals(-1.0D, Barrier.correctedDistance(0.0D, true, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(0.0D, false, RADIUS), 1e-9);
        assertEquals(-1.0D, Barrier.correctedDistance(1.0E-9D, false, RADIUS), 1e-9);
    }

    @Test
    @DisplayName("invalid radius is rejected")
    void invalidRadiusRejected() {
        assertThrows(IllegalArgumentException.class, () -> Barrier.correctedDistance(5.0D, true, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> Barrier.correctedDistance(5.0D, false, -1.0D));
    }
}

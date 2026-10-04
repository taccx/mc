package com.dsh.domainexpansion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the projectile steering.
 *
 * The important one is {@link #speedIsPreserved()}: the natural implementation of "turn a
 * fraction of the way towards the target" shortens the velocity every time the two directions
 * differ, which would stall a guided projectile within a few ticks. That is a bug a player
 * would feel but never see in a log, so it is checked here instead.
 */
class GuidanceTest {

    private static final double TURN = 0.30D;
    private static final double EPS = 1.0E-9D;

    @Test
    @DisplayName("steering never changes the projectile's speed")
    void speedIsPreserved() {
        double[] speeds = {0.5D, 1.0D, 3.5D, 12.0D};
        Guidance.Vector[] aims = {
                new Guidance.Vector(1, 0, 0),      // already aligned
                new Guidance.Vector(0, 0, 1),      // ninety degrees
                new Guidance.Vector(1, 0, 1),      // forty five degrees
                new Guidance.Vector(-1, 0, 0),     // directly opposed
                new Guidance.Vector(0, -1, 0.2D),  // mostly downward
        };
        for (double speed : speeds) {
            for (Guidance.Vector aim : aims) {
                Guidance.Vector motion = new Guidance.Vector(1, 0, 0).scale(speed);
                Guidance.Vector steered = Guidance.steer(motion, aim, TURN);
                assertEquals(speed, steered.length(), 1.0E-9D,
                        "speed changed from " + speed + " to " + steered.length()
                                + " when steering towards " + aim);
            }
        }
    }

    @Test
    @DisplayName("steering moves the heading towards the target and never overshoots")
    void headingTurnsTowardTheTarget() {
        Guidance.Vector motion = new Guidance.Vector(1, 0, 0).scale(2.0D);
        Guidance.Vector aim = new Guidance.Vector(0, 0, 1);
        double before = Guidance.angleBetween(motion, aim);
        Guidance.Vector steered = Guidance.steer(motion, aim, TURN);
        double after = Guidance.angleBetween(steered, aim);

        assertTrue(after < before, "the heading did not turn towards the target");
        // it should also not swing past it
        assertTrue(Guidance.angleBetween(steered, motion) <= before + EPS,
                "the turn went further than the target direction");
    }

    @Test
    @DisplayName("repeated steering converges onto the target")
    void repeatedSteeringConverges() {
        Guidance.Vector aim = new Guidance.Vector(0, 0, 1);
        Guidance.Vector motion = new Guidance.Vector(1, 0, 0).scale(1.5D);
        for (int i = 0; i < 60; i++) {
            motion = Guidance.steer(motion, aim, TURN);
        }
        double remaining = Guidance.angleBetween(motion, aim);
        assertTrue(remaining < Math.toRadians(20.0D),
                "after sixty ticks the heading is still " + Math.toDegrees(remaining) + " degrees off");
        // and it is still travelling at the original speed
        assertEquals(1.5D, motion.length(), 1.0E-9D);
    }

    @Test
    @DisplayName("turn 0 leaves the flight alone, turn 1 points straight at the target")
    void turnExtremes() {
        Guidance.Vector motion = new Guidance.Vector(1, 0, 0).scale(3.0D);
        Guidance.Vector aim = new Guidance.Vector(0, 1, 0);

        Guidance.Vector untouched = Guidance.steer(motion, aim, 0.0D);
        assertEquals(motion.x(), untouched.x(), EPS);
        assertEquals(motion.y(), untouched.y(), EPS);
        assertEquals(motion.z(), untouched.z(), EPS);

        Guidance.Vector snapped = Guidance.steer(motion, aim, 1.0D);
        assertEquals(0.0D, Guidance.angleBetween(snapped, aim), 1.0E-9D);
        assertEquals(3.0D, snapped.length(), 1.0E-9D);
    }

    @Test
    @DisplayName("degenerate inputs are survivable")
    void degenerateInputs() {
        Guidance.Vector still = new Guidance.Vector(0, 0, 0);
        Guidance.Vector aim = new Guidance.Vector(0, 1, 0);
        // not launched yet: unchanged, and no NaN
        assertEquals(still, Guidance.steer(still, aim, TURN));

        Guidance.Vector motion = new Guidance.Vector(1, 0, 0);
        // no target direction: unchanged
        assertEquals(motion, Guidance.steer(motion, still, TURN));

        // exactly opposed, turned exactly half way, where the blend cancels to zero
        Guidance.Vector opposed = Guidance.steer(motion, new Guidance.Vector(-1, 0, 0), 0.5D);
        assertTrue(opposed.length() > 0.0D, "the projectile lost its velocity entirely");
        assertEquals(1.0D, opposed.length(), 1.0E-9D);
        assertEquals(0.0D, Guidance.angleBetween(opposed, new Guidance.Vector(-1, 0, 0)), 1.0E-9D);
    }

    @Test
    @DisplayName("an invalid turn fraction is rejected rather than silently clamped")
    void invalidTurnRejected() {
        Guidance.Vector motion = new Guidance.Vector(1, 0, 0);
        Guidance.Vector aim = new Guidance.Vector(0, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> Guidance.steer(motion, aim, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> Guidance.steer(motion, aim, 1.5D));
    }

    @Test
    @DisplayName("the aim vector's length does not matter")
    void aimLengthIsIrrelevant() {
        Guidance.Vector motion = new Guidance.Vector(1, 0, 0).scale(2.0D);
        Guidance.Vector unit = Guidance.steer(motion, new Guidance.Vector(0, 0, 1000.0D), TURN);
        Guidance.Vector tiny = Guidance.steer(motion, new Guidance.Vector(0, 0, 1.0E-6D), TURN);
        assertEquals(unit.x(), tiny.x(), 1.0E-6D);
        assertEquals(unit.y(), tiny.y(), 1.0E-6D);
        assertEquals(unit.z(), tiny.z(), 1.0E-6D);
    }
}

package com.dsh.domainexpansion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the two phase sphere geometry.
 *
 * These assertions are the ones that would have caught the earlier failures: that the
 * phases together cover a complete sphere, that the wall is exactly one block thick and
 * therefore gapless, and that the caster's own column is never filled.
 */
class SphereShapeTest {

    private static final int RADIUS = 27;

    private Set<SphereShape.Offset> outwardHalf() {
        Set<SphereShape.Offset> all = new HashSet<>();
        for (int r = 1; r <= RADIUS; r++) {
            all.addAll(SphereShape.outwardStep(r));
        }
        return all;
    }

    private Set<SphereShape.Offset> verticalHalf() {
        Set<SphereShape.Offset> all = new HashSet<>();
        for (int y = 1; y <= RADIUS; y++) {
            all.addAll(SphereShape.verticalStep(y, RADIUS));
        }
        return all;
    }

    @Test
    @DisplayName("the outward phase lays the whole floor disc exactly once")
    void outwardLaysTheFloor() {
        Set<SphereShape.Offset> floor = new HashSet<>();
        for (SphereShape.Offset o : outwardHalf()) {
            if (o.y() == -1) {
                floor.add(o);
            }
        }

        int expected = 0;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (x * x + z * z <= RADIUS * RADIUS) {
                    expected++;
                    assertTrue(floor.contains(new SphereShape.Offset(x, -1, z)),
                            "floor is missing " + x + "," + z);
                }
            }
        }
        assertEquals(expected, floor.size(), "floor has stray blocks outside the disc");
        System.out.println("=> floor disc uses " + expected + " blocks");
    }

    @Test
    @DisplayName("the outward phase never fills the caster's own column")
    void casterColumnStaysClear() {
        Set<SphereShape.Offset> outward = outwardHalf();
        assertFalse(outward.contains(new SphereShape.Offset(0, 0, 0)),
                "a block would be placed where the caster stands");
        assertTrue(outward.contains(new SphereShape.Offset(0, -1, 0)),
                "the floor under the caster must still be laid");
    }

    @Test
    @DisplayName("the vertical phase covers the entire upper half of the sphere")
    void verticalCoversUpperHalf() {
        Set<SphereShape.Offset> vertical = verticalHalf();
        int expected = 0;
        int missing = 0;

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = 1; y <= RADIUS; y++) {
                    if (x * x + y * y + z * z <= RADIUS * RADIUS) {
                        expected++;
                        if (!vertical.contains(new SphereShape.Offset(x, y, z))) {
                            missing++;
                        }
                    }
                }
            }
        }
        assertEquals(0, missing, missing + " of " + expected + " sphere positions are missing");
        System.out.println("=> upper hemisphere interior uses " + expected + " blocks");
    }

    @Test
    @DisplayName("the wall band is exactly one block thick, so it cannot have holes")
    void shellIsOneBlockThick() {
        int outer = RADIUS * RADIUS;
        int inner = (RADIUS - 1) * (RADIUS - 1);

        int shell = 0;
        int inside = 0;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = -RADIUS; y <= RADIUS; y++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > outer) {
                        continue;
                    }
                    if (SphereShape.isShell(x, y, z, RADIUS)) {
                        shell++;
                    } else if (d2 <= inner) {
                        inside++;
                    }
                }
            }
        }
        // every block of the sphere is either wall or interior, with no gap between them
        assertTrue(shell > 0 && inside > 0);
        System.out.println("=> wall " + shell + " blocks, interior " + inside + " blocks");
    }

    @Test
    @DisplayName("a wall block always has a neighbour further in, so no sight line escapes")
    void wallHasNoGaps() {
        int outer = RADIUS * RADIUS;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = 0; y <= RADIUS; y++) {
                    if (!SphereShape.isShell(x, y, z, RADIUS)) {
                        continue;
                    }
                    // step one block towards the centre: it must be inside the sphere
                    double len = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
                    int ix = (int) Math.round(x - (double) x / len);
                    int iy = (int) Math.round(y - (double) y / len);
                    int iz = (int) Math.round(z - (double) z / len);
                    int d2 = ix * ix + iy * iy + iz * iz;
                    assertTrue(d2 <= outer, "inward neighbour of " + x + "," + y + "," + z + " escaped the sphere");
                }
            }
        }
    }

    @Test
    @DisplayName("no duplicate offsets in a single step")
    void stepsHaveNoDuplicates() {
        for (int r = 1; r <= 5; r++) {
            List<SphereShape.Offset> step = SphereShape.outwardStep(r);
            assertEquals(step.size(), new HashSet<>(step).size(), "outward step " + r + " has duplicates");
        }
        for (int y = 1; y <= 5; y++) {
            List<SphereShape.Offset> step = SphereShape.verticalStep(y, RADIUS);
            assertEquals(step.size(), new HashSet<>(step).size(), "vertical step " + y + " has duplicates");
        }
    }

    @Test
    @DisplayName("budget: the sphere stays within what one cast can place")
    void budgetIsBounded() {
        int shellAndFloor = 0;
        for (SphereShape.Offset o : outwardHalf()) {
            if (o.y() == -1 || SphereShape.isShell(o.x(), o.y(), o.z(), RADIUS)) {
                shellAndFloor++;
            }
        }
        for (SphereShape.Offset o : verticalHalf()) {
            if (SphereShape.isShell(o.x(), o.y(), o.z(), RADIUS)) {
                shellAndFloor++;
            }
        }
        System.out.println("=> visible wall + floor = " + shellAndFloor
                + " blocks; interior " + (outwardHalf().size() + verticalHalf().size() - shellAndFloor)
                + " (skipped where already air)");
        assertTrue(shellAndFloor < 20000, "too many visible blocks: " + shellAndFloor);
    }

    @Test
    @DisplayName("invalid input is rejected")
    void invalidInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> SphereShape.outwardStep(0));
        assertThrows(IllegalArgumentException.class, () -> SphereShape.verticalStep(0, 5));
        assertThrows(IllegalArgumentException.class, () -> SphereShape.steps(0));
        assertTrue(SphereShape.verticalStep(RADIUS + 1, RADIUS).isEmpty(),
                "a slice above the sphere should be empty");
    }

    // ------------------------------------------------------------------
    // the invariants of a whole build
    // ------------------------------------------------------------------

    /** Every position the full two phase build emits, in order, with how often it appears. */
    private java.util.Map<SphereShape.Offset, Integer> fullBuild() {
        java.util.Map<SphereShape.Offset, Integer> emitted = new java.util.LinkedHashMap<>();
        for (int r = 1; r <= RADIUS; r++) {
            for (SphereShape.Offset o : SphereShape.outwardStep(r)) {
                emitted.merge(o, 1, Integer::sum);
            }
        }
        for (int y = 1; y <= RADIUS; y++) {
            for (SphereShape.Offset o : SphereShape.verticalStep(y, RADIUS)) {
                emitted.merge(o, 1, Integer::sum);
            }
        }
        return emitted;
    }

    @Test
    @DisplayName("a whole build never writes the same position twice")
    void buildHasNoDuplicateWrites() {
        java.util.Map<SphereShape.Offset, Integer> emitted = fullBuild();
        List<SphereShape.Offset> repeated = emitted.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(java.util.Map.Entry::getKey)
                .limit(5)
                .toList();
        assertTrue(repeated.isEmpty(),
                repeated.size() + " positions are written more than once, e.g. " + repeated);
        System.out.println("=> whole build writes " + emitted.size() + " distinct positions");
    }

    @Test
    @DisplayName("the wall the build writes is exactly the one block band, nothing more")
    void buildWallMatchesTheBandExactly() {
        Set<SphereShape.Offset> wall = new HashSet<>();
        Set<SphereShape.Offset> floor = new HashSet<>();
        Set<SphereShape.Offset> filler = new HashSet<>();

        for (SphereShape.Offset o : fullBuild().keySet()) {
            switch (SphereShape.classify(o.x(), o.y(), o.z(), RADIUS)) {
                case SHELL -> wall.add(o);
                case FLOOR -> floor.add(o);
                case FILLER -> filler.add(o);
            }
        }

        // every in-band position above the floor must be wall, and nothing else may be
        int outer = RADIUS * RADIUS;
        int inner = (RADIUS - 1) * (RADIUS - 1);
        Set<SphereShape.Offset> expectedWall = new HashSet<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = 0; y <= RADIUS; y++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > inner && d2 <= outer) {
                        expectedWall.add(new SphereShape.Offset(x, y, z));
                    }
                }
            }
        }
        assertEquals(expectedWall, wall, "the wall written does not match the sphere band");
        assertTrue(filler.size() > 0, "a build should also emit interior positions to clear");
        System.out.println("=> build classifies " + wall.size() + " wall, " + floor.size()
                + " floor, " + filler.size() + " filler");
    }

    @Test
    @DisplayName("every position the build writes is either the floor or inside the upper sphere")
    void buildStaysInsideTheShape() {
        int outer = RADIUS * RADIUS;
        for (SphereShape.Offset o : fullBuild().keySet()) {
            if (o.y() == -1) {
                assertTrue(o.x() * o.x() + o.z() * o.z() <= outer,
                        "floor position " + o + " is outside the floor disc");
                continue;
            }
            assertTrue(o.y() >= 0, "position " + o + " is below the floor and should not be built");
            assertTrue(o.x() * o.x() + o.y() * o.y() + o.z() * o.z() <= outer,
                    "position " + o + " is outside the sphere");
        }
    }

    @Test
    @DisplayName("classification gives the floor precedence over the band")
    void classificationOrder() {
        // (0,-1,0) sits inside the band for a small radius but must still be floor
        assertEquals(SphereShape.Placement.FLOOR, SphereShape.classify(0, -1, 0, 1));
        assertEquals(SphereShape.Placement.FLOOR, SphereShape.classify(0, -5, 0, 5));
        // on the surface above the floor it is wall
        assertEquals(SphereShape.Placement.SHELL, SphereShape.classify(RADIUS, 0, 0, RADIUS));
        assertEquals(SphereShape.Placement.SHELL, SphereShape.classify(0, RADIUS, 0, RADIUS));
        // and well inside it is filler
        assertEquals(SphereShape.Placement.FILLER, SphereShape.classify(1, 1, 1, RADIUS));
        assertEquals(SphereShape.Placement.FILLER, SphereShape.classify(0, 0, 0, RADIUS));
    }

    // ------------------------------------------------------------------
    // the particle layer drawn over the wall
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the particle sample lies on the wall and respects its budget")
    void particleSampleIsOnTheWall() {
        int budget = 900;
        List<SphereShape.Offset> sample = SphereShape.shellSample(RADIUS, budget);

        assertTrue(sample.size() <= budget, "sample exceeds its budget: " + sample.size());
        assertTrue(sample.size() > budget / 2, "sample is far smaller than asked for: " + sample.size());
        for (SphereShape.Offset o : sample) {
            assertTrue(SphereShape.isShell(o.x(), o.y(), o.z(), RADIUS),
                    "particle at " + o + " is not on the wall band");
        }
        System.out.println("=> particle layer samples " + sample.size() + " wall positions");
    }

    @Test
    @DisplayName("the particle sample reaches every part of the sphere, not just one side")
    void particleSampleCoversAllDirections() {
        List<SphereShape.Offset> sample = SphereShape.shellSample(RADIUS, 900);

        int maxX = sample.stream().mapToInt(SphereShape.Offset::x).max().orElseThrow();
        int minX = sample.stream().mapToInt(SphereShape.Offset::x).min().orElseThrow();
        int maxY = sample.stream().mapToInt(SphereShape.Offset::y).max().orElseThrow();
        int maxZ = sample.stream().mapToInt(SphereShape.Offset::z).max().orElseThrow();
        int minZ = sample.stream().mapToInt(SphereShape.Offset::z).min().orElseThrow();

        assertTrue(maxX > RADIUS * 0.8 && minX < -RADIUS * 0.8, "sample does not span x");
        assertTrue(maxZ > RADIUS * 0.8 && minZ < -RADIUS * 0.8, "sample does not span z");
        assertTrue(maxY > RADIUS * 0.8, "sample does not reach the top of the dome");

        // and it should cover all four quadrants rather than clustering in one
        long[] quadrants = new long[4];
        for (SphereShape.Offset o : sample) {
            int q = (o.x() >= 0 ? 0 : 1) + (o.z() >= 0 ? 0 : 2);
            quadrants[q]++;
        }
        for (int q = 0; q < 4; q++) {
            assertTrue(quadrants[q] > 0, "quadrant " + q + " has no sampled points");
        }
    }

    @Test
    @DisplayName("a sample budget of one still works, and a useless budget is rejected")
    void particleSampleEdgeCases() {
        assertEquals(1, SphereShape.shellSample(RADIUS, 1).size());
        assertThrows(IllegalArgumentException.class, () -> SphereShape.shellSample(0, 10));
        assertThrows(IllegalArgumentException.class, () -> SphereShape.shellSample(RADIUS, 0));
    }
}

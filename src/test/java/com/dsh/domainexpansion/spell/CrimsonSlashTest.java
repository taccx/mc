package com.dsh.domainexpansion.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the crimson domain's level mapping.
 *
 * The requirement was that the highest domain uses the highest Crimson Slash. The domain tops
 * out at 3 and Blood Slash at 5, so a fixed offset of two is what makes the two maxima line up:
 * domain 1 runs slash 3, domain 2 runs slash 4, domain 3 runs slash 5. That is worth a test
 * because the numbers are not obvious from either spell on its own, and because the clamp has to
 * hold if either maximum is ever changed.
 */
class CrimsonSlashTest {

    /** Blood Slash's own maximum, which the mapping must never exceed. */
    private static final int SLASH_MAX = 5;

    @Test
    @DisplayName("the highest domain runs the highest slash")
    void maximaLineUp() {
        assertEquals(SLASH_MAX, CrimsonSlash.levelAt(3, SLASH_MAX));
    }

    @Test
    @DisplayName("each domain level runs two above itself")
    void levelsRunTwoAbove() {
        assertEquals(3, CrimsonSlash.levelAt(1, SLASH_MAX));
        assertEquals(4, CrimsonSlash.levelAt(2, SLASH_MAX));
        assertEquals(5, CrimsonSlash.levelAt(3, SLASH_MAX));
    }

    @Test
    @DisplayName("the slash's maximum is never exceeded")
    void neverExceedsSlashMaximum() {
        for (int domainLevel = 1; domainLevel <= 10; domainLevel++) {
            int level = CrimsonSlash.levelAt(domainLevel, SLASH_MAX);
            assertTrue(level <= SLASH_MAX,
                    "domain level " + domainLevel + " asked for slash level " + level);
            // and it stops climbing once it is there
            assertEquals(SLASH_MAX, levelAtOrAbove(domainLevel, 3));
        }
    }

    /** The mapped level for a domain at or past its own maximum, which must be the cap. */
    private static int levelAtOrAbove(int domainLevel, int firstCappedDomainLevel) {
        return CrimsonSlash.levelAt(Math.max(domainLevel, firstCappedDomainLevel), SLASH_MAX);
    }

    @Test
    @DisplayName("a level below one still yields a usable spell level")
    void neverBelowOne() {
        assertTrue(CrimsonSlash.levelAt(-5, SLASH_MAX) >= 1);
        assertTrue(CrimsonSlash.levelAt(0, SLASH_MAX) >= 1);
        // level 0 is not a real domain level, but it must not produce level 0 or less either
        assertEquals(2, CrimsonSlash.levelAt(0, SLASH_MAX));
        assertEquals(1, CrimsonSlash.levelAt(-5, SLASH_MAX));
    }

    @Test
    @DisplayName("a lower-levelled slash spell is still respected")
    void respectsALowerMaximum() {
        // if Blood Slash's maximum were ever reduced to 3, a level 3 domain must not ask for 5
        assertEquals(3, CrimsonSlash.levelAt(3, 3));
        assertEquals(3, CrimsonSlash.levelAt(2, 3));
        assertEquals(3, CrimsonSlash.levelAt(1, 3));
    }

    @Test
    @DisplayName("the mapping is the documented bonus, not an accident of two constants")
    void bonusMatchesTheConstant() {
        for (int domainLevel = 1; domainLevel <= 3; domainLevel++) {
            assertEquals(domainLevel + CrimsonSlash.LEVEL_BONUS, CrimsonSlash.levelAt(domainLevel, SLASH_MAX));
        }
    }

    @Test
    @DisplayName("exactly a hundred strikes land in a second")
    void hundredStrikesASecond() {
        int total = 0;
        for (int tick = 0; tick < 20; tick++) {
            total += CrimsonSlash.hitsForTick(tick);
        }
        assertEquals(100, total, "a second of domain ticks did not produce a hundred strikes");
        assertEquals(100.0D, CrimsonSlash.HITS_PER_SECOND, 1.0E-9D);
    }

    @Test
    @DisplayName("the per-second allowances land exactly, and are spread rather than bunched")
    void allowancesAreSpread() {
        // thirty and fifty do not divide into twenty ticks, so both need spreading
        assertEquals(30, sumOverOneSecond(CrimsonSlash.BLOOD_PER_SECOND));
        assertEquals(50, sumOverOneSecond(CrimsonSlash.STREAKS_PER_SECOND));

        // and no tick may spend more than one over the whole-tick share, which is what "spread"
        // means: the remainder must not all land on the same tick
        for (int perSecond : new int[]{CrimsonSlash.BLOOD_PER_SECOND, CrimsonSlash.STREAKS_PER_SECOND}) {
            int everyTick = perSecond / 20;
            int max = 0;
            for (int tick = 0; tick < 20; tick++) {
                max = Math.max(max, CrimsonSlash.allocationsThisTick(perSecond, tick));
            }
            assertEquals(everyTick + 1, max,
                    perSecond + " a second bunched more than the remainder into one tick");
        }
    }

    private static int sumOverOneSecond(int perSecond) {
        int total = 0;
        for (int tick = 0; tick < 20; tick++) {
            total += CrimsonSlash.allocationsThisTick(perSecond, tick);
        }
        return total;
    }

    @Test
    @DisplayName("an allowance that divides evenly is flat")
    void evenAllowanceIsFlat() {
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(4, CrimsonSlash.allocationsThisTick(80, tick));
        }
    }
}

package com.dsh.domainexpansion.spell;

import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin streaks that cut across the inside of a domain.
 *
 * There is no line primitive to draw with - Minecraft's particles are points - so a line is
 * faked the way the game itself fakes them: each streak carries a position and a velocity, and
 * every tick it emits several particles interpolated between where it was and where it now is.
 *
 * Getting that to read as a line rather than as a dotted trail is a matter of spacing, and it
 * took a revision to get right. At 1.1 blocks a tick with three dots the gaps were 0.37 blocks
 * apart - plainly a dashed trail. Two things fix it together:
 *
 *   - eight dots a step at 2.5 blocks a tick, which is a gap of about 0.31 blocks, and
 *   - larger particles, a dust at scale 1.8 rather than the default, so each dot covers the gap
 *     rather than leaving a hole between it and the next.
 *
 * Enlarging the dots is what makes a dense line affordable: spacing them at a tenth of a block
 * instead would need three times the particles for the same look.
 *
 * Colour is chosen once per streak, not per particle. It was per particle at first, which mixed
 * white and red along a single line - not what "either white or red" means.
 */
public final class SlashStreaks {

    /**
     * Sub-positions emitted along each tick's movement.
     *
     * Fifteen, which against the speed below puts the dots 0.15 of a block apart. This is the
     * number that makes it a line: at three dots with the old speed the gaps were 0.37 blocks
     * and the result read as a dashed trail. Enlarging the particles alone was not enough - the
     * spacing is what the eye picks up - so the count went up fivefold and the cost with it.
     */
    private static final int DOTS_PER_STEP = 15;

    /** A streak lives this long; with the speed, this sets the length of the line. */
    private static final int LIFE_TICKS = 4;

    /** Blocks per tick. Roughly doubled, so the slashes cross the space rather than drift. */
    private static final double SPEED = 2.2D;

    /** Roughly one streak in three is red; the rest are white. */
    private static final double RED_CHANCE = 0.35D;

    /**
     * Both colours are dust rather than a named particle type.
     *
     * Dust takes a scale, which is why it is used here: at 1.3 the dots are wide enough to close
     * a 0.15 block gap without turning into blobs, so the line stays thin. Dust also ignores
     * gravity, which the alternative - Iron's blood droplets - does not, and a line that sags is
     * not a line. END_ROD was used for white before, but it takes no scale and so cannot be
     * widened to meet its neighbours.
     */
    private static final ParticleOptions WHITE_STREAK =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.3F);

    private static final ParticleOptions RED_STREAK =
            new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.18F), 1.3F);

    private final List<Streak> streaks = new ArrayList<>();
    private int spawned;

    private record Streak(Vec3 position, Vec3 velocity, int ticksLeft, ParticleOptions particle) {
    }

    /** Total streaks started, for the heartbeat. */
    public int spawned() {
        return spawned;
    }

    public void clear() {
        streaks.clear();
    }

    /**
     * Advances every streak by one tick, emitting as it goes.
     *
     * Spawning is no longer done here. The streaks became the slash's visible effect, so they are
     * started on the entities being struck, by {@link #spawnAt}, rather than scattered through
     * the sphere on a timer - see the crimson domain's step for the per-second allowance.
     *
     * Positions are kept inside the sphere by construction: a streak is dropped as soon as its
     * next step would leave, rather than being allowed to wander out through the wall.
     */
    public void tick(ServerLevel server, java.util.function.Predicate<Vec3> allowed) {
        if (streaks.isEmpty()) {
            return;
        }
        List<Streak> next = new ArrayList<>(streaks.size());
        for (Streak streak : streaks) {
            Vec3 from = streak.position();
            Vec3 to = from.add(streak.velocity());
            for (int i = 1; i <= DOTS_PER_STEP; i++) {
                Vec3 p = from.lerp(to, (double) i / DOTS_PER_STEP);
                server.sendParticles(streak.particle(),
                        p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            int left = streak.ticksLeft() - 1;
            if (left > 0 && allowed.test(to)) {
                next.add(new Streak(to, streak.velocity(), left, streak.particle()));
            }
        }
        streaks.clear();
        streaks.addAll(next);
    }

    /**
     * Starts one streak at a position, flying off in a random direction.
     *
     * Called on a victim of the crimson domain, so the slash reads as cutting that entity and
     * carrying on past it, rather than as something crossing the room.
     */
    public void spawnAt(ServerLevel server, Vec3 origin) {
        double yaw = server.random.nextDouble() * Math.PI * 2.0D;
        double pitch = (server.random.nextDouble() - 0.5D) * 1.2D;
        Vec3 velocity = new Vec3(
                Math.cos(pitch) * Math.cos(yaw),
                Math.sin(pitch),
                Math.cos(pitch) * Math.sin(yaw)).scale(SPEED);

        // one colour for the whole streak, decided once
        ParticleOptions particle = server.random.nextDouble() < RED_CHANCE
                ? RED_STREAK
                : WHITE_STREAK;

        streaks.add(new Streak(origin, velocity, LIFE_TICKS, particle));
        spawned++;
    }
}

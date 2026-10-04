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
 * With the speed set so those interpolated points land a fifth of a block apart, consecutive
 * dots read as one continuous slash rather than as a row of specks.
 *
 * White and red are mixed per streak. The white is {@code END_ROD}, which is bright and ignores
 * gravity, so it holds a straight line. The red is a coloured dust rather than Iron's blood
 * droplets for the same reason - droplets fall, and a line that sags is not a line.
 */
public final class SlashStreaks {

    /** Sub-positions emitted along each tick's movement. Three keeps the dots touching. */
    private static final int DOTS_PER_STEP = 3;

    /** A streak lives this long, which is what sets its length. */
    private static final int LIFE_TICKS = 8;

    /** Blocks per tick. Long enough to look like a slash, short enough to stay inside. */
    private static final double SPEED = 1.1D;

    /**
     * Emitted every tick, so the effect stays continuous without flooding the client.
     *
     * Was every other tick with a batch of two - one streak a tick. Raising it to three a tick
     * was asked for after seeing it in game: the density is the part that reads as activity, and
     * three a tick costs nine particles against the sixty the wall already spends.
     */
    private static final int SPAWN_INTERVAL_TICKS = 1;

    /** How many streaks each spawn produces. */
    private static final int BATCH = 3;

    /** Roughly one streak in three is red; the rest are white. */
    private static final double RED_CHANCE = 0.35D;

    private static final ParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.18F), 0.75F);

    private final List<Streak> streaks = new ArrayList<>();
    private int spawnCooldown;
    private int spawned;

    private record Streak(Vec3 position, Vec3 velocity, int ticksLeft) {
    }

    /** Total streaks started, for the heartbeat. */
    public int spawned() {
        return spawned;
    }

    public void clear() {
        streaks.clear();
    }

    /**
     * Advances every streak by one tick, emitting as it goes, and starts new ones on the
     * interval.
     *
     * Called once per tick while the domain is finished. Positions are kept inside the sphere by
     * construction: new streaks start at a random point within {@code radius} of the centre and
     * are dropped once they leave, rather than being allowed to wander out through the wall.
     */
    public void tick(ServerLevel server, Vec3 center, double radius,
                     java.util.function.Predicate<Vec3> allowed) {
        if (spawnCooldown-- <= 0) {
            spawnCooldown = SPAWN_INTERVAL_TICKS;
            for (int i = 0; i < BATCH; i++) {
                spawn(server, center, radius, allowed);
            }
        }

        if (streaks.isEmpty()) {
            return;
        }
        List<Streak> next = new ArrayList<>(streaks.size());
        for (Streak streak : streaks) {
            Vec3 from = streak.position();
            Vec3 to = from.add(streak.velocity());
            for (int i = 1; i <= DOTS_PER_STEP; i++) {
                Vec3 p = from.lerp(to, (double) i / DOTS_PER_STEP);
                server.sendParticles(
                        server.random.nextDouble() < RED_CHANCE ? RED_DUST : ParticleTypes.END_ROD,
                        p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            int left = streak.ticksLeft() - 1;
            // drop it as soon as it would leave the sphere, so nothing is drawn outside the wall
            if (left > 0 && allowed.test(to)) {
                next.add(new Streak(to, streak.velocity(), left));
            }
        }
        streaks.clear();
        streaks.addAll(next);
    }

    private void spawn(ServerLevel server, Vec3 center, double radius,
                       java.util.function.Predicate<Vec3> allowed) {
        // start anywhere in the sphere, biased towards the outer half so the slashes are seen
        // against the wall rather than only in the middle
        double r = radius * (0.35D + 0.6D * Math.sqrt(server.random.nextDouble()));
        double theta = server.random.nextDouble() * Math.PI * 2.0D;
        double phi = Math.acos(2.0D * server.random.nextDouble() - 1.0D);
        Vec3 start = center.add(
                r * Math.sin(phi) * Math.cos(theta),
                r * Math.cos(phi) * 0.85D,
                r * Math.sin(phi) * Math.sin(theta));
        if (!allowed.test(start)) {
            return;
        }

        // a random direction, which is what makes them read as slashes crossing the space
        double yaw = server.random.nextDouble() * Math.PI * 2.0D;
        double pitch = (server.random.nextDouble() - 0.5D) * 1.2D;
        Vec3 velocity = new Vec3(
                Math.cos(pitch) * Math.cos(yaw),
                Math.sin(pitch),
                Math.cos(pitch) * Math.sin(yaw)).scale(SPEED);

        streaks.add(new Streak(start, velocity, LIFE_TICKS));
        spawned++;
    }
}

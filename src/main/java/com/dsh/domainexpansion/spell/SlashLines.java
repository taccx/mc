package com.dsh.domainexpansion.spell;

import com.dsh.domainexpansion.entity.SlashLineEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns the slash lines: long textured streaks crossing the domain.
 *
 * These replaced a particle implementation of the same idea, and the replacement is the point
 * rather than a detail. Drawing a line out of particles needs five of them to the block for the
 * gaps to close, so each line cost sixty particles over its life and every additional line made
 * the others look worse - the spacing is what the eye reads. One entity with one texture is a
 * line of any length for the cost of a single quad, so the screen can be full of them.
 *
 * Both the entities and their rendering are ours - see {@link SlashLineEntity} and the client
 * renderer - because a red line was wanted as well as a white one, and a borrowed slash visual
 * comes with borrowed colours.
 */
public final class SlashLines {

    /**
     * How many lines may exist at once.
     *
     * The line is the effect that fills the screen, so this is deliberately generous: at eighty,
     * with a life of eight ticks, up to two hundred a second can be started. Past the ceiling new
     * lines are dropped and counted.
     */
    public static final int MAX_LIVE_LINES = 70;

    /** The two colours, as the entity's variant. */
    public static final int WHITE = 0;
    public static final int RED = 1;

    /**
     * Roughly one line in three is red.
     *
     * The reference that prompted this was mostly white with red among it, and a solid red field
     * reads as a wall rather than as slashes.
     */
    private static final float RED_CHANCE = 0.35F;

    /**
     * Line lengths, in blocks.
     *
     * Lengthened along with the thickness. A 1.5 block thick line only four blocks long is a
     * stub, not a slash - at this weight the lines need to be long enough to read as streaks
     * crossing the sphere, which is also what the reference does.
     */
    private static final float MIN_LENGTH = 20.0F;
    private static final float MAX_LENGTH = 45.0F;

    private SlashLines() {
    }

    /**
     * Starts one line at a position, pointing in a random direction.
     *
     * The rotation is set on the entity because that is what the renderer orients the quad by -
     * yaw and pitch, exactly as an arrow would be aimed. Pitch is kept shallow: a line angled
     * straight up or down is seen edge-on and all but disappears.
     */
    public static SlashLineEntity spawn(ServerLevel server, Vec3 origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        SlashLineEntity line = new SlashLineEntity(server);
        line.setVariant(random.nextFloat() < RED_CHANCE ? RED : WHITE);
        line.setLength(MIN_LENGTH + random.nextFloat() * (MAX_LENGTH - MIN_LENGTH));
        line.moveTo(origin.x, origin.y, origin.z);
        line.setYRot(random.nextFloat() * 360.0F);
        line.setXRot((random.nextFloat() - 0.5F) * 70.0F);
        line.yRotO = line.getYRot();
        line.xRotO = line.getXRot();
        server.addFreshEntity(line);
        return line;
    }
}

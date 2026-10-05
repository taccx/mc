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
    public static final int MAX_LIVE_LINES = 130;

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
     * Mark size per block of distance from the eye.
     *
     * 0.28, which holds a mark at about a hundred and twenty pixels on a 1500 pixel wide screen at
     * the 121 degree horizontal field of view a 90 degree setting gives. The arithmetic: an object
     * of size s at distance d covers s/d/tan(hfov/2) of the half screen.
     *
     * Sized from the distance rather than fixed, and that is the whole answer to two failed
     * attempts. A fixed size cannot work across a sphere of radius thirty: the reference's own
     * value of 0.64 blocks is invisible at thirty blocks away, and anything big enough to see out
     * there is two fifths of the screen up close, which is what made earlier builds read as giant
     * arcs. Scaling with distance keeps every mark the same size on screen at every distance.
     *
     * The field of view is the one assumption, and it is a client setting the server is never
     * told. A player on a much wider setting sees the marks somewhat smaller than intended, not
     * wrong - which is why this is a ratio rather than a table of distances.
     */
    private static final float SIZE_PER_DISTANCE = 0.28F;

    /**
     * Bounds on the result of that, in blocks.
     *
     * The lower bound keeps a very close mark from shrinking to nothing; the upper stops a mark
     * near the far wall growing large enough to cut through it.
     */
    private static final float MIN_SIZE = 0.5F;
    private static final float MAX_SIZE = 5.0F;

    private SlashLines() {
    }

    /**
     * Starts one mark at a position, sized for how far it is from the eye that will see it.
     *
     * The rotation is set on the entity because that is what the renderer orients the quad by -
     * yaw and pitch, exactly as an arrow would be aimed. Pitch is kept shallow: a mark angled
     * straight up or down is seen edge-on and all but disappears.
     *
     * @param distanceFromEye how far the spawn point is from the viewer, in blocks; see
     *                        {@link #SIZE_PER_DISTANCE}
     */
    public static SlashLineEntity spawn(ServerLevel server, Vec3 origin, double distanceFromEye) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        SlashLineEntity line = new SlashLineEntity(server);
        line.setVariant(random.nextFloat() < RED_CHANCE ? RED : WHITE);
        line.setSize(MIN_SIZE + Math.min(MAX_SIZE - MIN_SIZE,
                Math.max(0.0F, (float) (distanceFromEye * SIZE_PER_DISTANCE) - MIN_SIZE)));
        line.moveTo(origin.x, origin.y, origin.z);
        line.setYRot(random.nextFloat() * 360.0F);
        line.setXRot((random.nextFloat() - 0.5F) * 70.0F);
        line.yRotO = line.getYRot();
        line.xRotO = line.getXRot();
        server.addFreshEntity(line);
        return line;
    }
}

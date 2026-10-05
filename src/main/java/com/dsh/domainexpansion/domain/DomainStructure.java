package com.dsh.domainexpansion.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The block structures a domain puts up: at present, a torii gate.
 *
 * Blocks after all. The first plan was to model the gates as entities on the grounds that blocks
 * cannot lean - which is wrong. A block cannot be rotated, but a structure built from blocks can
 * lean perfectly well by offsetting each layer sideways as it goes up, and the reference this was
 * asked to follow does exactly that: solid block torii, each one leaning, the lean made of the
 * stair steps between layers.
 *
 * Which makes blocks the better choice here for a reason beyond looks: block positions can be
 * calculated exactly, so what comes out is known rather than hoped for. A model would have had to
 * be built blind and corrected over several rounds in game.
 *
 * The lean is a shear. Every point is offset in proportion to its height:
 * <pre>
 *   x' = x + shearX * y
 *   z' = z + shearZ * y
 * </pre>
 * which is what makes the whole gate lean as one piece - the pillars drift as they rise and the
 * beams, being horizontal rows at a height, tilt with them. Offsetting only the pillars instead
 * would have left the beams level and the corners broken.
 */
public final class DomainStructure {

    /** Height of a torii in blocks, footings included. */
    public static final int TORII_HEIGHT = 13;

    private DomainStructure() {
    }

    /** One block of a structure, in world space. */
    public record Piece(BlockPos pos, BlockState state) {
    }

    /**
     * Builds a torii gate leaning by the given shear, centred on the given position.
     *
     * @param origin the centre of the gate's footprint, at the level of its feet
     * @param shearX how far the top leans along x, in blocks, over the gate's full height
     * @param shearZ the same along z
     */
    public static List<Piece> torii(BlockPos origin, double shearX, double shearZ) {
        List<Piece> pieces = new ArrayList<>();

        BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();

        int halfSpan = 4;              // pillars sit four blocks either side of centre
        int pillarHeight = TORII_HEIGHT;

        // --- pillars, two blocks thick so they have some weight. The second block is taken
        // inward-to-outward from the centre line on each side, so the pair is symmetric: at -4
        // the two are -4 and -5, at +4 they are 4 and 5.
        for (int side = -1; side <= 1; side += 2) {
            for (int thick = 0; thick != side * 2; thick += side) {
                for (int y = 0; y < pillarHeight; y++) {
                    int x = side * halfSpan + thick;
                    // mossy up to knee height, stone above: a gate that has been standing a while
                    BlockState state = y < 3 ? mossy : stone;
                    pieces.add(sheared(origin, x, y, 0, shearX, shearZ, state));
                }
            }
        }

        // --- footings, one block wider than the pillars and set into the ground
        for (int side = -1; side <= 1; side += 2) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    pieces.add(sheared(origin, side * halfSpan + dx, 0, dz, shearX, shearZ, mossy));
                }
            }
        }

        // --- the lower beam, running the full width so it meets both pillars
        int beamY = 9;
        for (int x = -halfSpan - 1; x <= halfSpan + 1; x++) {
            pieces.add(sheared(origin, x, beamY, 0, shearX, shearZ, stone));
            pieces.add(sheared(origin, x, beamY + 1, 0, shearX, shearZ, stone));
        }

        // --- the top lintel, wider than everything, and one block proud at each end
        int lintelY = pillarHeight;
        for (int x = -halfSpan - 2; x <= halfSpan + 2; x++) {
            pieces.add(sheared(origin, x, lintelY, 0, shearX, shearZ, cracked));
            // a second row, set back a block, which is the shadow line under the lintel
            pieces.add(sheared(origin, x, lintelY - 1, 0, shearX, shearZ, stone));
        }
        // and the ends lift a block, the way a torii's do
        for (int side = -1; side <= 1; side += 2) {
            pieces.add(sheared(origin, side * (halfSpan + 2), lintelY + 1, 0, shearX, shearZ, cracked));
        }

        // --- the plaque, a small stack between the beams
        for (int y = beamY + 2; y < lintelY - 1; y++) {
            pieces.add(sheared(origin, 0, y, 0, shearX, shearZ, mossy));
        }

        return pieces;
    }

    /**
     * Applies the shear and rounds to a block position.
     *
     * Rounding rather than flooring, so a small lean of half a block still moves the top row by
     * one rather than by nothing, and the step is even all the way up.
     */
    private static Piece sheared(BlockPos origin, int x, int y, int z,
                                 double shearX, double shearZ, BlockState state) {
        int sx = (int) Math.round(x + shearX * y);
        int sz = (int) Math.round(z + shearZ * y);
        return new Piece(origin.offset(sx, y, sz), state);
    }
}

package com.dsh.domainexpansion.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The block structures a domain puts up: a torii gate for an aqua domain, a ram skull for a
 * crimson one.
 *
 * Blocks rather than models, and the skull is the second attempt at it. It was an entity with a
 * model first, on the reasoning that a skull is organic and boxes would make a poor one - and
 * after all of that it did not render, for reasons that could not be established without another
 * round in game. Blocks have the property that matters more here: their positions can be computed,
 * so the result is known before it is placed, and the same arithmetic can draw a picture of it to
 * check. A slightly coarse skull that exists beats a well shaped one that does not.
 *
 * Two things make a block structure lean, and both are used:
 * <ul>
 *   <li>a shear, offsetting every point in proportion to its height, which leans the whole thing
 *       as one piece rather than only moving the pillars;</li>
 *   <li>a yaw, rotating the local footprint, which is the only way a torii can face - a block
 *       cannot be rotated, but the arrangement of them can.</li>
 * </ul>
 */
public final class DomainStructure {

    /** Height of a torii in blocks, footings included. */
    public static final int TORII_HEIGHT = 18;

    private DomainStructure() {
    }

    /** One block of a structure, in world space. */
    public record Piece(BlockPos pos, BlockState state) {
    }

    /**
     * Builds a torii gate.
     *
     * @param origin the centre of the gate's footprint, at the level of its feet
     * @param shearX how far the top leans along x, in blocks per block of height
     * @param shearZ the same along z
     * @param yawDeg the direction the gate faces, in degrees
     */
    public static List<Piece> torii(BlockPos origin, double shearX, double shearZ, float yawDeg) {
        List<Piece> pieces = new ArrayList<>();

        // Timber and vermilion, which is what a torii is made of. The first version was mossy
        // cobblestone and plain stone, and it read as a ruin rather than a gate - the schematics
        // found online are all far too large to use, but their material lists are the useful part:
        // pale timber for the columns, a painted red beam, dark capping, stone underfoot.
        BlockState timber = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
        BlockState timberAlt = Blocks.BIRCH_PLANKS.defaultBlockState();
        BlockState vermilion = Blocks.RED_TERRACOTTA.defaultBlockState();
        BlockState vermilionAlt = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        BlockState capping = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState mossCarpet = Blocks.MOSS_CARPET.defaultBlockState();

        double yaw = Math.toRadians(yawDeg);

        // Three rather than four, and eighteen tall rather than thirteen: a torii is taller than it
        // is wide, and the first numbers made one that was wider than it was tall.
        int halfSpan = 3;
        int pillarHeight = TORII_HEIGHT;
        int beamY = 13;

        // --- pillars, two by two, so they have weight from every side
        for (int side = -1; side <= 1; side += 2) {
            for (int dx = 0; dx != side * 2; dx += side) {
                for (int dz = 0; dz != side * 2; dz += side) {
                    for (int y = 0; y < pillarHeight; y++) {
                        // timber all the way up, with a painted band at the height the beam passes
                        // through - which is how a real one is painted
                        BlockState state = (y >= beamY - 1 && y <= beamY + 2)
                                ? vermilionAlt : timber;
                        add(pieces, origin, yaw, shearX, shearZ,
                                side * halfSpan + dx, y, dz, state);
                    }
                }
            }
        }

        // --- footings: one wider than the pillar, in stone
        for (int side = -1; side <= 1; side += 2) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    add(pieces, origin, yaw, shearX, shearZ, side * halfSpan + dx, 0, dz, stone);
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                        add(pieces, origin, yaw, shearX, shearZ,
                                side * halfSpan + dx, 1, dz, mossy);
                    }
                }
            }
        }

        // --- the lower beam, painted, running through the pillars and proud at each end
        for (int x = -halfSpan - 2; x <= halfSpan + 2; x++) {
            add(pieces, origin, yaw, shearX, shearZ, x, beamY, 0, vermilion);
            add(pieces, origin, yaw, shearX, shearZ, x, beamY, 1, vermilion);
            add(pieces, origin, yaw, shearX, shearZ, x, beamY + 1, 0, timberAlt);
            add(pieces, origin, yaw, shearX, shearZ, x, beamY + 1, 1, timberAlt);
        }

        // --- the upper beam and the lintel above it, each wider than the last, and the ends
        // stepping up: a torii's lintel curves upward at both ends, and in blocks a curve is a
        // staircase
        int upperY = pillarHeight - 1;
        int lintelY = pillarHeight;
        for (int x = -halfSpan - 2; x <= halfSpan + 2; x++) {
            add(pieces, origin, yaw, shearX, shearZ, x, upperY, 0, vermilion);
            add(pieces, origin, yaw, shearX, shearZ, x, upperY, 1, vermilion);
        }
        for (int x = -halfSpan - 4; x <= halfSpan + 4; x++) {
            // how far this column is from the middle, which is how high its end lifts
            int fromEnd = (halfSpan + 4) - Math.abs(x);
            // the capping is dark and slightly wider than everything below it, which is what gives
            // a torii its silhouette
            add(pieces, origin, yaw, shearX, shearZ, x, lintelY, -1, capping);
            add(pieces, origin, yaw, shearX, shearZ, x, lintelY, 0, capping);
            add(pieces, origin, yaw, shearX, shearZ, x, lintelY, 1, capping);
            add(pieces, origin, yaw, shearX, shearZ, x, lintelY, 2, capping);
            if (fromEnd >= 2) {
                add(pieces, origin, yaw, shearX, shearZ, x, lintelY + 1, 0, capping);
                add(pieces, origin, yaw, shearX, shearZ, x, lintelY + 1, 1, capping);
            }
            if (fromEnd >= 5) {
                add(pieces, origin, yaw, shearX, shearZ, x, lintelY + 2, 0, mossCarpet);
                add(pieces, origin, yaw, shearX, shearZ, x, lintelY + 2, 1, mossCarpet);
            }
        }

        // --- the plaque, hanging between the beams
        for (int y = beamY + 2; y < upperY; y++) {
            add(pieces, origin, yaw, shearX, shearZ, 0, y, 0, timberAlt);
            add(pieces, origin, yaw, shearX, shearZ, 0, y, 1, timberAlt);
            if (y == beamY + 2) {
                add(pieces, origin, yaw, shearX, shearZ, 0, y, -1, vermilion);
            }
        }

        return pieces;
    }

    /**
     * Builds a ram skull.
     *
     * Roughly seven blocks long and five high, lying on the floor. The first version was three
     * blocks and read as a lump - the fault was proportion more than detail: a sheep's skull is
     * long, and almost all of its length is muzzle. So there is a short cranium at the back and a
     * long tapering snout in front of it, and what makes it a ram rather than a sheep is the pair
     * of heavy horns.
     *
     * The horns are the whole job. A ram's curl is a spiral: out from the skull, up, back, and then
     * down and forward again to close. A chain of blocks stepping in all three axes at once is the
     * only way to say that in blocks, so each horn is fourteen segments stepping out, then up, then
     * back, then down and in - which from the side reads as a coil.
     */
    public static List<Piece> ramSkull(BlockPos origin, float yawDeg) {
        List<Piece> pieces = new ArrayList<>();

        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState horn = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();

        double yaw = Math.toRadians(yawDeg);

        // --- cranium: five wide, four deep, three high, sitting on the floor
        for (int x = -1; x <= 1; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    add(pieces, origin, yaw, 0.0D, 0.0D, x, y, z, bone);
                }
            }
        }

        // --- snout: three wide at the base, narrowing to one, and long - most of a sheep's skull
        for (int z = 3; z <= 5; z++) {
            int width = z <= 4 ? 1 : 0;
            for (int x = -width; x <= width; x++) {
                for (int y = 0; y <= (z <= 4 ? 1 : 0); y++) {
                    add(pieces, origin, yaw, 0.0D, 0.0D, x, y, z, bone);
                }
            }
        }

        // --- the nasal opening, dark, at the very tip
        add(pieces, origin, yaw, 0.0D, 0.0D, 0, 1, 5, dark);

        // a brow ridge across the top of the cranium. This is what stops the whole thing reading
        // as a slab: a skull is tall at the back and long and low at the front, and with the
        // cranium only as tall as the snout the two merged into one flat block
        for (int x = -1; x <= 1; x++) {
            add(pieces, origin, yaw, 0.0D, 0.0D, x, 3, 2, bone);
        }

        // --- eye sockets, recessed into the cranium's front corners, with a brow above each
        for (int side = -1; side <= 1; side += 2) {
            add(pieces, origin, yaw, 0.0D, 0.0D, side, 1, 3, dark);
        }

        // --- horns: a spiral each side, fourteen segments. Out, up, back, then down and forward,
        // which is the shape that says ram rather than sheep.
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 2;
            int y = 2;
            int z = 1;
            for (int segment = 0; segment < 9; segment++) {
                add(pieces, origin, yaw, 0.0D, 0.0D, x, y, z, horn);
                if (segment < 2) {
                    x += side;              // out
                    y += 1;
                } else if (segment < 4) {
                    z += 1;                 // back
                    y += 1;
                } else if (segment < 6) {
                    z += 1;                 // and back down
                    y -= 1;
                } else {
                    x -= side;              // in, closing the coil
                    z += 1;
                }
            }
        }

        return pieces;
    }

    /**
     * Adds one block, after the shear and then the yaw.
     *
     * The shear is applied in the structure's own frame, so a leaning gate that has been turned
     * still leans the way it was built to lean rather than the way the world's axes run.
     */
    private static void add(List<Piece> pieces, BlockPos origin, double yaw,
                            double shearX, double shearZ, int x, int y, int z, BlockState state) {
        double sx = x + shearX * y;
        double sz = z + shearZ * y;
        double rx = sx * Math.cos(yaw) - sz * Math.sin(yaw);
        double rz = sx * Math.sin(yaw) + sz * Math.cos(yaw);
        pieces.add(new Piece(origin.offset((int) Math.round(rx), y, (int) Math.round(rz)), state));
    }
}

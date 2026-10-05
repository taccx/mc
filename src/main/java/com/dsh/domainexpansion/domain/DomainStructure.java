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

        BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState mossySlab = Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState mossCarpet = Blocks.MOSS_CARPET.defaultBlockState();

        double yaw = Math.toRadians(yawDeg);

        // Three rather than four, and eighteen tall rather than thirteen: a torii is taller than it
        // is wide, and the first numbers made one that was wider than it was tall.
        int halfSpan = 3;
        int pillarHeight = TORII_HEIGHT;

        // --- pillars, two by two, so they have weight from every side
        for (int side = -1; side <= 1; side += 2) {
            for (int dx = 0; dx != side * 2; dx += side) {
                for (int dz = 0; dz != side * 2; dz += side) {
                    for (int y = 0; y < pillarHeight; y++) {
                        // mossy most of the way up with stone showing through: a gate that has been
                        // standing a while rather than a clean new one
                        BlockState state = switch (y % 5) {
                            case 0, 1, 2 -> mossy;
                            case 3 -> stone;
                            default -> bricks;
                        };
                        if (y < 2) {
                            state = mossy;
                        }
                        add(pieces, origin, yaw, shearX, shearZ,
                                side * halfSpan + dx, y, dz, state);
                    }
                }
            }
        }

        // --- footings: one wider than the pillar, with a mossy slab course above the ground
        for (int side = -1; side <= 1; side += 2) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    add(pieces, origin, yaw, shearX, shearZ, side * halfSpan + dx, 0, dz, mossy);
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                        add(pieces, origin, yaw, shearX, shearZ,
                                side * halfSpan + dx, 1, dz, mossySlab);
                    }
                }
            }
        }

        // --- the lower beam, running through the pillars and one block proud at each end
        int beamY = 13;
        for (int x = -halfSpan - 2; x <= halfSpan + 2; x++) {
            add(pieces, origin, yaw, shearX, shearZ, x, beamY, 0, bricks);
            add(pieces, origin, yaw, shearX, shearZ, x, beamY + 1, 0, stone);
        }

        // --- the upper beam and the lintel above it, each wider than the last, and the ends
        // stepping up: a torii's lintel curves upward at both ends, and in blocks a curve is a
        // staircase
        int upperY = pillarHeight - 1;
        int lintelY = pillarHeight;
        for (int x = -halfSpan - 2; x <= halfSpan + 2; x++) {
            add(pieces, origin, yaw, shearX, shearZ, x, upperY, 0, stone);
        }
        for (int x = -halfSpan - 4; x <= halfSpan + 4; x++) {
            // how far this column is from the middle, which is how high its end lifts
            int fromEnd = (halfSpan + 4) - Math.abs(x);
            add(pieces, origin, yaw, shearX, shearZ, x, lintelY, 0, cracked);
            if (fromEnd >= 2) {
                add(pieces, origin, yaw, shearX, shearZ, x, lintelY + 1, 0, cracked);
            }
            if (fromEnd >= 5) {
                add(pieces, origin, yaw, shearX, shearZ, x, lintelY + 2, 0, mossCarpet);
            }
        }

        // --- the plaque, a short stack between the beams
        for (int y = beamY + 2; y < upperY; y++) {
            add(pieces, origin, yaw, shearX, shearZ, 0, y, 0, mossy);
        }

        return pieces;
    }

    /**
     * Builds a ram skull.
     *
     * Small, about three blocks, sitting on the floor. The shape that says "ram" is the horns: two
     * curls sweeping out and back, each a chain of blocks stepping outward, upward and back so it
     * reads as a spiral from the side. The eye sockets are the only dark blocks, which is what
     * makes a pair of flat rectangles read as sockets.
     */
    public static List<Piece> ramSkull(BlockPos origin, float yawDeg) {
        List<Piece> pieces = new ArrayList<>();

        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState socket = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

        double yaw = Math.toRadians(yawDeg);

        // --- cranium, three by two by three, its base on the floor
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 1; y++) {
                    add(pieces, origin, yaw, 0.0D, 0.0D, x, y, z, bone);
                }
            }
        }

        // --- muzzle, stepped forward and narrower
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                add(pieces, origin, yaw, 0.0D, 0.0D, x, y, 2, bone);
            }
        }
        add(pieces, origin, yaw, 0.0D, 0.0D, 0, 0, 3, bone);

        // --- eye sockets, set into the upper front corners
        for (int x = -1; x <= 1; x += 2) {
            add(pieces, origin, yaw, 0.0D, 0.0D, x, 1, 2, socket);
        }

        // --- horns: one chain each side, stepping out once and then curling up and back. Out once
        // only: stepping out on every segment made a pair of horns eleven blocks across and five
        // tall, which is a pair of wings rather than a ram.
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 2;
            int y = 1;
            int z = 0;
            for (int segment = 0; segment < 6; segment++) {
                add(pieces, origin, yaw, 0.0D, 0.0D, x, y, z, bone);
                if (segment == 0) {
                    x += side;
                    y += 1;
                    z -= 1;
                } else if (segment < 3) {
                    y += 1;
                    z -= 1;
                } else if (segment == 3) {
                    // the curl turns back inward as it closes
                    x -= side;
                    z -= 1;
                } else {
                    z -= 1;
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

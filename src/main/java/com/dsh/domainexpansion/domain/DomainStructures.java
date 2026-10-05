package com.dsh.domainexpansion.domain;

import com.dsh.domainexpansion.DomainExpansion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The structures the domains put up, read from the saves they were built in.
 *
 * Both were built by hand in creative worlds rather than described, so the shapes come from the
 * world files: the region format is read directly and every non-ground block is written out as an
 * offset from the structure's own corner. This class reads that file back and turns it into blocks
 * to place.
 *
 * Two things worth being explicit about.
 *
 * Block states are not carried over. The extractor recorded block ids, so a stair comes back as a
 * stair facing whatever its default is rather than the way it was turned while building, and the
 * same for trapdoors and slabs. Placing them is still right; a few of them face the wrong way.
 *
 * Rotation is applied here rather than baked in, because the whole point of reading the layout
 * instead of hard coding it is that one gate can stand in the middle of a domain and a dozen
 * gallows can stand at a dozen different angles.
 */
public final class DomainStructures {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final String PATH = "/data/domain_expansion/structures/structures.json";

    /** Loaded once, on first use. The file never changes while the game is running. */
    private static Map<String, List<int[]>> blocks;
    private static Map<String, String[]> names;

    private DomainStructures() {
    }

    /** A structure's blocks, as offsets from its own corner, rotated by the given yaw. */
    public static List<DomainStructure.Piece> build(String key, BlockPos origin, float yawDeg) {
        load();
        List<int[]> offsets = blocks.get(key);
        String[] ids = names.get(key);
        if (offsets == null || ids == null) {
            return List.of();
        }

        // Centre the structure on the given point rather than hanging it off its own corner.
        // The extractor wrote offsets from the bounding box's minimum corner, so placing at a
        // position put the CORNER there - which is why a twenty block gate asked to stand in the
        // middle of a domain stood half of itself to one side of it. Offsetting by half the
        // footprint first is the fix, and it applies to any structure rather than just this one.
        int[] size = sizeOf(key);
        double cx = (size[0] - 1) / 2.0D;
        double cz = (size[2] - 1) / 2.0D;

        double yaw = Math.toRadians(yawDeg);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        List<DomainStructure.Piece> out = new ArrayList<>(offsets.size());
        BlockState fallback = Blocks.STONE.defaultBlockState();

        for (int i = 0; i < offsets.size(); i++) {
            int[] o = offsets.get(i);
            double px = o[0] - cx;
            double pz = o[2] - cz;
            double rx = px * cos - pz * sin;
            double rz = px * sin + pz * cos;
            BlockState state = stateOf(ids[i], fallback);
            out.add(new DomainStructure.Piece(
                    origin.offset((int) Math.round(rx), o[1], (int) Math.round(rz)), state));
        }
        return out;
    }

    /** How large the structure is, so a caller can tell whether it will fit. */
    public static int[] sizeOf(String key) {
        load();
        List<int[]> offsets = blocks.get(key);
        if (offsets == null || offsets.isEmpty()) {
            return new int[]{0, 0, 0};
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE;
        int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] o : offsets) {
            x0 = Math.min(x0, o[0]);
            x1 = Math.max(x1, o[0]);
            y0 = Math.min(y0, o[1]);
            y1 = Math.max(y1, o[1]);
            z0 = Math.min(z0, o[2]);
            z1 = Math.max(z1, o[2]);
        }
        return new int[]{x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1};
    }

    private static BlockState stateOf(String id, BlockState fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        if (block == null || block == Blocks.AIR) {
            return fallback;
        }
        return block.defaultBlockState();
    }

    private static void load() {
        if (blocks != null) {
            return;
        }
        blocks = new HashMap<>();
        names = new HashMap<>();

        try (InputStream in = DomainExpansion.class.getResourceAsStream(PATH)) {
            if (in == null) {
                LOGGER.error("[DomainExpansion] {} is missing from the jar; "
                        + "the domains will have no scenery", PATH);
                return;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : root.keySet()) {
                JsonElement el = root.get(key);
                if (el.isJsonNull()) {
                    continue;
                }
                JsonArray arr = el.getAsJsonObject().getAsJsonArray("blocks");
                List<int[]> offsets = new ArrayList<>(arr.size());
                String[] ids = new String[arr.size()];
                int i = 0;
                for (JsonElement b : arr) {
                    JsonArray t = b.getAsJsonArray();
                    offsets.add(new int[]{t.get(0).getAsInt(), t.get(1).getAsInt(),
                            t.get(2).getAsInt()});
                    ids[i++] = t.get(3).getAsString();
                }
                blocks.put(key, offsets);
                names.put(key, ids);
                LOGGER.info("[DomainExpansion] loaded structure '{}': {} blocks",
                        key, offsets.size());
            }
        } catch (Exception e) {
            LOGGER.error("[DomainExpansion] could not read {}: {}",
                    PATH, e.toString());
        }
    }
}

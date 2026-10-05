package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.entity.DomainDecorationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The two pieces of domain scenery, built out of boxes.
 *
 * Drawn in three passes, one per material, and the materials are vanilla block textures rather
 * than anything drawn for this mod: mossy cobblestone and stone for the torii, bone block for the
 * skull, polished blackstone for the eye sockets. Nothing is copied and no texture has to be
 * shipped - the game already has them - and the result matches the blocks the player sees around
 * them instead of standing out from them.
 *
 * The model is in pixels at sixteen to the block, as Minecraft models are, and the renderer scales
 * it by a sixteenth. The torii is therefore about five and a half blocks tall, which is what the
 * entity's own dimensions say as well, so culling and the model agree.
 *
 * All geometry is cuboids. That is a real limit on how organic the skull can be - a ram's horn
 * wants to be a curve - so each horn is a chain of boxes turning as it goes, which at this size
 * reads as a curl.
 */
public class DomainDecorationModel extends Model {

    /** Vanilla textures, referenced rather than copied. */
    public static final ResourceLocation MOSS =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/mossy_cobblestone.png");
    public static final ResourceLocation STONE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png");
    public static final ResourceLocation BONE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/bone_block_side.png");
    public static final ResourceLocation DARK =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/polished_blackstone.png");

    private final ModelPart moss;
    private final ModelPart stone;
    private final ModelPart bone;
    private final ModelPart dark;

    public DomainDecorationModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.moss = root.getChild("moss");
        this.stone = root.getChild("stone");
        this.bone = root.getChild("bone");
        this.dark = root.getChild("dark");
    }

    /** One part per material, because each is drawn with its own texture. */
    public ModelPart partFor(int material) {
        return switch (material) {
            case 1 -> stone;
            case 2 -> bone;
            case 3 -> dark;
            default -> moss;
        };
    }

    public static final int MATERIAL_COUNT = 4;

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---------------------------------------------------------------- torii
        PartDefinition moss = root.addOrReplaceChild("moss", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition stone = root.addOrReplaceChild("stone", CubeListBuilder.create(), PartPose.ZERO);

        // the two pillars, six pixels square and seventy tall, set five blocks apart
        for (int side = -1; side <= 1; side += 2) {
            moss.addOrReplaceChild("pillar" + side,
                    CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -70.0F, -3.0F, 6.0F, 70.0F, 6.0F),
                    PartPose.offset(side * 20.0F, 0.0F, 0.0F));
            // a stone footing under each, so the wood does not simply stop at the floor
            stone.addOrReplaceChild("footing" + side,
                    CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 6.0F, 12.0F),
                    PartPose.offset(side * 20.0F, 0.0F, 0.0F));
        }

        // the top lintel, wider than the pillars and standing slightly proud of them
        stone.addOrReplaceChild("lintel",
                CubeListBuilder.create().texOffs(0, 0).addBox(-30.0F, -80.0F, -4.0F, 60.0F, 7.0F, 8.0F),
                PartPose.ZERO);
        // the second beam, lower and narrower
        stone.addOrReplaceChild("beam",
                CubeListBuilder.create().texOffs(0, 0).addBox(-26.0F, -62.0F, -3.0F, 52.0F, 6.0F, 6.0F),
                PartPose.ZERO);
        // the plaque between them
        moss.addOrReplaceChild("plaque",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -70.0F, -2.0F, 10.0F, 8.0F, 4.0F),
                PartPose.ZERO);

        // ---------------------------------------------------------------- ram skull
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition dark = root.addOrReplaceChild("dark", CubeListBuilder.create(), PartPose.ZERO);

        // cranium, sitting high so the horns have somewhere to be
        bone.addOrReplaceChild("cranium",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -14.0F, -7.0F, 10.0F, 9.0F, 12.0F),
                PartPose.ZERO);
        // muzzle, narrower and lower, in front
        bone.addOrReplaceChild("muzzle",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -11.0F, 5.0F, 6.0F, 5.0F, 12.0F),
                PartPose.ZERO);
        // the lower jaw, short and set back under the muzzle
        bone.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, 4.0F, 6.0F, 3.0F, 11.0F),
                PartPose.ZERO);

        // eye sockets: boxes proud of the skull rather than holes in it, which cuboids cannot make,
        // read as sockets at this size because they are the darkest thing on it
        for (int side = -1; side <= 1; side += 2) {
            dark.addOrReplaceChild("socket" + side,
                    CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -12.5F, -6.5F, 4.0F, 4.0F, 3.0F),
                    PartPose.offset(side * 4.0F, 0.0F, 0.0F));

            // A horn, as a chain of boxes stepping outward, up and back: a spiral in eight
            // segments. Each is offset from the last and turned a little further, and the whole
            // chain is built downwards in the model tree so it hangs together when the parent
            // turns.
            PartDefinition parent = bone;
            float x = side * 4.0F;
            float y = -12.0F;
            float z = -6.0F;
            float turn = side * 22.0F;
            for (int segment = 0; segment < 8; segment++) {
                float size = 3.4F - segment * 0.28F;
                PartDefinition next = parent.addOrReplaceChild("horn" + side + "_" + segment,
                        CubeListBuilder.create().texOffs(0, 0)
                                .addBox(-size / 2.0F, -size / 2.0F, -size / 2.0F,
                                        size, size, size + 1.6F),
                        PartPose.offsetAndRotation(x, y, z, 0.0F, (float) Math.toRadians(turn), 0.0F));
                parent = next;
                x = side * 3.0F;
                y = -2.6F;
                z = -1.4F;
                turn = side * 17.0F;
            }
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        // the renderer drives one material at a time and calls this with the part already posed
        throw new UnsupportedOperationException("render one material group at a time");
    }

    /**
     * Draws one material group.
     *
     * The three groups share a coordinate space, so they are all drawn through the same pose - the
     * renderer sets that up once and calls this four times, once per texture.
     */
    public void renderPart(int material, PoseStack poseStack, VertexConsumer consumer,
                           int packedLight, int packedOverlay, float red, float green, float blue,
                           float alpha) {
        partFor(material).render(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** Whether a material has anything in it for the given variant, so empty passes are skipped. */
    public boolean hasMaterialFor(int variant, int material) {
        if (variant == DomainDecorationEntity.SKULL) {
            return material == 2 || material == 3;
        }
        return material == 0 || material == 1;
    }
}

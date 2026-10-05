package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.entity.SlashLineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a slash line as two textured quads: a wide soft halo, then the line itself on top.
 *
 * The shape of this is taken from traveloptics' GyroSlashVisualRenderer, which solved the same
 * problem - a long thin streak lying along an arbitrary direction - and reading it is what
 * pointed at this approach after two attempts at drawing lines out of particles.
 *
 * Two passes rather than one is what gives the edges something to glow from. The halo is drawn
 * at well over twice the thickness and tinted per variant, so a red line has a red bleed around
 * it and white has a cold one. Both passes are emissive, which means full brightness regardless
 * of the light level and, under a shader pack, something for the bloom to pick up.
 *
 * The quad is built by hand rather than from a model: four vertices, full texture, no overlay.
 */
public class SlashLineRenderer extends EntityRenderer<SlashLineEntity> {

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(
                    "domain_expansion", "textures/entity/slash_line/slash_line_white.png");
    private static final ResourceLocation RED =
            ResourceLocation.fromNamespaceAndPath(
                    "domain_expansion", "textures/entity/slash_line/slash_line_red.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(
                    "domain_expansion", "textures/entity/slash_line/slash_line_glow.png");

    /** How much wider than the line the halo is drawn. */
    private static final float GLOW_SCALE = 1.08F;

    /**
     * The halo's opacity.
     *
     * 1.08 and 0.16 now, against 1.55 and 0.28. The wider, stronger version was added for the
     * edge glow that was asked for, and it was most of what made the line look blurry: a soft
     * band wider than the line itself, over the line, reads as a smudge rather than as a glowing
     * edge. This is a rim tight against the line instead.
     */
    private static final float GLOW_ALPHA = 0.16F;

    /**
     * How far the line is pushed in front of its own halo, in blocks.
     *
     * The two quads are otherwise exactly coplanar, which z-fights: the depth test cannot
     * separate them and the result alternates between the two, which came back from play as the
     * line looking banded - white and grey in strips, with a rim around it. Small enough not to
     * read as an offset, large enough to settle it.
     */
    private static final float CORE_OFFSET = 0.03F;

    /**
     * Frames in the mark's sprite sheet.
     *
     * Eight, one per tick of the mark's life, taken from the reference: its slash particle
     * advances one frame of an eight frame sheet per tick and that sweep is most of what makes a
     * cut read as having happened rather than as a decal sitting there.
     */
    private static final int FRAMES = 8;

    public SlashLineRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SlashLineEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float progress = entity.progress(partialTick);

        // the line sweeps out and then fades: full length by a third of the way through, gone by
        // the end. Without this a line simply appears and vanishes, which reads as a flicker.
        float grow = Math.min(1.0F, progress * 3.0F);
        // cubed rather than squared, so the mark holds most of its strength for most of its life
        // and drops away at the end instead of fading out from the moment it appears.
        float fade = 1.0F - progress * progress * progress;
        // square: the mark is two crossing cuts, so it needs a square to live in
        float half = entity.size() * 0.5F * grow;
        float alpha = Math.max(0.0F, fade);
        if (alpha <= 0.01F) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        // a fixed roll per entity, so lines crossing the view are not all edge-on
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.getId() * 37) % 360));

        boolean red = entity.variant() == 1;

        // the cut sweeps in over the first half of the sheet and holds for the second, one frame
        // per tick, which is what the reference does with its eight frame particle sprite
        int frame = Math.min(FRAMES - 1, (int) (entity.tickCount + partialTick));
        float vMin = (float) frame / FRAMES;
        float vMax = (float) (frame + 1) / FRAMES;

        // halo first, so the mark draws over it. The halo is a single frame strip, not a sheet,
        // so it always spans the full texture.
        quad(buffers.getBuffer(SlashRenderTypes.of(GLOW)),
                poseStack, half * GLOW_SCALE, half * GLOW_SCALE,
                red ? 1.0F : 0.82F, red ? 0.18F : 0.90F, red ? 0.24F : 1.0F,
                alpha * GLOW_ALPHA, 0.0F, 0.0F, 1.0F);

        // then the mark, pushed forward so the two cannot z-fight
        quad(buffers.getBuffer(SlashRenderTypes.of(red ? RED : WHITE)),
                poseStack, half, half, 1.0F, 1.0F, 1.0F, alpha, CORE_OFFSET, vMin, vMax);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** One quad, centred, spanning the given slice of the texture's height. */
    private static void quad(VertexConsumer consumer, PoseStack poseStack,
                             float halfLength, float halfThickness,
                             float red, float green, float blue, float alpha, float z,
                             float vMin, float vMax) {
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        vertex(consumer, matrix, normal, -halfLength, halfThickness, 0.0F, vMin, red, green, blue, alpha, z);
        vertex(consumer, matrix, normal, halfLength, halfThickness, 1.0F, vMin, red, green, blue, alpha, z);
        vertex(consumer, matrix, normal, halfLength, -halfThickness, 1.0F, vMax, red, green, blue, alpha, z);
        vertex(consumer, matrix, normal, -halfLength, -halfThickness, 0.0F, vMax, red, green, blue, alpha, z);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float u, float v,
                               float red, float green, float blue, float alpha, float z) {
        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SlashLineEntity entity) {
        return entity.variant() == 1 ? RED : WHITE;
    }
}

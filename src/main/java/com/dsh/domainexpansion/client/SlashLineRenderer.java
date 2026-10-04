package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.entity.SlashLineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a slash line as one textured quad, oriented by the entity's own rotation.
 *
 * The shape of this is taken from traveloptics' GyroSlashVisualRenderer, which solved the same
 * problem - a long thin streak that has to lie along an arbitrary direction. Reading it is what
 * pointed at the right approach after two attempts at drawing lines out of particles. The
 * differences are that the texture is ours, so there is a red one as well as a white, and that
 * the line fades out and stretches over its life instead of switching between animation frames.
 *
 * The quad is built by hand rather than from a model: four vertices, full texture, drawn at full
 * brightness with no overlay, in a translucent render type so the fade works.
 */
public class SlashLineRenderer extends EntityRenderer<SlashLineEntity> {

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(
                    "domain_expansion", "textures/entity/slash_line/slash_line_white.png");
    private static final ResourceLocation RED =
            ResourceLocation.fromNamespaceAndPath(
                    "domain_expansion", "textures/entity/slash_line/slash_line_red.png");

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
        float fade = 1.0F - progress * progress;
        float half = entity.length() * 0.5F * grow;
        float alpha = Math.max(0.0F, fade);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        // a fixed roll per entity, so lines crossing the view are not all edge-on
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.getId() * 37) % 360));

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture(entity)));

        float thickness = entity.getDimensions(entity.getPose()).height * 0.5F;
        vertex(consumer, matrix, normal, -half, thickness, 0.0F, 0.0F, alpha);
        vertex(consumer, matrix, normal, half, thickness, 1.0F, 0.0F, alpha);
        vertex(consumer, matrix, normal, half, -thickness, 1.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, -half, -thickness, 0.0F, 1.0F, alpha);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float u, float v, float alpha) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    private static ResourceLocation texture(SlashLineEntity entity) {
        return entity.variant() == 1 ? RED : WHITE;
    }

    @Override
    public ResourceLocation getTextureLocation(SlashLineEntity entity) {
        return texture(entity);
    }
}

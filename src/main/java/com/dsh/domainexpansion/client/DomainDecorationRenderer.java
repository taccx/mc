package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.entity.DomainDecorationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a decoration, including the part where it comes up out of the floor.
 *
 * The rise is done here rather than by moving the entity: the model is drawn lower by however much
 * of the two seconds is left, and the floor is solid and drawn with depth, so the part still below
 * it is simply hidden. That means no server-side movement, no position packets, and a rise that
 * looks the same to a client that receives the entity half way up.
 *
 * Easing on the way: a decoration rising at a constant speed reads as a lift, so the curve starts
 * quickly and settles, which reads as something being raised into place.
 */
public class DomainDecorationRenderer extends EntityRenderer<DomainDecorationEntity> {

    private final DomainDecorationModel model;

    public DomainDecorationRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DomainDecorationModel(context.bakeLayer(DomainDecorationModelLayer.LAYER));
    }

    @Override
    public void render(DomainDecorationEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float progress = Mth.clamp(age / DomainDecorationEntity.RISE_TICKS, 0.0F, 1.0F);
        // ease-out cubic: fast at first, settling at the end
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        float below = (1.0F - eased) * DomainDecorationEntity.riseFrom();

        poseStack.pushPose();
        // the lean, set when the domain placed it, so it does not change from frame to frame
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(entity.leanX));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.leanZ));
        // one sixteenth: the model is in pixels, the world is in blocks
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        poseStack.translate(0.0F, -below * 16.0F, 0.0F);

        for (int material = 0; material < DomainDecorationModel.MATERIAL_COUNT; material++) {
            if (!model.hasMaterialFor(entity.variant(), material)) {
                continue;
            }
            ModelPart part = model.partFor(material);
            var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(textureFor(material)));
            part.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static ResourceLocation textureFor(int material) {
        return switch (material) {
            case 1 -> DomainDecorationModel.STONE;
            case 2 -> DomainDecorationModel.BONE;
            case 3 -> DomainDecorationModel.DARK;
            default -> DomainDecorationModel.MOSS;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(DomainDecorationEntity entity) {
        return DomainDecorationModel.MOSS;
    }
}

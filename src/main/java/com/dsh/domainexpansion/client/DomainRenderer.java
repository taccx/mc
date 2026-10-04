package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.entity.DomainEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * A renderer that draws nothing.
 *
 * The domain entity is a server-side anchor: the visible boundary is the shell of
 * blocks, not the entity. But every entity type still needs a renderer registered on
 * the client, otherwise {@code EntityRenderDispatcher} hands back null and the game
 * crashes with a NullPointerException the moment the entity becomes visible. This
 * exists purely to satisfy that lookup.
 *
 * It cannot reuse vanilla's ThrownItemRenderer, which requires the entity to implement
 * ItemSupplier.
 */
public class DomainRenderer extends EntityRenderer<DomainEntity> {

    /** Never actually read, since render() draws nothing. */
    private static final ResourceLocation NO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("domain_expansion", "textures/misc/empty.png");

    public DomainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DomainEntity entity, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer,
                       int packedLight) {
        // intentionally empty: the domain is shown by its shell blocks
    }

    @Override
    public ResourceLocation getTextureLocation(DomainEntity entity) {
        return NO_TEXTURE;
    }
}

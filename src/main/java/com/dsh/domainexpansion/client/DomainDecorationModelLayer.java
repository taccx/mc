package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.DomainExpansion;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Where the decoration model's layer is declared.
 *
 * Entity models are baked from a layer definition, and the layer has to be registered before
 * anything asks for it - the renderer's constructor bakes it, so this runs first.
 */
public final class DomainDecorationModelLayer {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DomainExpansion.MODID, "domain_decoration"),
            "main");

    private DomainDecorationModelLayer() {
    }

    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, DomainDecorationModel::createBodyLayer);
    }
}

package com.dsh.domainexpansion.client;

import com.dsh.domainexpansion.DomainExpansion;
import com.dsh.domainexpansion.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only wiring.
 *
 * Registering the domain renderer is not optional: without it, the first time the
 * entity is sent to a client the dispatcher returns a null renderer and the game
 * crashes in LevelRenderer. That is exactly what happened on the first in-game test.
 *
 * The class is guarded to Dist.CLIENT so the dedicated server never loads it.
 */
@Mod.EventBusSubscriber(modid = DomainExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DOMAIN.get(), DomainRenderer::new);
        event.registerEntityRenderer(ModEntities.SLASH_LINE.get(), SlashLineRenderer::new);
    }
}

package com.dsh.domainexpansion.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A render type for the slash lines with mipmapping and blur switched off.
 *
 * This is the fix for the blur that survived several rounds of texture work. Mipmapping averages
 * a texture down into smaller copies for distant and angled surfaces, and a thin bright line on a
 * transparent background is exactly the case it destroys: every level mixes the line with the
 * empty pixels either side, so what arrives on screen is a soft copy of a sharp original. The
 * blur becomes worse with distance and with angle, which is why it looked worst on the lines
 * crossing the view.
 *
 * TextureStateShard takes both flags, and both are off here:
 * <pre>
 *   new TextureStateShard(texture, false, false)   // blur, mipmap
 * </pre>
 *
 * Everything else matches the emissive entity render type the lines were using - full brightness
 * whatever the light level, translucent, no culling so a line is drawn from either side. The
 * constants used to build it are protected on RenderStateShard, so this extends it, which is the
 * usual way for a mod to reach them.
 *
 * Built once per texture and cached: a render type carries state and allocating one per frame,
 * per line, would be worse than the blur it is fixing.
 */
public final class SlashRenderTypes extends RenderStateShard {

    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    private SlashRenderTypes() {
        super("domain_expansion_slash", () -> {
        }, () -> {
        });
    }

    public static RenderType of(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, SlashRenderTypes::build);
    }

    private static RenderType build(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new ShaderStateShard(
                        GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .createCompositeState(false);

        return RenderType.create("domain_expansion_slash", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true, state);
    }
}

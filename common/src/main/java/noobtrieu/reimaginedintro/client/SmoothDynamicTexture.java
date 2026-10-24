package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Replaces the default nearest-neighbor sampler with bilinear filtering and edge clamping.
 *
 * 1.21.11 port note (VulkanMod compatibility): on this MC version the sampler cache lives in
 * {@code com.mojang.blaze3d.textures} (RenderPearl was only extracted in 26.x). VulkanMod 0.6.8
 * implements {@code GpuDevice.createSampler} with {@code VkSampler}, so this path stays fully
 * Vulkan-compatible. The texture never requests mipmaps, which matters because VulkanMod's
 * {@code VkGlTexture.generateMipmap} is a no-op.
 */
public class SmoothDynamicTexture extends DynamicTexture {
    public SmoothDynamicTexture(String label, int width, int height, boolean useCalloc) {
        super(label, width, height, useCalloc);
        // Replace nearest-neighbor sampler with linear (bilinear) filtering & edge clamping
        this.sampler = RenderSystem.getSamplerCache().getSampler(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR,
            FilterMode.LINEAR,
            false
        );
    }
}

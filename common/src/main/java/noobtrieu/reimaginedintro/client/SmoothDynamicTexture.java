package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

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

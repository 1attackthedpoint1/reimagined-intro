package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.ui.GlassRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frosted-glass rounded buttons for all menus.
 *
 * renderDefaultSprite is the single choke point every vanilla-styled button goes
 * through (Button / CycleButton / Checkbox / mod buttons using the default look),
 * so replacing it here restyles the whole menu at once. Label text, hover logic,
 * hitboxes and narration stay completely vanilla. In-game screens (world HUD,
 * inventory, etc.) are left untouched.
 */
@Mixin(AbstractButton.class)
public abstract class GlassButtonsMixin {
    @Shadow protected abstract boolean isHoveredOrFocused();
    @Shadow public abstract boolean isActive();
    @Shadow protected float alpha;

    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$renderGlassPanel(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!ModConfig.get().glassButtons) return;
        // Only restyle menus: keep in-game UI (pause, inventory, chat...) vanilla.
        if (Minecraft.getInstance().level != null) return;

        AbstractWidget self = (AbstractWidget) (Object) this;
        GlassRendering.drawButtonPanel(
            guiGraphics,
            self.getX(), self.getY(), self.getWidth(), self.getHeight(),
            isActive(), isHoveredOrFocused(), alpha
        );
        ci.cancel();
    }
}

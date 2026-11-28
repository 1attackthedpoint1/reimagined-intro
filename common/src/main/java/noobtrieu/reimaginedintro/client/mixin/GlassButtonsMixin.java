package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.ui.GlassRendering;
import org.spongepowered.asm.mixin.Mixin;
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
 *
 * Note: isHoveredOrFocused()/isActive() are declared on AbstractWidget — mixins
 * can only @Shadow members of the direct target class, so this mixin extends
 * AbstractWidget and calls them as plain virtual methods instead.
 */
@Mixin(AbstractButton.class)
public abstract class GlassButtonsMixin extends AbstractWidget {
    protected GlassButtonsMixin(int x, int y, int width, int height, net.minecraft.network.chat.Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$renderGlassPanel(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!ModConfig.get().glassButtons) return;
        if (noobtrieu.reimaginedintro.client.ui.GlassBridge.present()) return; // ReGlass owns the material
        // Liquid-glass theme applies EVERYWHERE (menus + in-game) so morph
        // droplets and settled buttons always share the same material.

        GlassRendering.drawButtonPanel(
            guiGraphics,
            this.getX(), this.getY(), this.getWidth(), this.getHeight(),
            this.isActive(), this.isHoveredOrFocused(), this.alpha
        );
        ci.cancel();
    }
}

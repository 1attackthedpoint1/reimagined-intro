package noobtrieu.reimaginedintro.client.mixin;
 
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import noobtrieu.reimaginedintro.client.IntroSoundController;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Unique
    private static final ThreadLocal<SoundInstance> reimaginedintro$activeSound = new ThreadLocal<>();

    @Inject(method = "play", at = @At("HEAD"))
    private void reimaginedintro$onPlayHead(SoundInstance soundInstance, CallbackInfoReturnable<?> cir) {
        reimaginedintro$activeSound.set(soundInstance);
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void reimaginedintro$onPlayReturn(SoundInstance soundInstance, CallbackInfoReturnable<?> cir) {
        reimaginedintro$activeSound.remove();
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"))
    private void reimaginedintro$onCalcVolumeHead(SoundInstance soundInstance, CallbackInfoReturnable<Float> cir) {
        reimaginedintro$activeSound.set(soundInstance);
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
    private void reimaginedintro$onCalcVolumeReturn(SoundInstance soundInstance, CallbackInfoReturnable<Float> cir) {
        reimaginedintro$activeSound.remove();
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("HEAD"), cancellable = true)
    private void reimaginedintro$onCalculateVolume(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        SoundInstance sound = reimaginedintro$activeSound.get();
        if (sound != null && IntroSoundController.isIntroSound(sound)) {
            if (!ModConfig.get().linkToMasterVolume) {
                cir.setReturnValue(Mth.clamp(ModConfig.get().introVolume, 0.0F, 1.0F));
            }
        }
    }
}

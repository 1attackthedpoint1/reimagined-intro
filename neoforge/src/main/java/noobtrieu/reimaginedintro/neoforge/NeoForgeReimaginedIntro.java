package noobtrieu.reimaginedintro.neoforge;

import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.registries.RegisterEvent;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.config.ModConfigScreen;

@Mod(ReimaginedIntro.MOD_ID)
public class NeoForgeReimaginedIntro {
    public NeoForgeReimaginedIntro(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::register);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (client, parentScreen) -> new ModConfigScreen(parentScreen)
            );
            ReimaginedIntro.init();
        }
    }

    private void register(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> {
            helper.register(ReimaginedIntro.INTRO_SOUND_ID, ReimaginedIntro.INTRO_SOUND_EVENT);
        });
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Registered sound events (NeoForge).");
    }
}

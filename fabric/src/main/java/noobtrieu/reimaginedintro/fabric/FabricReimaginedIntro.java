package noobtrieu.reimaginedintro.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import noobtrieu.reimaginedintro.ReimaginedIntro;

public class FabricReimaginedIntro implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, ReimaginedIntro.INTRO_SOUND_ID, ReimaginedIntro.INTRO_SOUND_EVENT);
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Registered sound events (Fabric).");
    }
}

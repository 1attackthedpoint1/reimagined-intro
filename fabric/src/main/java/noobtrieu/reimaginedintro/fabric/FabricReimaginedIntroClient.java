package noobtrieu.reimaginedintro.fabric;

import net.fabricmc.api.ClientModInitializer;
import noobtrieu.reimaginedintro.ReimaginedIntro;

public class FabricReimaginedIntroClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ReimaginedIntro.init();
    }
}

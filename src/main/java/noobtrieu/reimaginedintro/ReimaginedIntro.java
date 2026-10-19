package noobtrieu.reimaginedintro;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReimaginedIntro implements ModInitializer {
	public static final String MOD_ID = "reimagined-intro";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier INTRO_SOUND_ID = id("intro");
	public static final SoundEvent INTRO_SOUND_EVENT = SoundEvent.createVariableRangeEvent(INTRO_SOUND_ID);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.SOUND_EVENT, INTRO_SOUND_ID, INTRO_SOUND_EVENT);
		LOGGER.info("[ReimaginedIntro] Initialized mod sound events.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

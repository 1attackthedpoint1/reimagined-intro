package noobtrieu.reimaginedintro.client;

import net.fabricmc.api.ClientModInitializer;

public class ReimaginedIntroClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		noobtrieu.reimaginedintro.client.migration.LegacyMigrationHandler.checkAndPrompt();
	}
}
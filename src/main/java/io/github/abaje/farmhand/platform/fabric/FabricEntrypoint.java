package io.github.abaje.farmhand.platform.fabric;

//? fabric {

import io.github.abaje.farmhand.Farmhand;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ModInitializer;

@Entrypoint("main")
public class FabricEntrypoint implements ModInitializer {

	@Override
	public void onInitialize() {
		Farmhand.onInitialize();
	}
}
//?}

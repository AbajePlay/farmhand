package io.github.abaje.farmhand.platform.fabric;

//? fabric {

import io.github.abaje.farmhand.platform.Platform;

public class FabricPlatform implements Platform {

	@Override
	public ModLoader loader() {
		return ModLoader.FABRIC;
	}
}
//?}

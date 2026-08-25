package io.github.abaje.farmhand;

import io.github.abaje.farmhand.platform.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? fabric {
import io.github.abaje.farmhand.platform.fabric.FabricPlatform;
//?} else {
/*import io.github.abaje.farmhand.platform.neoforge.NeoforgePlatform;
*///?}

/**
 * Entry point. The mod is server-side: all the work happens in a mixin on the farmer villager's
 * behaviour, so there is nothing to register at runtime.
 */
public final class Farmhand {

	public static final String MOD_ID = /*$ mod_id*/ "farmhand";
	public static final String MOD_VERSION = /*$ mod_version*/ "1.0.0";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	private Farmhand() {
	}

	public static void onInitialize() {
		LOGGER.info("Farmhand {} on {}", MOD_VERSION, PLATFORM.loader());
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		return new FabricPlatform();
		//?} else {
		/*return new NeoforgePlatform();
		*///?}
	}
}

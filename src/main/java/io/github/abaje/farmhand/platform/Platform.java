package io.github.abaje.farmhand.platform;

/**
 * Minimal loader abstraction, deliberately reduced to a single method. Everything else the
 * template offered ({@code isModLoaded}, {@code mcVersion}, {@code isDevelopmentEnvironment})
 * went unused by Farmhand while dragging in version-fragile calls such as
 * {@code FMLLoader.getCurrent()}.
 */
public interface Platform {

	ModLoader loader();

	/** Quilt runs the Fabric build as-is, so it has no separate entry here. */
	enum ModLoader {
		FABRIC, NEOFORGE
	}
}

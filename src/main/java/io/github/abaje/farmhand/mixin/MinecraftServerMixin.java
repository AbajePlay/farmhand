package io.github.abaje.farmhand.mixin;

import io.github.abaje.farmhand.FarmhandSelfTest;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * Entry point for the self-test. Hooked through a mixin rather than loader events so that a
 * single implementation works on both Fabric and NeoForge.
 * <p>
 * Without {@code -Dfarmhand.selftest=true} the injected body returns immediately, so this does
 * nothing for players.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void farmhand$runSelfTest(CallbackInfo ci) {
		FarmhandSelfTest.onServerTick((MinecraftServer) (Object) this);
	}
}

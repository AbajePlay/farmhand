package io.github.abaje.farmhand.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
//? >=1.21.11 {
import net.minecraft.world.entity.npc.villager.Villager;
//?} else {
/*import net.minecraft.world.entity.npc.Villager;
*///?}

/**
 * Access to the behaviour's protected methods. The self-test uses it to drive the scenario
 * directly, without waiting on the villager's brain, its schedule or the time of day, so the
 * checks hit exactly the code this mod changes and stay deterministic.
 */
@Mixin(HarvestFarmland.class)
public interface HarvestFarmlandAccessor {

	@Invoker("checkExtraStartConditions")
	boolean farmhand$checkStart(ServerLevel level, Villager villager);

	@Invoker("tick")
	void farmhand$tick(ServerLevel level, Villager villager, long gameTime);
}

package io.github.abaje.farmhand.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
//? >=1.21.11 {
import net.minecraft.world.entity.npc.villager.Villager;
//?} else {
/*import net.minecraft.world.entity.npc.Villager;
*///?}
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

/**
 * Makes a farmer villager replant the crop it just harvested.
 * <p>
 * Vanilla's {@code HarvestFarmland#tick} walks the inventory in slot order and plants the
 * <b>first</b> plantable seed it finds, so a farmer that harvested carrots will replant wheat
 * whenever wheat seeds sit in an earlier slot. Modded crops already work in vanilla through the
 * {@code villager_plantable_seeds} tag, so only the choice of crop is fixed here.
 * <p>
 * A {@link HarvestFarmland} instance belongs to a single villager's brain, which is why "what grew
 * here" is kept in plain fields: no global maps, no timed cleanup, no leaks on chunk unload.
 */
@Mixin(HarvestFarmland.class)
public abstract class HarvestFarmlandMixin {

	@Shadow
	private BlockPos aboveFarmlandPos;

	/** Crop harvested at {@link #farmhand$harvestedAt}. */
	@Unique
	private Block farmhand$harvestedCrop;

	/** Plot the crop was harvested from. */
	@Unique
	private BlockPos farmhand$harvestedAt;

	/** Crop to plant on this pass, or {@code null} to behave exactly like vanilla. */
	@Unique
	private Block farmhand$preferredCrop;

	/**
	 * Record the crop before vanilla destroys the block — afterwards the position is already air.
	 */
	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private void farmhand$rememberHarvestedCrop(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		BlockPos pos = this.aboveFarmlandPos;
		if (pos == null) {
			return;
		}

		Block block = level.getBlockState(pos).getBlock();
		if (block instanceof CropBlock) {
			this.farmhand$harvestedCrop = block;
			this.farmhand$harvestedAt = pos.immutable();
		}
	}

	/**
	 * Runs right before vanilla scans the inventory. Preference is only enabled when the villager
	 * really carries the matching seed; otherwise vanilla behaviour is left alone, so the plot
	 * never ends up empty because of this mod.
	 */
	// The descriptor must be fully qualified: the mixin remapper rejects an owner-less target
	// ("... is not fully qualified") and the mod would silently fail to apply in production.
	// Villager moved package in 1.21.11 (npc -> npc.villager), hence the two branches.
	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			//? >=1.21.11 {
			target = "Lnet/minecraft/world/entity/npc/villager/Villager;hasFarmSeeds()Z"
			//?} else {
			/*target = "Lnet/minecraft/world/entity/npc/Villager;hasFarmSeeds()Z"
			*///?}
		)
	)
	private boolean farmhand$choosePreferredCrop(Villager villager) {
		boolean hasSeeds = villager.hasFarmSeeds();
		this.farmhand$preferredCrop = hasSeeds ? farmhand$resolvePreferred(villager) : null;
		return hasSeeds;
	}

	/**
	 * Vanilla filters seeds with this check. Letting only the wanted crop through makes its loop
	 * reach the right slot instead of stopping at the first plantable one.
	 */
	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"
		)
	)
	private boolean farmhand$acceptOnlyPreferredSeed(ItemStack stack, TagKey<Item> tag) {
		if (!stack.is(tag)) {
			return false;
		}

		Block preferred = this.farmhand$preferredCrop;
		return preferred == null || farmhand$plants(stack, preferred);
	}

	/**
	 * Crop worth putting back, or {@code null} when there is nothing to prefer: nothing was
	 * harvested, the villager moved to another plot, or it lacks the matching seed.
	 */
	@Unique
	private Block farmhand$resolvePreferred(Villager villager) {
		Block harvested = this.farmhand$harvestedCrop;
		BlockPos pos = this.aboveFarmlandPos;
		if (harvested == null || pos == null || !pos.equals(this.farmhand$harvestedAt)) {
			return null;
		}

		SimpleContainer inventory = villager.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			// The tag check is required. Without it we could prefer a crop whose seed vanilla then
			// rejects in the planting loop, leaving the plot empty — worse than no mod at all.
			// This check and farmhand$acceptOnlyPreferredSeed must accept the exact same items.
			if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && farmhand$plants(stack, harvested)) {
				return harvested;
			}
		}
		return null;
	}

	/** Whether the item plants this exact crop — the same way vanilla identifies it. */
	@Unique
	private static boolean farmhand$plants(ItemStack stack, Block crop) {
		return !stack.isEmpty()
			&& stack.getItem() instanceof BlockItem blockItem
			&& blockItem.getBlock() == crop;
	}
}

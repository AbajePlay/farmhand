package io.github.abaje.farmhand;

import io.github.abaje.farmhand.mixin.HarvestFarmlandAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
//? >=1.21.11 {
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
//?} else {
/*import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
*///?}

/**
 * Functional self-test, enabled only with {@code -Dfarmhand.selftest=true}.
 * <p>
 * It has two parts:
 * <ol>
 *   <li>fast scenarios per vanilla crop, where the behaviour is driven directly through
 *       {@link HarvestFarmlandAccessor}, which keeps the result deterministic;</li>
 *   <li>a live villager, where the scenario is set up and the server then ticks the villager
 *       itself, so the brain, the schedule and the plot search all take part.</li>
 * </ol>
 * GameTest is deliberately not used: Mojang rewrote that API in 1.21.5, and covering the
 * 1.20 - 26.2 range would require two independent implementations.
 */
public final class FarmhandSelfTest {

	public static final String PROPERTY = "farmhand.selftest";
	private static final String TAG = "FARMHAND-SELFTEST";

	/** Let the world finish loading before building the scenario. */
	private static final int SETUP_TICK = 20;
	/** A live villager needs time to reach the plot and run its behaviour. */
	private static final int LIVE_CHECK_TICK = SETUP_TICK + 600;

	private static final BlockPos LIVE_FARMLAND = new BlockPos(200, 100, 0);

	private static int ticks;
	private static boolean directPassed;

	private FarmhandSelfTest() {
	}

	public static boolean requested() {
		return Boolean.getBoolean(PROPERTY);
	}

	/** Called from the mixin on every server tick. */
	public static void onServerTick(MinecraftServer server) {
		if (!requested()) {
			return;
		}
		ticks++;

		try {
			ServerLevel level = server.overworld();
			if (ticks == SETUP_TICK) {
				directPassed = runDirectScenarios(level);
				setUpLiveVillager(server, level);
			} else if (ticks == LIVE_CHECK_TICK) {
				boolean live = checkLiveVillager(level);
				report(directPassed && live, "self-test verdict");
			}
		} catch (Throwable t) {
			report(false, "exception: " + t);
			Farmhand.LOGGER.error("{} details", TAG, t);
		}
	}

	// ------------------- part 1: fast scenarios per crop -------------------

	/**
	 * For each crop the villager carries wheat seeds <b>before</b> the matching seed. Vanilla
	 * walks slots in order and would plant wheat, so the mod has to pick the harvested crop
	 * instead. A control run without the matching seed must leave vanilla wheat behind,
	 * otherwise the test proves nothing.
	 */
	private static boolean runDirectScenarios(ServerLevel level) {
		Object[][] crops = {
			{ Blocks.CARROTS, Items.CARROT, "carrots" },
			{ Blocks.POTATOES, Items.POTATO, "potatoes" },
			{ Blocks.BEETROOTS, Items.BEETROOT_SEEDS, "beetroot" },
		};

		boolean all = true;
		int x = 0;
		for (Object[] row : crops) {
			Block crop = (Block) row[0];
			Item seed = (Item) row[1];
			String name = (String) row[2];

			Block planted = scenario(level, new BlockPos(x, 100, 0), crop, seed, true);
			boolean prefers = planted == crop;
			report(prefers, "preference (" + name + "): planted " + planted);

			Block fallback = scenario(level, new BlockPos(x + 8, 100, 0), crop, seed, false);
			boolean vanilla = fallback == Blocks.WHEAT;
			report(vanilla, "fallback without seed (" + name + "): planted " + fallback);

			all = all && prefers && vanilla;
			x += 16;
		}
		return all;
	}

	private static Block scenario(ServerLevel level, BlockPos farmland, Block crop, Item seed, boolean giveSeed) {
		BlockPos cropPos = farmland.above();
		level.setBlockAndUpdate(farmland, Blocks.FARMLAND.defaultBlockState());
		CropBlock cropBlock = (CropBlock) crop;
		level.setBlockAndUpdate(cropPos, cropBlock.getStateForAge(cropBlock.getMaxAge()));

		Villager villager = createFarmer(level, cropPos);
		if (villager == null) {
			return Blocks.AIR;
		}

		// Wheat first: this is what vanilla would pick.
		villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
		if (giveSeed) {
			villager.getInventory().addItem(new ItemStack(seed, 8));
		}

		HarvestFarmlandAccessor behavior = (HarvestFarmlandAccessor) new HarvestFarmland();
		if (!behavior.farmhand$checkStart(level, villager)) {
			Farmhand.LOGGER.error("{} behaviour did not start at {}", TAG, farmland);
			villager.discard();
			return Blocks.AIR;
		}

		// Vanilla never harvests and plants on the same tick: blockState is read before the block
		// is destroyed, so several ticks are needed - harvest first, then planting.
		for (int i = 0; i < 8; i++) {
			behavior.farmhand$tick(level, villager, level.getGameTime() + i);
		}

		villager.discard();
		return level.getBlockState(cropPos).getBlock();
	}

	// ------------------- part 2: live villager -------------------

	/**
	 * Places a plot with mature carrots and a real farmer next to it, then hands control back to
	 * the server: nothing calls the behaviour by hand, the villager has to pick the plot through
	 * its own brain. This covers the path the fast scenarios deliberately skip.
	 */
	private static void setUpLiveVillager(MinecraftServer server, ServerLevel level) {
		BlockPos cropPos = LIVE_FARMLAND.above();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				level.setBlockAndUpdate(LIVE_FARMLAND.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
			}
		}
		level.setBlockAndUpdate(LIVE_FARMLAND, Blocks.FARMLAND.defaultBlockState());
		CropBlock carrots = (CropBlock) Blocks.CARROTS;
		level.setBlockAndUpdate(cropPos, carrots.getStateForAge(carrots.getMaxAge()));

		Villager villager = createFarmer(level, cropPos);
		if (villager == null) {
			Farmhand.LOGGER.error("{} live scenario: villager not created", TAG);
			return;
		}
		// Wheat first: this is what vanilla would pick.
		villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
		villager.getInventory().addItem(new ItemStack(Items.CARROT, 8));

		// HarvestFarmland requires this memory; in vanilla the job site search provides it.
		// The memory holds a list of positions, not a single one.
		villager.getBrain().setMemory(
			MemoryModuleType.SECONDARY_JOB_SITE,
			java.util.List.of(GlobalPos.of(level.dimension(), LIVE_FARMLAND))
		);
		villager.getBrain().setActiveActivityIfPossible(Activity.WORK);

		// Villagers only work during the day. The time is set through a command because the Java
		// API drifted - 26.2 has neither setDayTime nor getDayTime on ServerLevel - while the
		// command syntax is identical across the whole version range.
		server.getCommands().performPrefixedCommand(
			server.createCommandSourceStack(), "time set 2000");

		Farmhand.LOGGER.info("{} live scenario set up at {}", TAG, LIVE_FARMLAND);
	}

	private static boolean checkLiveVillager(ServerLevel level) {
		Block planted = level.getBlockState(LIVE_FARMLAND.above()).getBlock();
		boolean ok = planted == Blocks.CARROTS;
		report(ok, "live villager: plot holds " + planted);
		return ok;
	}

	// ------------------- shared -------------------

	private static Villager createFarmer(ServerLevel level, BlockPos near) {
		// The Villager(EntityType, Level) constructor exists across the whole 1.20 - 26.2 range,
		// unlike EntityType.create(...) whose signature changed twice (MobSpawnType was added,
		// then renamed to EntitySpawnReason). Entity type constants moved from EntityType to
		// EntityTypes in 26.2; 26.1.2 still uses the old name.
		//? >=26.2 {
		Villager villager = new Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
		//?} else {
		/*Villager villager = new Villager(EntityType.VILLAGER, level);
		*///?}
		// setPos exists across the whole range; moveTo was renamed to snapTo in 26.x and the
		// villager rotation does not matter here, so no version branch is needed.
		villager.setPos(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		villager.setVillagerData(farmerData(level));
		level.addFreshEntity(villager);
		return villager;
	}

	/** Since 1.21.5 VillagerData holds Holders instead of the type and profession objects. */
	private static VillagerData farmerData(ServerLevel level) {
		//? >=1.21.5 {
		return new VillagerData(
			level.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
			level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.FARMER),
			1
		);
		//?} else {
		/*return new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1);
		*///?}
	}

	private static void report(boolean ok, String detail) {
		Farmhand.LOGGER.info("{} {} - {}", TAG, ok ? "PASS" : "FAIL", detail);
	}
}

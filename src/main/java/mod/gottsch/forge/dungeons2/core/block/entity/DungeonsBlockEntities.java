
package mod.gottsch.forge.dungeons2.core.block.entity;

import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/**
 * Dungeons2's block entity types.
 *
 * <h2>This class registered NOTHING until 2026-08-14, and the reason is worth keeping</h2>
 * <p>A {@code DeferredRegister} collects an entry when the {@link RegistryObject} field initialises
 * &mdash; which happens only when the holding class is first loaded. Nothing loaded this one: it
 * carries no {@code @Mod.EventBusSubscriber} (so Forge does not force-load it), and its only
 * reference in the whole mod was from {@code DeferredDungeonGeneratorBlockEntity}'s own
 * constructor, which cannot run until after the type it needs is registered. The same was true of
 * {@link DungeonsBlocks}. Both were therefore absent from the game's registries, silently and with
 * no error &mdash; the identical trap {@code DungeonsEntities} hit during the rat work (#40/#41).
 * {@link Registration#init()} now touches both explicitly.</p>
 *
 * <h2>Backlog #43 said not to fix this. That advice was right, and is now spent</h2>
 * <p>#43's reasoning: wiring these up "would put a block with no model, no loot table and no display
 * name into the registry to serve a code path that does not run". True while
 * {@link #DEFERRED_DUNGEON_GENERATOR_ENTITY_TYPE} was the only occupant. #10's mob-set spawner is a
 * live block that genuinely needs registering, so the holder has to be wired regardless &mdash; and
 * the dead entry, now unavoidably along for the ride, was given the model and lang key it lacked.
 * <strong>Phase 6 deletes the deferred-generator field and its block, not this class.</strong></p>
 *
 * @author Mark Gottschling on Oct 25, 2023
 *
 */
public class DungeonsBlockEntities {

	public static final RegistryObject<BlockEntityType<DeferredDungeonGeneratorBlockEntity>> DEFERRED_DUNGEON_GENERATOR_ENTITY_TYPE =
			Registration.BLOCK_ENTITIES.register("deferred_dungeon_generator",
					() -> BlockEntityType.Builder.of(DeferredDungeonGeneratorBlockEntity::new,
									DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR.get())
							.build(null));

	/**
	 * Backlog #10. The block entity is {@link DungeonSpawnerBlockEntity} &mdash; GottschCore's
	 * proximity mob-set spawner plus a persisted floor index. GottschCore is a library that
	 * registers nothing, so the type belongs to whoever consumes it.
	 *
	 * <p>Built with the {@code Supplier} constructor rather than the eager one so the type is
	 * resolved on demand instead of during its own registration.</p>
	 */
	public static final RegistryObject<BlockEntityType<DungeonSpawnerBlockEntity>> MOB_SET_SPAWNER =
			Registration.BLOCK_ENTITIES.register("mob_set_spawner",
					() -> BlockEntityType.Builder.of(
									(pos, state) -> new DungeonSpawnerBlockEntity(
											DungeonsBlockEntities::mobSetSpawnerType, pos, state),
									DungeonsBlocks.MOB_SET_SPAWNER.get())
							.build(null));

	/**
	 * Indirection so {@link mod.gottsch.forge.dungeons2.core.block.MobSetSpawnerBlock} and the
	 * builder above can both hand GottschCore a {@code Supplier} instead of a resolved type.
	 */
	public static BlockEntityType<DungeonSpawnerBlockEntity> mobSetSpawnerType() {
		return MOB_SET_SPAWNER.get();
	}

	/**
	 * Forces this class to load so the fields above actually register. Called from
	 * {@link Registration#init()}.
	 */
	public static void register() {
		// Intentionally empty -- class loading is the whole point.
	}
}

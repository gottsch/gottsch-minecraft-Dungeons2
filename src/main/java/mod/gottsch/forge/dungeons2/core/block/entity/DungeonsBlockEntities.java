
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
 * name into the registry to serve a code path that does not run". True while the deferred-generator
 * type was the only occupant. #10's mob-set spawner is a live block that genuinely needs
 * registering, so the holder had to be wired regardless &mdash; and <strong>Phase 6 then deleted the
 * dead entry outright (2026-08-18)</strong>, which is the resolution #43 asked for: the field and
 * its block go, this class stays because live content is in it.</p>
 *
 * @author Mark Gottschling on Oct 25, 2023
 *
 */
public class DungeonsBlockEntities {

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
	 * Backlog #48 step 3. Carries no behaviour -- it exists so a template can store per-marker
	 * fields and {@code ChestMarkerProcessor} can read them back during placement.
	 */
	public static final RegistryObject<BlockEntityType<ChestMarkerBlockEntity>> CHEST_MARKER =
			Registration.BLOCK_ENTITIES.register("chest_marker",
					() -> BlockEntityType.Builder.of(ChestMarkerBlockEntity::new,
							DungeonsBlocks.CHEST_MARKER.get()).build(null));

	/**
	 * The spawner authoring marker's per-cell data. Registered last of the three markers and for the
	 * same reason as the other two: which set, how many and how far are PER MARKER decisions, and a
	 * codec field on the processor is per POOL. See {@link SpawnerMarkerBlockEntity}.
	 */
	public static final RegistryObject<BlockEntityType<SpawnerMarkerBlockEntity>> SPAWNER_MARKER =
			Registration.BLOCK_ENTITIES.register("spawner_marker",
					() -> BlockEntityType.Builder.of(SpawnerMarkerBlockEntity::new,
							DungeonsBlocks.SPAWNER_MARKER.get()).build(null));

	/**
	 * Backlog #56. Like {@link #CHEST_MARKER} it carries no behaviour: it exists so a template can
	 * store per-marker fields and {@code PotMarkerProcessor} can read them back during placement.
	 */
	public static final RegistryObject<BlockEntityType<PotMarkerBlockEntity>> POT_MARKER =
			Registration.BLOCK_ENTITIES.register("pot_marker",
					() -> BlockEntityType.Builder.of(PotMarkerBlockEntity::new,
							DungeonsBlocks.POT_MARKER.get()).build(null));

	/**
	 * Forces this class to load so the fields above actually register. Called from
	 * {@link Registration#init()}.
	 */
	public static void register() {
		// Intentionally empty -- class loading is the whole point.
	}
}

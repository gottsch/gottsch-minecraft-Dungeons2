
package mod.gottsch.forge.dungeons2.core.block.entity;

import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/**
 * <strong>This registers nothing today, and that is currently harmless. Verified 2026-08-13.</strong>
 *
 * <p>A {@code DeferredRegister} collects an entry when the {@link RegistryObject} field below
 * initialises &mdash; which happens only when this class is first loaded. Nothing loads it during
 * startup: it carries no {@code @Mod.EventBusSubscriber} (so Forge does not force-load it), and its
 * only reference in the whole mod is from {@code DeferredDungeonGeneratorBlockEntity}'s own
 * constructor, which cannot run until after the type it needs is registered. The same is true of
 * {@link DungeonsBlocks}. So the block and the block entity are both absent from the game's
 * registries.</p>
 *
 * <p><strong>Why that does not matter yet:</strong> the whole deferred-generation path is Phase 2
 * legacy awaiting Phase 6 deletion. {@code ConfiguredFeatures} is commented out in its entirety, so
 * {@code DungeonFeature} &mdash; the only thing that ever placed this block &mdash; is never
 * registered either, and the {@code deferred_dungeon*.json} files sit outside any registry folder.
 * Nothing places the block, so nothing misses it.</p>
 *
 * <p><strong>Do not "fix" this by touching the class in {@code Registration.init()}</strong> the way
 * {@code DungeonsEntities} and {@code DungeonsItems} are. That would put a block with no model, no
 * loot table and no display name into the registry to serve a code path that does not run. Either
 * leave it, or delete the pair with the rest of Phase 6.</p>
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

}

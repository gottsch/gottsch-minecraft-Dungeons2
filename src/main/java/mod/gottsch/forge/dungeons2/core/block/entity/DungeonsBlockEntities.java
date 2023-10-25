
package mod.gottsch.forge.dungeons2.core.block.entity;

import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/**
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

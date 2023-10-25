package mod.gottsch.forge.dungeons2.core.block;

import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

public class DungeonsBlocks {
    public static final RegistryObject<Block> DEFERRED_DUNGEON_GENERATOR = Registration.BLOCKS.register("deferred_dungeon_generator", () -> new DeferredDungeonGeneratorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
            .strength(3.0F).sound(SoundType.STONE).replaceable().noCollission().noLootTable().air()));

}

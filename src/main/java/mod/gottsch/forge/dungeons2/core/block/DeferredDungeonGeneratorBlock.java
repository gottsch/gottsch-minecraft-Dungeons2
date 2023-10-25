package mod.gottsch.forge.dungeons2.core.block;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.entity.DeferredDungeonGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class DeferredDungeonGeneratorBlock extends BaseEntityBlock {
    public DeferredDungeonGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        Dungeons.LOGGER.debug("created dungeon generate be");
        DeferredDungeonGeneratorBlockEntity blockEntity = null;
        try {
            blockEntity = new DeferredDungeonGeneratorBlockEntity(pos, state);
        }
        catch(Exception e) {
            Dungeons.LOGGER.error(e);
        }
        return (BlockEntity) blockEntity;
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() ? (lvl, pos, blockState, t) -> {
            if (t instanceof DeferredDungeonGeneratorBlockEntity entity) {
                entity.tickServer();
            }
        } : null;
    }

    @Override
    public boolean isAir(BlockState state) {
        return true;
    }
}

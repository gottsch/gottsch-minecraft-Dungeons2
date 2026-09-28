package mod.gottsch.forge.dungeons2.core.event;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a tomb's guardian says where it came from (#104). DungeonBlocks raises it TRIGGERED with no
 * spawner, from code Dungeons2 does not run, so the only witness is the open tomb it stands on or
 * beside &mdash; and the stamp generation left on it.
 */
class EchelonTombOriginTest {

    /** Bed-shaped, with a lid: the two properties a sarcophagus is recognised by. */
    static final class TombStandIn extends Block {
        TombStandIn() {
            super(BlockBehaviour.Properties.of());
            registerDefaultState(stateDefinition.any());
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.BED_PART,
                    BlockStateProperties.OPEN);
        }
    }

    /** A handful of cells; everything else is air. */
    static final class Cells implements BlockGetter {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, BlockEntity> entities = new HashMap<>();

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return entities.get(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private static final BlockPos HEAD = new BlockPos(5, 40, 5);
    private static final BlockPos FOOT = HEAD.south();
    private static Block tomb;
    private static BlockEntityType<BlockEntity> type;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        tomb = new TombStandIn();
        type = BlockEntityType.Builder.<BlockEntity>of((pos, state) -> null, tomb).build(null);
    }

    /** A tomb, head and foot, each carrying the stamp when asked to, lid open or shut. */
    private static Cells tomb(boolean open, boolean stamped) {
        Cells cells = new Cells();
        for (BlockPos pos : new BlockPos[] {HEAD, FOOT}) {
            BlockState state = tomb.defaultBlockState()
                    .setValue(BlockStateProperties.BED_PART, pos == HEAD ? BedPart.HEAD : BedPart.FOOT)
                    .setValue(BlockStateProperties.OPEN, open);
            BlockEntity entity = new BlockEntity(type, pos, state) {};
            if (stamped) {
                entity.getPersistentData().put(EchelonSpawnEvent.ORIGIN,
                        EchelonSpawnEvent.originTag("classic", 3));
            }
            cells.states.put(pos, state);
            cells.entities.put(pos, entity);
        }
        return cells;
    }

    @Test
    void aGuardianOnTheLidTakesTheTombsOrigin() {
        Optional<EchelonSpawnEvent.Origin> origin = EchelonSpawnEvent.tombOrigin(tomb(true, true), HEAD);
        assertTrue(origin.isPresent());
        assertEquals("classic", origin.get().motif());
        assertEquals(3, origin.get().floorIndex());
    }

    /** No headroom on the lid: DungeonBlocks stands it beside either half. */
    @Test
    void aGuardianBesideEitherHalfFindsItToo() {
        assertTrue(EchelonSpawnEvent.tombOrigin(tomb(true, true), HEAD.east()).isPresent());
        assertTrue(EchelonSpawnEvent.tombOrigin(tomb(true, true), FOOT.south()).isPresent());
    }

    @Test
    void twoCellsAwayIsNotTheTomb() {
        assertTrue(EchelonSpawnEvent.tombOrigin(tomb(true, true), HEAD.east(2)).isEmpty());
    }

    /** A shut lid means no tomb is being opened; a TRIGGERED spawn beside it is something else. */
    @Test
    void aClosedTombClaimsNothing() {
        assertTrue(EchelonSpawnEvent.tombOrigin(tomb(false, true), HEAD).isEmpty());
    }

    /** A player's own sarcophagus, or one generated before the stamp existed. */
    @Test
    void anUnstampedTombClaimsNothing() {
        assertTrue(EchelonSpawnEvent.tombOrigin(tomb(true, false), HEAD).isEmpty());
    }

    @Test
    void onlyABedShapedBlockWithAnOpenLidIsATomb() {
        assertTrue(EchelonSpawnEvent.isOpenTomb(tomb.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true)));
        assertFalse(EchelonSpawnEvent.isOpenTomb(Blocks.RED_BED.defaultBlockState()), "no lid");
        assertFalse(EchelonSpawnEvent.isOpenTomb(Blocks.BARREL.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true)), "a lid, but not bed-shaped");
    }
}

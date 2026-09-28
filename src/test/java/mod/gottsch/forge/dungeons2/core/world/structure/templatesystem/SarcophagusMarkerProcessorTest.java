package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.core.block.entity.SarcophagusMarkerBlockEntity;
import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.TombContents;
import mod.gottsch.forge.dungeons2.core.event.EchelonSpawnEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authored tomb route (#104). A vanilla CHEST stands in for the marker (it has the same
 * {@code facing}), and a block built here stands in for the tomb: {@code facing}, {@code part} and
 * {@code open}, with its default taken from {@code stateDefinition.any()} -- which makes it OPEN,
 * exactly the trap the real block's constructor has to dodge. Built through the registry
 * {@code unfreeze()} {@code DungeonSpawnerBlockEntityTest} documents; never registered.
 */
class SarcophagusMarkerProcessorTest {

    /** Bed-shaped, with a lid, and open by default. */
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

    private static BlockState TOMB;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        TOMB = new TombStandIn().defaultBlockState();
    }

    private static final BlockPos MARKER = new BlockPos(10, 64, 20);

    private static final TombContents POOL = new TombContents(
            Optional.of(List.of(new ChestConfig.LootTableEntry("dungeons2:chests/classic_tomb_deep", 1))),
            Optional.of(List.of(new TombContents.GuardianEntry("dungeons2:ghoul", 1))),
            Optional.of(3), Optional.of(4), Optional.of(3));

    private static StructureTemplate.StructureBlockInfo info(BlockPos pos, BlockState state,
                                                             CompoundTag nbt) {
        return new StructureTemplate.StructureBlockInfo(pos, state, nbt);
    }

    private static BlockState marker(Direction facing) {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
    }

    /** A marker, the air all round it, and a floor under it. */
    private static List<StructureTemplate.StructureBlockInfo> piece(Direction facing, CompoundTag nbt) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        blocks.add(info(MARKER, marker(facing), nbt));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            blocks.add(info(MARKER.relative(side), Blocks.AIR.defaultBlockState(), null));
        }
        blocks.add(info(MARKER.below(), Blocks.STONE.defaultBlockState(), null));
        return blocks;
    }

    private static List<StructureTemplate.StructureBlockInfo> convert(
            List<StructureTemplate.StructureBlockInfo> blocks, Rotation rotation, TombContents pool) {
        return SarcophagusMarkerProcessor.convert(blocks, state -> state.is(Blocks.CHEST), TOMB,
                rotation, Mirror.NONE, pool, null, "test:tomb");
    }

    private static StructureTemplate.StructureBlockInfo at(
            List<StructureTemplate.StructureBlockInfo> blocks, BlockPos pos) {
        return blocks.stream().filter(i -> i.pos().equals(pos)).findFirst().orElseThrow();
    }

    @Test
    void theMarkerBecomesTheFootAndTheAirAheadTheHead() {
        List<StructureTemplate.StructureBlockInfo> out = convert(piece(Direction.NORTH, null),
                Rotation.NONE, POOL);
        BlockState foot = at(out, MARKER).state();
        BlockState head = at(out, MARKER.north()).state();
        assertTrue(foot.is(TOMB.getBlock()) && head.is(TOMB.getBlock()));
        assertTrue(TOMB.getValue(BlockStateProperties.OPEN), "the stand-in must start open to prove anything");
        assertFalse(foot.getValue(BlockStateProperties.OPEN), "a tomb generated open is never opened");
        assertFalse(head.getValue(BlockStateProperties.OPEN));
        assertEquals(BedPart.FOOT, foot.getValue(BlockStateProperties.BED_PART));
        assertEquals(BedPart.HEAD, head.getValue(BlockStateProperties.BED_PART));
        assertEquals(Direction.NORTH, foot.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Direction.NORTH, head.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertTrue(at(out, MARKER.south()).state().isAir(), "only the cell AHEAD is taken");
    }

    /**
     * Positions are world positions, states are not: vanilla rotates this processor's output. So
     * the head's CELL applies the rotation and both halves keep the authored facing -- rotating
     * the state here as well is the double rotation ChestMarkerProcessor#markerFacing records.
     */
    @Test
    void theHeadsCellFollowsTheRotationAndTheStatesDoNot() {
        List<StructureTemplate.StructureBlockInfo> out = convert(piece(Direction.NORTH, null),
                Rotation.CLOCKWISE_90, POOL);
        BlockState head = at(out, MARKER.east()).state();
        assertTrue(head.is(TOMB.getBlock()), "north turned clockwise is east");
        assertEquals(Direction.NORTH, head.getValue(BlockStateProperties.HORIZONTAL_FACING),
                "unrotated: placement turns it to east");
        assertTrue(at(out, MARKER.north()).state().isAir());
    }

    @Test
    void aHeadCellThatIsNotAirLeavesTheMarkerStanding() {
        List<StructureTemplate.StructureBlockInfo> blocks = piece(Direction.NORTH, null);
        blocks.set(blocks.indexOf(at(blocks, MARKER.north())),
                info(MARKER.north(), Blocks.STONE_BRICKS.defaultBlockState(), null));
        List<StructureTemplate.StructureBlockInfo> out = convert(blocks, Rotation.NONE, POOL);
        assertTrue(at(out, MARKER).state().is(Blocks.CHEST), "the marker stays, visibly wrong");
        assertTrue(at(out, MARKER.north()).state().is(Blocks.STONE_BRICKS), "the wall is not eaten");
    }

    @Test
    void aHeadCellOutsideTheTemplateLeavesTheMarkerStanding() {
        List<StructureTemplate.StructureBlockInfo> blocks = piece(Direction.NORTH, null);
        blocks.removeIf(i -> i.pos().equals(MARKER.north()));
        List<StructureTemplate.StructureBlockInfo> out = convert(blocks, Rotation.NONE, POOL);
        assertTrue(at(out, MARKER).state().is(Blocks.CHEST));
        assertEquals(blocks.size(), out.size(), "nothing is written outside the template");
    }

    @Test
    void twoMarkersCannotShareOneHead() {
        BlockPos other = MARKER.north().north();
        List<StructureTemplate.StructureBlockInfo> blocks = piece(Direction.NORTH, null);
        blocks.add(info(other, marker(Direction.SOUTH), null));
        List<StructureTemplate.StructureBlockInfo> out = convert(blocks, Rotation.NONE, POOL);
        boolean first = at(out, MARKER).state().is(TOMB.getBlock());
        boolean second = at(out, other).state().is(TOMB.getBlock());
        assertTrue(first ^ second, "exactly one of the two may take the shared head cell");
    }

    @Test
    void theContentsAreOnTheHeadAndTheMarkersOwnValuesWin() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(SarcophagusMarkerBlockEntity.GUARDIAN, "minecraft:husk");
        nbt.putInt(SarcophagusMarkerBlockEntity.EMPTY_WEIGHT, 0);
        List<StructureTemplate.StructureBlockInfo> out = convert(piece(Direction.WEST, nbt),
                Rotation.NONE, POOL);
        CompoundTag head = at(out, MARKER.west()).nbt();
        assertEquals("minecraft:husk", head.getString(TombContents.Drawn.GUARDIAN), "the marker's own");
        assertEquals("dungeons2:chests/classic_tomb_deep", head.getString(TombContents.Drawn.LOOT_TABLE),
                "the pool's, since the marker named none");
        assertEquals(0, head.getInt(TombContents.Drawn.EMPTY_WEIGHT), "the marker's own");
        assertEquals(4, head.getInt(TombContents.Drawn.GUARDIAN_WEIGHT), "the pool's");
        assertTrue(head.getLong(TombContents.Drawn.LOOT_TABLE_SEED) != 0L);
        assertNull(at(out, MARKER).nbt(), "no stamp, so the foot keeps its own fresh entity");
    }

    @Test
    void aTombThatCanHoldNothingLeavesTheMarkerStanding() {
        List<StructureTemplate.StructureBlockInfo> out = convert(piece(Direction.NORTH, null),
                Rotation.NONE, TombContents.NONE);
        assertTrue(at(out, MARKER).state().is(Blocks.CHEST));
    }

    @Test
    void bothDepthKeysStampBothHalvesAndOneAloneStampsNothing() {
        CompoundTag both = new CompoundTag();
        both.putInt(SarcophagusMarkerBlockEntity.FLOOR_INDEX, 3);
        both.putString(SarcophagusMarkerBlockEntity.MOTIF, "classic");
        List<StructureTemplate.StructureBlockInfo> out = convert(piece(Direction.NORTH, both),
                Rotation.NONE, POOL);
        for (BlockPos half : List.of(MARKER, MARKER.north())) {
            CompoundTag origin = at(out, half).nbt().getCompound(SarcophagusMarkerProcessor.FORGE_DATA)
                    .getCompound(EchelonSpawnEvent.ORIGIN);
            assertEquals(3, origin.getInt(EchelonSpawnEvent.FLOOR_INDEX));
            assertEquals("classic", origin.getString(EchelonSpawnEvent.MOTIF));
        }

        CompoundTag one = new CompoundTag();
        one.putInt(SarcophagusMarkerBlockEntity.FLOOR_INDEX, 3);
        List<StructureTemplate.StructureBlockInfo> unstamped = convert(piece(Direction.NORTH, one),
                Rotation.NONE, POOL);
        assertFalse(at(unstamped, MARKER.north()).nbt().contains(SarcophagusMarkerProcessor.FORGE_DATA));
    }

    /** Every chunk pass sees the whole piece and must build the identical tomb. */
    @Test
    void everyPassBuildsTheSameTomb() {
        List<StructureTemplate.StructureBlockInfo> a = convert(piece(Direction.EAST, null),
                Rotation.NONE, POOL);
        List<StructureTemplate.StructureBlockInfo> b = convert(piece(Direction.EAST, null),
                Rotation.NONE, POOL);
        assertEquals(at(a, MARKER.east()).nbt(), at(b, MARKER.east()).nbt());
    }

    @Test
    void aBlockThatIsNotAMarkerPassesThroughUntouched() {
        List<StructureTemplate.StructureBlockInfo> blocks = piece(Direction.NORTH, null);
        List<StructureTemplate.StructureBlockInfo> out = SarcophagusMarkerProcessor.convert(blocks,
                state -> false, TOMB, Rotation.NONE, Mirror.NONE, POOL, null, "test:tomb");
        for (int i = 0; i < blocks.size(); i++) {
            assertSame(blocks.get(i), out.get(i));
        }
    }

    @Test
    void onlyABedShapedBlockIsATomb() {
        assertTrue(SarcophagusMarkerProcessor.isTombShaped(TOMB));
        assertTrue(SarcophagusMarkerProcessor.isTombShaped(Blocks.RED_BED.defaultBlockState()));
        assertFalse(SarcophagusMarkerProcessor.isTombShaped(Blocks.CHEST.defaultBlockState()));
    }
}

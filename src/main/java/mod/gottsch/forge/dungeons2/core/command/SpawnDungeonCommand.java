package mod.gottsch.forge.dungeons2.core.command;

import com.mojang.brigadier.CommandDispatcher;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLayoutRenderer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.IRoomGenerator;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Debug command: plan a dungeon at a position and write it directly to the
 * world, bypassing the Structure / datapack system. This is the Phase 2/3
 * visual smoke test &mdash; it exercises the {@link DungeonStackPlanner} and
 * {@link DungeonLayoutRenderer} end-to-end so you can walk a generated dungeon
 * before the vanilla worldgen plumbing exists.
 *
 * <p>Usage: {@code /d2-generate <pos>} where {@code pos} is the surface
 * entrance opening. The dungeon builds downward from there. Single-floor,
 * SMALL tier for now. Entrance and END (terminal) rooms have no {@code .nbt}
 * templates yet, so they're rendered as synthetic placeholders &mdash; a 3x3
 * ladder shaft for the entrance and a ladder in the END room.</p>
 *
 * @author Mark Gottschling
 */
public class SpawnDungeonCommand {

    private static final BlockState BACKING = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState GLOWSTONE = Blocks.GLOWSTONE.defaultBlockState();
    /** Ladder facing SOUTH is attached to the block on its north side. */
    private static final BlockState LADDER =
            Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher
                .register(Commands.literal("d2-generate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(source ->
                                        spawn(source.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(source, "pos")))
                        )
                );
    }

    private static int spawn(CommandSourceStack sourceStack, BlockPos pos) {
        try {
            ServerLevel level = sourceStack.getLevel();
            long seed = pos.asLong();
            int surfaceY = pos.getY();
            ICoords anchor = new Coords(pos.getX(), 0, pos.getZ());

            Optional<DungeonLayout> result = new DungeonStackPlanner(
                    seed, anchor, surfaceY, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.SMALL)
                    .withFloorCount(1)
                    .plan();

            if (result.isEmpty()) {
                sourceStack.sendFailure(Component.literal("Dungeon planning failed at " + pos));
                return 0;
            }
            DungeonLayout layout = result.get();

            // World origin: shift so the entrance footprint's center lands at pos XZ.
            Rectangle2D ent = layout.getEntrance().getFootprint();
            int worldOriginX = pos.getX() - ent.getCenterX();
            int worldOriginZ = pos.getZ() - ent.getCenterY();

            RandomSource random = RandomSource.create(seed);
            List<BlockPlacement> placements = new ArrayList<>();

            // Synthetic START/END room boxes FIRST so the renderer's corridors and
            // doors (which come last) win at any shared perimeter cells. These stand
            // in for the entrance / transition templates until .nbt files exist.
            IRoomGenerator roomGen = new BasicRoomGenerator();
            for (FloorLayout floor : layout.getFloors()) {
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) {
                        roomGen.build(room, floor.getFloorY(), DungeonMotif.CLASSIC, random, placements);
                    }
                }
            }
            // Procedural rooms / corridors / doors.
            placements.addAll(new DungeonLayoutRenderer().render(layout, random));

            // Write everything (floor-local XZ + world origin; Y already absolute).
            int written = 0;
            for (BlockPlacement p : placements) {
                BlockPos worldPos = new BlockPos(
                        worldOriginX + p.getX(), p.getY(), worldOriginZ + p.getZ());
                level.setBlock(worldPos, BlockStateCodec.resolve(p), Block.UPDATE_CLIENTS);
                written++;
            }

            int floor0Y = layout.getFloors().get(0).getFloorY();
            carveEntranceShaft(level, layout, pos.getX(), pos.getZ(), surfaceY);
            placeSurfaceMarker(level, pos.getX(), pos.getZ(), surfaceY);
            BlockPos endLadder = carveEndLadder(level, layout, worldOriginX, worldOriginZ);

            final int total = written;
            final String endStr = endLadder != null
                    ? endLadder.getX() + " " + endLadder.getY() + " " + endLadder.getZ()
                    : "(none)";
            Dungeons.LOGGER.info("d2-generate: {}", layout.describe());
            sourceStack.sendSuccess(() -> Component.literal(
                    "Generated dungeon (" + total + " blocks)."
                            + "\n  Entrance shaft (descend here): "
                            + pos.getX() + " " + surfaceY + " " + pos.getZ()
                            + "  →  floor0 Y=" + floor0Y
                            + "\n  End/ladder room: " + endStr
                            + "\n  Glowstone pillar marks the entrance on the surface."), true);
        } catch (Exception e) {
            Dungeons.LOGGER.error("d2-generate failed: ", e);
            sourceStack.sendFailure(Component.literal("d2-generate error: " + e.getMessage()));
        }
        return 1;
    }

    /** A 3x3 air shaft with a north-wall ladder from floor 0 up to the surface. */
    private static void carveEntranceShaft(ServerLevel level, DungeonLayout layout,
                                           int cx, int cz, int surfaceY) {
        int floor0Y = layout.getFloors().get(0).getFloorY();
        for (int y = floor0Y; y <= surfaceY; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(new BlockPos(cx + dx, y, cz + dz), AIR, Block.UPDATE_CLIENTS);
                }
            }
            // Backing block to the north, ladder on its south face.
            level.setBlock(new BlockPos(cx, y, cz - 1), BACKING, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(cx, y, cz), LADDER, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * A ladder up the north interior wall of the bottom floor's END room.
     * Returns the ladder's base position (for chat feedback), or null if there
     * is no END room.
     */
    private static BlockPos carveEndLadder(ServerLevel level, DungeonLayout layout,
                                           int worldOriginX, int worldOriginZ) {
        FloorLayout bottom = layout.getFloors().get(layout.getFloors().size() - 1);
        RoomData end = bottom.getRooms().stream()
                .filter(r -> r.getRole() == RoomRole.END)
                .findFirst().orElse(null);
        if (end == null) {
            return null;
        }
        int ex = worldOriginX + end.getOriginX() + end.getWidth() / 2;
        int ez = worldOriginZ + end.getOriginZ() + 1; // one cell in from the north wall
        int floorY = bottom.getFloorY();
        for (int y = floorY + 1; y < floorY + end.getHeight() - 1; y++) {
            level.setBlock(new BlockPos(ex, y, ez), LADDER, Block.UPDATE_CLIENTS);
        }
        return new BlockPos(ex, floorY, ez);
    }

    /** A short glowstone pillar 2 cells east of the shaft so the entrance is findable. */
    private static void placeSurfaceMarker(ServerLevel level, int cx, int cz, int surfaceY) {
        int mx = cx + 2;
        for (int y = surfaceY; y <= surfaceY + 4; y++) {
            level.setBlock(new BlockPos(mx, y, cz), GLOWSTONE, Block.UPDATE_CLIENTS);
        }
    }
}

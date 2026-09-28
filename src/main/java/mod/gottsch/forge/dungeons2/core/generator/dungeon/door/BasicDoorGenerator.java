/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.SecretDoorway;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one doorway as a 4-block column of {@link BlockPlacement}s.
 *
 * <p>Column layout (relative to floor surface Y):</p>
 * <ul>
 *     <li>Y = floorY: door-sill block (a floor-style block from
 *         {@link DoorPattern#FLOOR})</li>
 *     <li>Y = floorY+1: door lower half (or air if there's no valid facing)</li>
 *     <li>Y = floorY+2: door upper half (or air)</li>
 *     <li>Y = floorY+3: lintel block ({@link DoorPattern#LINTEL})</li>
 * </ul>
 *
 * <p>The {@link DoorData#getFacing()} direction (planner-resolved at Phase 1
 * convert time) is used to set the {@code FACING} property of both door
 * halves. Doors with {@link Direction2D#NONE} facing emit air halves instead
 * of doors &mdash; the column is still walkable but has no actual door.</p>
 *
 * <p>{@code DoorConfig#probability} produces the <em>same</em> outcome deliberately: an opening
 * that loses its roll is exactly the doorless-but-framed column that a NONE facing already
 * produced, so there is one code path for "no door here" rather than two. The sill and lintel are
 * emitted before the roll, because they frame the opening whether or not anything hangs in it.</p>
 *
 * @author Mark Gottschling on Dev 7, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicDoorGenerator implements IDoorGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;
    /** Non-null when this doorway is a secret room's: see {@link #withSecret}. */
    private SecretDoorway secret;
    private String hiddenDoor;

    /**
     * Makes this a secret doorway: a hidden door instead of the motif's, the wall block for a
     * lintel, and a lever sconce (and, where there is wall for it, a decoy torch sconce) on the
     * corridor side. {@code hiddenDoor} is the DungeonBlocks door in the room's wall stone.
     */
    public BasicDoorGenerator withSecret(SecretDoorway secret, String hiddenDoor) {
        this.secret = secret;
        this.hiddenDoor = hiddenDoor;
        return this;
    }

    /** See {@code BasicWallGenerator#withMotifConfig}. */
    public BasicDoorGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    @Override
    public void build(DoorData door, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        int x = door.getX();
        int z = door.getZ();

        if (secret != null && hiddenDoor != null) {
            buildSecret(x, z, floorY, random, out);
            return;
        }

        // Sill (floor) and lintel (top).
        out.add(BlockStateCodec.placement(x, floorY, z, motifConfig.door().floorState()));
        out.add(BlockStateCodec.placement(x, floorY + 3, z, motifConfig.door().lintelState()));

        // Door halves. The roll happens even when it cannot change the outcome (probability 1.0, or
        // no facing) so that the random advances identically either way -- this generator is called
        // per door from a per-door seed, so it costs nothing, and it keeps a config change from
        // silently reshuffling anything downstream that shares the sequence.
        boolean hung = random.nextDouble() < motifConfig.door().probability();

        Direction direction = toMcDirection(door.getFacing());
        BlockState doorBase = motifConfig.door().doorState();
        if (direction != null && hung) {
            BlockState lower = doorBase.setValue(DoorBlock.FACING, direction)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            BlockState upper = doorBase.setValue(DoorBlock.FACING, direction)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            out.add(BlockStateCodec.placement(x, floorY + 1, z, lower));
            out.add(BlockStateCodec.placement(x, floorY + 2, z, upper));
        } else {
            // No valid facing, or this opening simply has no door: emit air halves so the doorway
            // is at least walkable.
            BlockState airState = Blocks.AIR.defaultBlockState();
            out.add(BlockStateCodec.placement(x, floorY + 1, z, airState));
            out.add(BlockStateCodec.placement(x, floorY + 2, z, airState));
        }
    }

    /**
     * The secret doorway: sill, hidden door, a lintel in the WALL block, and the lever.
     *
     * <ul>
     *   <li><strong>Always hung.</strong> {@code door.probability} is about ruin, and an open
     *       secret door is not a secret. The probability draw is still made so the stream is the
     *       one every other doorway uses.</li>
     *   <li><strong>The lintel is the wall block</strong>, not {@code door.lintel}: a polished
     *       andesite lintel over "more wall" gives the game away.</li>
     *   <li><strong>{@code facing} is inward</strong>, from the corridor into the room, which puts
     *       the shut panel flush with the corridor's wall face.</li>
     *   <li>The lever sconce hangs on the corridor face of the wall cell beside the door, at the
     *       door's upper row. Pulled, it strongly powers that cell, which touches the door. The wall
     *       cell is re-laid here too, so whatever the room's own weathering did to it, the lever has
     *       a whole block to hang on and to power.</li>
     * </ul>
     *
     * <p>Every block but the sill is UNDECORATED: the weathering and decoration passes never see
     * it. A crumbled lintel over an intact wall is a tell, and a lever whose wall weathers to air
     * pops off.</p>
     */
    private void buildSecret(int x, int z, int floorY, RandomSource random, List<BlockPlacement> out) {
        BlockState wall = motifConfig.wall().wallState();
        random.nextDouble(); // the hung roll every doorway makes; see build()
        String hinge = random.nextBoolean() ? "left" : "right";
        String inward = secret.inward().name().toLowerCase(java.util.Locale.ROOT);
        String outward = secret.outward().name().toLowerCase(java.util.Locale.ROOT);

        out.add(BlockStateCodec.placement(x, floorY, z, motifConfig.door().floorState()));
        out.add(hiddenDoorHalf(x, floorY + 1, z, inward, hinge, "lower"));
        out.add(hiddenDoorHalf(x, floorY + 2, z, inward, hinge, "upper"));
        out.add(BlockStateCodec.placement(x, floorY + 3, z, wall).undecorated());

        int row = floorY + 2;
        out.add(BlockStateCodec.placement(secret.leverWall().getX(), row, secret.leverWall().getY(),
                wall).undecorated());
        out.add(new BlockPlacement(secret.lever().getX(), row, secret.lever().getY(),
                HiddenDoors.LEVER_SCONCE, Map.of("facing", outward, "powered", "false")).undecorated());
        if (secret.decoy()) {
            out.add(BlockStateCodec.placement(secret.decoyWall().getX(), row,
                    secret.decoyWall().getY(), wall).undecorated());
            out.add(new BlockPlacement(secret.decoyCell().getX(), row, secret.decoyCell().getY(),
                    HiddenDoors.TORCH_SCONCE, Map.of("facing", outward)).undecorated());
        }
    }

    private BlockPlacement hiddenDoorHalf(int x, int y, int z, String facing, String hinge,
                                          String half) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("facing", facing);
        properties.put("half", half);
        properties.put("hinge", hinge);
        properties.put("open", "false");
        properties.put("powered", "false");
        return new BlockPlacement(x, y, z, hiddenDoor, properties).undecorated();
    }

    /** Map planner-level {@link Direction2D} to Minecraft {@link Direction}, or null for NONE. */
    private static Direction toMcDirection(Direction2D facing) {
        if (facing == null) return null;
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            default -> null;
        };
    }
}

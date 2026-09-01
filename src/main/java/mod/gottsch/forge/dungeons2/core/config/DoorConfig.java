/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The doorway section of a {@link MotifConfig}: the door itself plus the sill ({@code floor}) it
 * stands on and the {@code lintel} above it. See {@code BasicDoorGenerator} for the 4-block column
 * these fill.
 *
 * <p>{@code door} is expected to be an actual {@code DoorBlock} &mdash; the generator sets
 * {@code FACING}/{@code HALF} on it. Its fallback is {@code minecraft:oak_door} rather than the
 * stone_bricks the other slots use, since a non-door block there would silently lose those
 * properties.</p>
 *
 * <h2>probability &mdash; not every opening carries a door</h2>
 * <p>{@link #probability} is the chance a doorway gets an actual door block; the rest are open
 * doorways. A dungeon where every single opening is hung with a working door reads as a building
 * that is still maintained, which is the opposite of the thing being generated: some doors have
 * rotted off their hinges. The sill and lintel are placed either way, so a doorless opening is
 * still a <em>framed</em> opening and still reads as deliberate architecture rather than as a hole.
 * </p>
 *
 * <p>Defaults to 1.0 (every opening doored), which is what the field did before it existed. The
 * roll is made from the door piece's own stable seed, so a doorway does not gain or lose its door
 * between chunk loads &mdash; see {@code DungeonDoorPiece#renderPlacements}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record DoorConfig(String door, String lintel, String floor, double probability) {

    public static final DoorConfig DEFAULT =
            new DoorConfig("minecraft:oak_door", "minecraft:stone_bricks", "minecraft:stone_bricks");

    /** Every opening gets a door, the behaviour before {@code probability} existed. */
    public DoorConfig(String door, String lintel, String floor) {
        this(door, lintel, floor, 1.0);
    }

    public static final Codec<DoorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codecs.BLOCK_ID.fieldOf("door").forGetter(DoorConfig::door),
            Codecs.BLOCK_ID.fieldOf("lintel").forGetter(DoorConfig::lintel),
            Codecs.BLOCK_ID.fieldOf("floor").forGetter(DoorConfig::floor),
            // Optional, unlike its siblings: it is a shape knob rather than a material, so there is
            // a meaningful default to fall back on. The blocks have none on purpose.
            Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "probability", 1.0)
                    .forGetter(DoorConfig::probability)
    ).apply(instance, DoorConfig::new));

    public BlockState doorState() {
        return BlockStateCodec.block(door, Blocks.OAK_DOOR);
    }

    public BlockState lintelState() {
        return BlockStateCodec.block(lintel, Blocks.STONE_BRICKS);
    }

    public BlockState floorState() {
        return BlockStateCodec.block(floor, Blocks.STONE_BRICKS);
    }
}

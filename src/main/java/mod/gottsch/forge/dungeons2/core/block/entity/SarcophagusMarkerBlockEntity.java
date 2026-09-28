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
package mod.gottsch.forge.dungeons2.core.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The per-cell data a {@code dungeons2:sarcophagus_marker} carries (#104).
 *
 * <p>Every field is an override and absent means the {@code dungeons2:sarcophagus} processor
 * entry's, as on the other three markers: a template full of tombs states nothing, and one special
 * tomb states one line. The contents keys mirror the entry's {@code TombContents} one for one, but
 * name ONE table and ONE mob rather than weighted lists &mdash; a marker is a single tomb.</p>
 *
 * <p>Fields rather than pass-through keys, for the spawner marker's reason: a key this entity has
 * no field for is dropped at its next save, which is before the structure block writes the
 * template, so the {@code /data merge} would look applied and never reach the {@code .nbt}.</p>
 *
 * <p>Nothing here has runtime behaviour. By the time the world runs, the marker is the foot of the
 * tomb it described.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public class SarcophagusMarkerBlockEntity extends BlockEntity {

    /** The table this tomb may spill, e.g. {@code dungeons2:chests/classic_tomb_deep}. */
    public static final String LOOT_TABLE = "lootTable";
    /** The mob this tomb may raise, e.g. {@code minecraft:skeleton}. */
    public static final String GUARDIAN = "guardian";
    /** The three weights of DungeonBlocks' one roll; see {@code TombContents}. */
    public static final String LOOT_WEIGHT = "lootWeight";
    public static final String GUARDIAN_WEIGHT = "guardianWeight";
    public static final String EMPTY_WEIGHT = "emptyWeight";
    /**
     * The floor this tomb's guardian is scaled for, with {@link #MOTIF} beside it &mdash; the
     * spawner marker's pair, and like it a FIXED authored depth, not the floor the room lands on.
     * Without both the guardian is left to Stronger Mobs Below.
     */
    public static final String FLOOR_INDEX = "floorIndex";
    public static final String MOTIF = "motif";

    private String lootTable;
    private String guardian;
    private Integer lootWeight;
    private Integer guardianWeight;
    private Integer emptyWeight;
    private Integer floorIndex;
    private String motif;

    public SarcophagusMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonsBlockEntities.SARCOPHAGUS_MARKER.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(LOOT_TABLE, Tag.TAG_STRING)) {
            this.lootTable = tag.getString(LOOT_TABLE);
        }
        if (tag.contains(GUARDIAN, Tag.TAG_STRING)) {
            this.guardian = tag.getString(GUARDIAN);
        }
        // TAG_ANY_NUMERIC: `lootWeight:3b` and `lootWeight:3` are the same thing to an author.
        if (tag.contains(LOOT_WEIGHT, Tag.TAG_ANY_NUMERIC)) {
            this.lootWeight = Math.max(0, tag.getInt(LOOT_WEIGHT));
        }
        if (tag.contains(GUARDIAN_WEIGHT, Tag.TAG_ANY_NUMERIC)) {
            this.guardianWeight = Math.max(0, tag.getInt(GUARDIAN_WEIGHT));
        }
        if (tag.contains(EMPTY_WEIGHT, Tag.TAG_ANY_NUMERIC)) {
            this.emptyWeight = Math.max(0, tag.getInt(EMPTY_WEIGHT));
        }
        if (tag.contains(FLOOR_INDEX, Tag.TAG_ANY_NUMERIC)) {
            this.floorIndex = tag.getInt(FLOOR_INDEX);
        }
        if (tag.contains(MOTIF, Tag.TAG_STRING)) {
            this.motif = tag.getString(MOTIF);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Only what was stated, so an unconfigured marker saves no opinions into the template.
        if (lootTable != null && !lootTable.isEmpty()) {
            tag.putString(LOOT_TABLE, lootTable);
        }
        if (guardian != null && !guardian.isEmpty()) {
            tag.putString(GUARDIAN, guardian);
        }
        if (lootWeight != null) {
            tag.putInt(LOOT_WEIGHT, lootWeight);
        }
        if (guardianWeight != null) {
            tag.putInt(GUARDIAN_WEIGHT, guardianWeight);
        }
        if (emptyWeight != null) {
            tag.putInt(EMPTY_WEIGHT, emptyWeight);
        }
        if (floorIndex != null) {
            tag.putInt(FLOOR_INDEX, floorIndex);
        }
        if (motif != null && !motif.isEmpty()) {
            tag.putString(MOTIF, motif);
        }
    }
}

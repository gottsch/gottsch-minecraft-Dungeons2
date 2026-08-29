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

import mod.gottsch.forge.dungeons2.core.data.PotionEffectSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Backlog #56: the per-cell authored data behind a {@code dungeons2:pot_marker}.
 *
 * <h2>Why this follows the CHEST marker and not the spawner marker</h2>
 * <p>{@code SpawnerMarkerBlock} is a plain block: every one of them means the same thing, and the
 * processor that swaps it needs no per-cell information. A pot marker does &mdash; how likely, how
 * many, which variants, what loot, what effects &mdash; and a template holds several of them saying
 * different things. That is the same requirement {@code chest_marker} has, and a block entity is
 * how D2 already answers it (#48).</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code probability} &mdash; 0..1, whether this marker produces anything at all. 1 by
 *       default, so a marker placed and never configured is simply "a pot stands here".</li>
 *   <li>{@code minCount} / {@code maxCount} &mdash; how many pots at THIS cell. 1/1 by default. A
 *       marker is one cell, so this is a cluster count and not the room-wide count
 *       {@code PotConfig} rolls; the two words mean different things because they answer to
 *       different scopes.</li>
 *   <li>{@code variants} &mdash; weighted entity ids, the same shape as {@code PotConfig.PotVariant}
 *       and for the same reason: "mostly the tall pot, occasionally a squat one" without repeating
 *       ids. This is also how a template asks for the RED and BLUE palettes, which are reserved for
 *       authored rooms by convention and were unreachable before this marker existed.</li>
 *   <li>{@code lootTable} &mdash; a pot's contents come from a per-entity loot table. Unset means
 *       the pot shatters into nothing, exactly as {@code PotConfig} documents.</li>
 *   <li>{@code effects} &mdash; see {@link PotionEffectSpec}. Live on a {@code PotionEntity}
 *       variant, inert on a plain pot.</li>
 * </ul>
 *
 * <h2>Every field is optional and read back defensively</h2>
 * <p>These are authored by hand in a dev world and then saved into a structure {@code .nbt}, so the
 * tag that comes back is whatever the author actually set. {@code load} therefore fills in defaults
 * rather than trusting the tag's shape, and {@code saveAdditional} writes only what differs from
 * the default &mdash; which keeps an unconfigured marker's tag empty and the {@code .nbt} diff
 * readable.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public class PotMarkerBlockEntity extends BlockEntity {

    public static final String PROBABILITY = "probability";
    public static final String MIN_COUNT = "minCount";
    public static final String MAX_COUNT = "maxCount";
    public static final String VARIANTS = "variants";
    public static final String LOOT_TABLE = "lootTable";
    public static final String EFFECTS = "effects";

    public static final String ENTITY = "entity";
    public static final String WEIGHT = "weight";
    public static final String EFFECT = "effect";
    public static final String AMPLIFIER = "amplifier";
    public static final String DURATION = "duration";

    /** One weighted pot entity id. Mirrors {@code PotConfig.PotVariant} in NBT. */
    public record Variant(String entity, int weight) {
    }

    private float probability = 1.0F;
    private int minCount = 1;
    private int maxCount = 1;
    private String lootTable;
    private List<Variant> variants = List.of();
    private List<PotionEffectSpec> effects = List.of();

    public PotMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonsBlockEntities.POT_MARKER.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(PROBABILITY)) {
            this.probability = Math.max(0.0F, Math.min(1.0F, tag.getFloat(PROBABILITY)));
        }
        if (tag.contains(MIN_COUNT)) {
            this.minCount = Math.max(0, tag.getInt(MIN_COUNT));
        }
        if (tag.contains(MAX_COUNT)) {
            this.maxCount = Math.max(0, tag.getInt(MAX_COUNT));
        }
        // A max under min is an authoring slip, not a reason to place nothing: clamping up reads as
        // "at least this many", which is what someone who typed them the wrong way round meant.
        this.maxCount = Math.max(this.minCount, this.maxCount);
        if (tag.contains(LOOT_TABLE)) {
            String table = tag.getString(LOOT_TABLE);
            this.lootTable = table.isEmpty() ? null : table;
        }
        if (tag.contains(VARIANTS, Tag.TAG_LIST)) {
            List<Variant> read = new ArrayList<>();
            ListTag list = tag.getList(VARIANTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String entity = entry.getString(ENTITY);
                if (entity.isEmpty()) {
                    continue;
                }
                // A missing or zero weight means 1, not "never drawn". An author writing a bare
                // entity id means they want it, and a silently undrawable variant is the kind of
                // thing that is only noticed several dungeons later.
                int weight = entry.contains(WEIGHT) ? Math.max(1, entry.getInt(WEIGHT)) : 1;
                read.add(new Variant(entity, weight));
            }
            this.variants = List.copyOf(read);
        }
        if (tag.contains(EFFECTS, Tag.TAG_LIST)) {
            List<PotionEffectSpec> read = new ArrayList<>();
            ListTag list = tag.getList(EFFECTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String effect = entry.getString(EFFECT);
                if (effect.isEmpty()) {
                    continue;
                }
                read.add(new PotionEffectSpec(effect, entry.getInt(AMPLIFIER),
                        // 0 ticks is an effect nobody would see. Default to 10 seconds so a marker
                        // that names an effect and nothing else does something visible.
                        entry.contains(DURATION) ? Math.max(1, entry.getInt(DURATION)) : 200));
            }
            this.effects = List.copyOf(read);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Written back so a marker configured in a dev world survives being saved into a structure
        // .nbt -- the only way these fields are ever authored. Defaults are omitted rather than
        // written, so an unconfigured marker carries no tag at all.
        if (probability != 1.0F) {
            tag.putFloat(PROBABILITY, probability);
        }
        if (minCount != 1) {
            tag.putInt(MIN_COUNT, minCount);
        }
        if (maxCount != 1) {
            tag.putInt(MAX_COUNT, maxCount);
        }
        if (lootTable != null && !lootTable.isEmpty()) {
            tag.putString(LOOT_TABLE, lootTable);
        }
        if (!variants.isEmpty()) {
            ListTag list = new ListTag();
            for (Variant variant : variants) {
                CompoundTag entry = new CompoundTag();
                entry.putString(ENTITY, variant.entity());
                entry.putInt(WEIGHT, variant.weight());
                list.add(entry);
            }
            tag.put(VARIANTS, list);
        }
        if (!effects.isEmpty()) {
            ListTag list = new ListTag();
            for (PotionEffectSpec spec : effects) {
                CompoundTag entry = new CompoundTag();
                entry.putString(EFFECT, spec.effect());
                entry.putInt(AMPLIFIER, spec.amplifier());
                entry.putInt(DURATION, spec.duration());
                list.add(entry);
            }
            tag.put(EFFECTS, list);
        }
    }

    public float getProbability() {
        return probability;
    }

    public void setProbability(float probability) {
        this.probability = Math.max(0.0F, Math.min(1.0F, probability));
    }

    public int getMinCount() {
        return minCount;
    }

    public void setMinCount(int minCount) {
        this.minCount = Math.max(0, minCount);
        this.maxCount = Math.max(this.minCount, this.maxCount);
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = Math.max(this.minCount, Math.max(0, maxCount));
    }

    public String getLootTable() {
        return lootTable;
    }

    public void setLootTable(String lootTable) {
        this.lootTable = lootTable == null || lootTable.isEmpty() ? null : lootTable;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public List<PotionEffectSpec> getEffects() {
        return effects;
    }

    public void setEffects(List<PotionEffectSpec> effects) {
        this.effects = effects == null ? List.of() : List.copyOf(effects);
    }
}

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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Turns an {@link EntityPlacement} into a live entity. One implementation shared by the structure
 * piece ({@code DungeonPiece#placeEntities}, during worldgen) and the {@code /spawndungeon} debug
 * command (against a live {@code ServerLevel}), because the loot round trip below is subtle enough
 * that two copies would drift.
 *
 * <p><strong>This does no deduplication.</strong> Whether an entity should be spawned at all is the
 * caller's problem, and it is a real one during worldgen: a piece's {@code postProcess} runs once
 * per overlapping chunk, so the piece clips to the chunk box first. The command has no such
 * concern, running exactly once.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public final class EntitySpawner {

    private EntitySpawner() {}

    /**
     * Creates, positions and adds one entity. Returns {@code false} without throwing when the id
     * does not resolve or the type refuses to construct &mdash; a bad entity id is a datapack
     * problem, and losing a decorative prop is not worth aborting a dungeon over, the same
     * degrade-don't-abort convention an unresolved block id gets.
     *
     * <p>Loot is applied by a save/mutate/load round trip rather than by casting to a concrete
     * entity class. {@code saveWithoutId} produces a tag that already carries the {@code Pos} /
     * {@code Motion} / {@code Rotation} entries {@code Entity#load} requires, so adding
     * {@code LootTable} and {@code LootTableSeed} and loading it back sets those fields without
     * disturbing anything else. That keeps this generic over any entity honouring the vanilla loot
     * keys, and keeps Dungeons2 from compiling against {@code dungeonblocks}' {@code PotEntity}
     * &mdash; that mod is a content dependency, not an API one.</p>
     */
    public static boolean spawn(ServerLevelAccessor level, EntityPlacement placement,
                                int worldX, int worldY, int worldZ) {
        EntityType<?> type = EntityType.byString(placement.getEntityId()).orElse(null);
        if (type == null) {
            Dungeons.LOGGER.warn("Unknown entity id in placement: {}", placement.getEntityId());
            return false;
        }
        Entity entity = type.create(level.getLevel());
        if (entity == null) {
            Dungeons.LOGGER.warn("Entity type {} would not construct", placement.getEntityId());
            return false;
        }
        entity.moveTo(worldX + placement.getXOffset(), worldY, worldZ + placement.getZOffset(),
                placement.getYRot(), 0.0F);

        String lootTable = placement.getLootTable();
        if (lootTable != null && !lootTable.isBlank()) {
            CompoundTag tag = new CompoundTag();
            entity.saveWithoutId(tag);
            tag.putString("LootTable", new ResourceLocation(lootTable).toString());
            tag.putLong("LootTableSeed", placement.getLootTableSeed());
            entity.load(tag);
        }
        return level.addFreshEntity(entity);
    }
}

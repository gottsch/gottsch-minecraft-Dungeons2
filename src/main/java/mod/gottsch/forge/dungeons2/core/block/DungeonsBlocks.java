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
package mod.gottsch.forge.dungeons2.core.block;

import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Dungeons2's blocks.
 *
 * <p><strong>This class registered nothing at all until 2026-08-14</strong>, because nothing
 * referenced it and a {@code DeferredRegister} collects an entry only when the holding class is
 * first loaded. {@link Registration#init()} now touches it explicitly via {@link #register()}. See
 * {@link mod.gottsch.forge.dungeons2.core.block.entity.DungeonsBlockEntities} for the full
 * diagnosis, which is worth reading before adding anything here.</p>
 *
 * <p><strong>Phase 6 (backlog #43) removed {@code DEFERRED_DUNGEON_GENERATOR} on 2026-08-18</strong>,
 * along with its block, block entity and the whole unregistered feature path it existed for. This
 * class stays because live content shares it &mdash; which is exactly the split #43 called for.</p>
 *
 * @author Mark Gottschling Oct 25, 2023
 *
 */
public class DungeonsBlocks {

    /**
     * Backlog #10: the invisible proximity mob-set spawner. Air-<em>like</em> in every respect that
     * matters to the player &mdash; not visible, not collidable, not targetable, no drops &mdash;
     * because the dungeon around it is the content and this is only a trigger.
     *
     * <p>No {@code air()} and no {@code isAir} override. <strong>Not because they broke anything
     * &mdash; they did not.</strong> The spawner's "no block entity" symptom was GottschCore's
     * {@code SpawnUtil} discarding every mob and {@code execute()} self-destructing regardless; this
     * block worked throughout. Dropping {@code air()} is kept because a block entity host should not
     * claim to be air, not as a fix. See {@link MobSetSpawnerBlock#getShape}.</p>
     *
     * <p>Invisibility comes from {@code BaseEntityBlock}, whose {@code getRenderShape} is
     * {@code INVISIBLE}, so none of it needed to be bought with {@code air()} in the first place.
     * {@code noCollission} keeps the player walking through it and
     * {@link MobSetSpawnerBlock#getShape} makes it un-targetable, which together is the whole of
     * what {@code air()} was there for.</p>
     */
    public static final RegistryObject<Block> MOB_SET_SPAWNER = Registration.BLOCKS.register(
            "mob_set_spawner",
            () -> new MobSetSpawnerBlock(MobSetSpawnerBlock.properties()));

    /**
     * Backlog #10: the <strong>authoring</strong> marker. A template author places one of these; the
     * {@code dungeons2:spawner} processor swaps it for {@link #MOB_SET_SPAWNER} at placement, so it
     * is visible in the structure editor and gone in the finished dungeon.
     *
     * <p><strong>Why a real block and not a DATA structure block.</strong> The original design used
     * {@code d2:spawner} on a DATA structure block, and it cannot work:
     * {@code SinglePoolElement.getSettings} installs {@code BlockIgnoreProcessor.STRUCTURE_BLOCK}
     * <em>before</em> the pool's own processors, and that returns {@code null} for a structure block
     * &mdash; removing it from the placement list rather than replacing it. The pool's processors are
     * then handed a list it is already absent from, and the unwritten cell shows the terrain the
     * dungeon was carved out of. Pinned by {@code JigsawStripsStructureBlocksTest}. Village Dungeons
     * uses marker blocks for the same reason.</p>
     *
     * <p>An ordinary solid block: it must survive to the processor, so nothing air-like or
     * replaceable. {@code noLootTable} because it is authoring scaffolding, not content.</p>
     *
     * <p><strong>It carries a block entity as of 2026-09-03</strong>, like {@link #CHEST_MARKER}
     * and {@link #POT_MARKER} before it, so a single template can name its own mob set and trigger
     * distance instead of taking the pool's. The claim that "a block cannot carry free text", which
     * is why the mob set became a codec field on the processor in the first place, was only ever
     * true of a block with no block entity &mdash; see {@link SpawnerMarkerBlock}.</p>
     */
    public static final RegistryObject<Block> SPAWNER_MARKER = Registration.BLOCKS.register(
            "spawner_marker",
            () -> new SpawnerMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE).noLootTable()));

    /**
     * Backlog #48 step 3: the chest authoring marker. Unlike {@link #SPAWNER_MARKER} this one has a
     * block entity, because which loot table a chest draws is a per-cell decision and a template
     * holds several chests -- see {@link ChestMarkerBlock}.
     *
     * <p>Solid and visible for the same reason the spawner marker is: it must survive to the
     * processor, and its author has to be able to see it while building.</p>
     */
    public static final RegistryObject<Block> CHEST_MARKER = Registration.BLOCKS.register(
            "chest_marker",
            () -> new ChestMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noLootTable()));

    /**
     * Backlog #56: the pot authoring marker. Carries a block entity for the same reason
     * {@link #CHEST_MARKER} does &mdash; how likely, how many, which variants, what loot and what
     * potion effects are all PER-MARKER decisions, and a template holds several markers saying
     * different things, where a codec field on the processor is per POOL.
     *
     * <p>Solid and visible so it survives to the processor and its author can see it while
     * building. Unlike the chest marker it has no {@code FACING}: a pot's rotation is rolled, so
     * the property would look meaningful and do nothing.</p>
     */
    public static final RegistryObject<Block> POT_MARKER = Registration.BLOCKS.register(
            "pot_marker",
            () -> new PotMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.5F)
                    .sound(SoundType.STONE).noLootTable()));

    /**
     * Forces this class to load so the fields above actually register. Called from
     * {@link Registration#init()}; see that method's comment for why a holder nothing references
     * registers nothing at all.
     */
    public static void register() {
        // Intentionally empty -- class loading is the whole point.
    }
}

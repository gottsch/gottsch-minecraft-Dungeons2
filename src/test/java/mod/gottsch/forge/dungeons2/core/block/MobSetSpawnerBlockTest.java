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
package mod.gottsch.forge.dungeons2.core.block;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawner block's shape contract: invisible and intangible, but <strong>not air</strong>.
 *
 * <h2>Read this before deciding what it proves</h2>
 * <p>These assertions were written while chasing a spawner that placed and then did nothing, on the
 * theory that {@code BlockBehaviour.Properties.air()} (copied from Village Dungeons) cost the block
 * its block entity. <strong>That theory was wrong twice over.</strong> First
 * {@link #theSpawnerActuallyCarriesABlockEntity} passes with {@code air()} set, so it is not true at
 * the state level. Then removing {@code air()} changed nothing in game.</p>
 *
 * <p>The actual fault was in GottschCore's {@code SpawnUtil.spawnMob}: it read Forge's ordinary
 * {@code onFinalizeSpawn} return of {@code null} as a cancelled spawn, discarded the mob on all 20
 * attempts and returned empty, after which {@code execute()} called {@code selfDestruct()} regardless
 * and deleted the block and its entity. {@code /data get block} reporting "not a Block Entity" was
 * accurate about a cell that had already erased itself. <strong>This block was working the whole
 * time.</strong></p>
 *
 * <p>So these tests are a <em>specification</em>, not a regression guard for a bug that lived here:
 * a block entity host should not claim to be air, and invisibility should come from
 * {@code BaseEntityBlock}'s {@code INVISIBLE} render shape, {@code noCollission} and an empty
 * outline instead. Worth keeping on those grounds; worth not mistaking for evidence.</p>
 *
 * @author Mark Gottschling on Aug 16, 2026
 */
class MobSetSpawnerBlockTest {

    private static MobSetSpawnerBlock block;

    /**
     * Constructing a {@code Block} allocates an intrusive holder in {@code BuiltInRegistries.BLOCK},
     * which {@code Bootstrap} leaves frozen &mdash; "Registry is already frozen". Forge's own
     * {@code unfreeze()} hook is the way round it, the same one {@code TestRegistries} uses for
     * processor types.
     *
     * <p><strong>Constructed but not registered, and left thawed.</strong> Forge's
     * {@code NamespacedWrapper} is <em>locked</em> independently of vanilla's frozen flag, so
     * {@code Registry.register} into the block registry is refused outright in a unit test
     * ("Modder should use Forge Register methods") &mdash; and re-freezing then trips over the
     * unregistered intrusive holder. None of that is needed: every property under test lives on
     * the constructed {@code BlockState}, not on the registry entry. Freezing only guards writes,
     * every other test here reads, and the suite is green. Noted rather than done silently.</p>
     */
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        block = new MobSetSpawnerBlock(MobSetSpawnerBlock.properties());
    }

    private static MobSetSpawnerBlock block() {
        return block;
    }

    @Test
    void theSpawnerStateIsNotAir() {
        assertFalse(block().defaultBlockState().isAir(),
                "a block entity host should not claim to be air, and this one has no need to:"
                        + " BaseEntityBlock already renders it invisible, noCollission lets the"
                        + " player through, and getShape makes it un-targetable.");
    }

    @Test
    void theSpawnerActuallyCarriesABlockEntity() {
        MobSetSpawnerBlock block = block();
        BlockState state = block.defaultBlockState();

        assertInstanceOf(EntityBlock.class, block, "must be an EntityBlock to hold the spawner");
        assertTrue(state.hasBlockEntity(),
                "the state must report a block entity. NOTE this passes with air() set too, which"
                        + " is how the air() theory was disproved -- see the class notes");
        // newBlockEntity() is deliberately NOT called: it resolves the BlockEntityType through a
        // RegistryObject, which only populates during mod loading. hasBlockEntity() above is the
        // property that actually broke, and it is reachable here.
    }

    /** What {@code air()} was reached for, provided by other means that do not lie about the block. */
    @Test
    void itStillBehavesLikeAirToThePlayer() {
        MobSetSpawnerBlock block = block();
        BlockState state = block.defaultBlockState();

        assertEquals(RenderShape.INVISIBLE, block.getRenderShape(state),
                "BaseEntityBlock gives invisibility -- air() was never needed for it");
        assertTrue(state.getCollisionShape(null, null).isEmpty(),
                "the player must walk through it");
        assertTrue(block.getShape(state, null, BlockPos.ZERO, null).isEmpty(),
                "an empty outline stops it being highlighted or broken -- otherwise the room has a"
                        + " phantom block the player can target but not see");
    }
}

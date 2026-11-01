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
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
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
 * <h2>2026-09-16: "this block was working the whole time" was true of that bug and not of this one</h2>
 * <p>A second, unrelated fault DID live here, and the paragraph above is exactly why it was not
 * looked for: <strong>water deleted the block.</strong> The {@code noCollission()} this class spent
 * three paragraphs defending is what made {@code FlowingFluid.canHoldFluid} treat the cell as
 * free &mdash; it ends in {@code !state.blocksMotion()} &mdash; so in a flooded room the first
 * fluid spread replaced the spawner with water and the block entity was gone before any player
 * could trigger it. Every land spawner was unaffected, which is what kept it hidden.
 * {@link #waterCannotWashTheSpawnerAway} is the regression guard; see
 * {@code MobSetSpawnerBlock}'s own note for the full history.</p>
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
    /**
     * <strong>Water must not be able to destroy the spawner.</strong>
     *
     * <p>The regression guard for the sewer bug. {@code FlowingFluid.canSpreadTo} asks
     * {@code canHoldFluid}, which prefers the {@code LiquidBlockContainer} branch and otherwise
     * ends in {@code return !state.blocksMotion()}. This block's collision shape is empty by
     * design, so {@code blocksMotion()} is false and the fallthrough branch of {@code spreadTo}
     * &mdash; {@code beforeDestroyingBlock} then {@code setBlock(pos, water)} &mdash; was reachable.
     *
     * <p>Asserted as the two facts vanilla actually consults, rather than by driving a fluid tick:
     * being a {@code LiquidBlockContainer} is what diverts {@code canHoldFluid} away from the
     * {@code blocksMotion} test at all, and {@code canPlaceLiquid} accepting water is what makes
     * {@code spreadTo} call {@code placeLiquid} instead of destroying the block. Both are on the
     * block, need no level, and are exactly what broke.
     */
    @Test
    void waterCannotWashTheSpawnerAway() {
        MobSetSpawnerBlock spawner = block();
        BlockState state = spawner.defaultBlockState();

        assertFalse(state.blocksMotion(),
                "the spawner is intentionally intangible -- if this ever becomes true the fix below"
                        + " is no longer what is protecting it, and this test should be rewritten"
                        + " rather than deleted");
        assertInstanceOf(LiquidBlockContainer.class, spawner,
                "must be a LiquidBlockContainer: it is the ONLY branch of FlowingFluid.canHoldFluid"
                        + " that does not fall through to !blocksMotion(), and falling through means"
                        + " spreadTo replaces the spawner (and its block entity) with water");
        assertTrue(spawner.canPlaceLiquid(null, BlockPos.ZERO, state, Fluids.WATER),
                "must accept water, so spreadTo calls placeLiquid and flips WATERLOGGED instead of"
                        + " destroying the block");
    }

    /**
     * The default state must be dry.
     *
     * <p>{@code stateDefinition.any()} resolves every unnamed boolean to TRUE, so a
     * {@code registerDefaultState} that forgot to name {@code WATERLOGGED} would ship a spawner
     * that reports water in every cell it is placed in &mdash; including the dry ones, where
     * {@code getFluidState} would then hand {@code IN_WATER} mobs a position that is not wet. The
     * same trap made a dungeonblocks lantern flood its own pit.
     */
    @Test
    void theSpawnerIsDryByDefault() {
        BlockState state = block().defaultBlockState();
        assertFalse(state.getValue(MobSetSpawnerBlock.WATERLOGGED),
                "default state must be waterlogged=false");
        assertTrue(block().getFluidState(state).isEmpty(),
                "a dry spawner must report no fluid");
        assertEquals(Fluids.WATER, block()
                        .getFluidState(state.setValue(MobSetSpawnerBlock.WATERLOGGED, Boolean.TRUE))
                        .getType(),
                "a waterlogged spawner must report water, so the cell still reads as water to"
                        + " NaturalSpawner's IN_WATER test -- the gate that decides whether a fish"
                        + " can be placed there");
    }
}

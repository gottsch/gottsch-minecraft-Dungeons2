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
package mod.gottsch.forge.dungeons2.diagnostic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An in-memory {@link WorldGenLevel} good enough to drive a {@code StructurePiece} through its real
 * {@code postProcess}.
 *
 * <h2>Why this exists</h2>
 * <p>Every other headless check in this project calls the generators directly, so it sees what a
 * piece <em>intends</em> to place. {@code postProcess} is where the intent becomes blocks, and it is
 * also where the weathering pass and {@code settleJoinShapes} run &mdash; neither of which any unit
 * test, the floor-plan viewer, or an ad-hoc probe has ever executed. Four defects in three sessions
 * lived in exactly that gap (the {@code updateShape} chunk-gen crash, outer corners never
 * populating, stairs weathering into dirt cubes, duplicated arch caps), and every one of them was
 * found by a person looking at a screenshot.</p>
 *
 * <h2>How it is built</h2>
 * <p>A {@link Proxy}, not a hand-written class. {@code WorldGenLevel} inherits something like eighty
 * methods across eight interfaces, of which {@code postProcess} touches about six; hand-stubbing the
 * rest would bury those six. Anything not handled below throws an
 * {@link UnsupportedOperationException} <em>naming the method</em>, so extending this is a matter of
 * running a test and reading the message rather than guessing. Interface {@code default} methods run
 * their own real implementations, which is how the inherited convenience overloads keep working.</p>
 *
 * <h2>What it deliberately is not</h2>
 * <ul>
 *   <li><strong>No chunks.</strong> {@code getChunk} returns one empty {@code ProtoChunk} for
 *       every position &mdash; a sink for vanilla's {@code markPosForPostprocessing} and nothing
 *       more (see {@code chunk()} on why null stopped working). Nothing here models chunk
 *       boundaries, so this cannot reproduce the "piece skipped in an already-generated chunk"
 *       class of bug.</li>
 *   <li><strong>No {@code ServerLevel}.</strong> {@code getLevel()} throws, so no entity is ever
 *       constructed here &mdash; entity creation needs a real {@code ServerLevel}. That costs less
 *       than it sounds and never blocked rooms: {@code DungeonPiece#placeEntities} degrades per
 *       placement rather than throwing, and headless it does not reach the throw at all, because
 *       {@code dungeonblocks} is off this classpath and {@code EntityType.byString} cannot resolve
 *       the pot ids. Rooms, corridors and doors all run their <em>block</em> half here &mdash; see
 *       {@code RoomPostProcessTest}. What stays unreachable is the entity half itself: the
 *       per-chunk spawn clip in {@code placeEntities} cannot be observed from here.</li>
 *   <li><strong>No terrain.</strong> Every unwritten position reads as air, so a processor rule
 *       keyed on the surrounding world sees air rather than stone.</li>
 * </ul>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
public final class FakeWorldGenLevel implements InvocationHandler {

    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final RegistryAccess registryAccess;
    private final WorldGenLevel proxy;

    private FakeWorldGenLevel(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
        this.proxy = (WorldGenLevel) Proxy.newProxyInstance(
                FakeWorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
    }

    /** A level backed by the shipped datapack content, which is what {@code postProcess} reads. */
    public static FakeWorldGenLevel create() {
        return new FakeWorldGenLevel(TestRegistries.get());
    }

    public WorldGenLevel level() {
        return proxy;
    }

    /** Every block written, in write order. */
    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public BlockState blockAt(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public Object invoke(Object self, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "getBlockState":
                return blockAt((BlockPos) args[0]);
            case "setBlock":
                blocks.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                return true;
            case "getFluidState":
                // Read off the block rather than hardcoded empty: vanilla's placeBlock schedules a
                // fluid tick when this is non-empty, and a motif that ever places water should get
                // that path rather than a quiet lie.
                return blockAt((BlockPos) args[0]).getFluidState();
            case "registryAccess":
                return registryAccess;
            case "getBlockEntity":
                return null;
            // A SINK for markPosForPostprocessing, and nothing more -- see chunk() below.
            case "getChunk":
                return chunk(self);
            case "scheduleTick":
            case "neighborChanged":
            case "blockUpdated":
                return null;
            case "getMinBuildHeight":
                return MIN_BUILD_HEIGHT;
            case "getHeight":
                // LevelHeightAccessor.getHeight() takes no args; the Heightmap overload does.
                if (args == null || args.length == 0) {
                    return HEIGHT;
                }
                break;
            case "getSeed":
                return 0L;
            case "isClientSide":
                return false;
            case "toString":
                return "FakeWorldGenLevel(" + blocks.size() + " blocks)";
            case "hashCode":
                return System.identityHashCode(self);
            case "equals":
                return self == args[0];
            default:
                break;
        }
        // Inherited convenience overloads (isStateAtPosition, getBlockState(int,int,int), ...) are
        // real default methods; running them keeps this consistent with the interface's contract
        // instead of reimplementing it.
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(self, method, args);
        }
        throw new UnsupportedOperationException(
                "FakeWorldGenLevel does not implement " + method.getDeclaringClass().getSimpleName()
                        + "." + method.getName() + "() -- add it to the switch in "
                        + FakeWorldGenLevel.class.getSimpleName() + ".invoke if postProcess now needs it");
    }

    /**
     * One empty {@link ProtoChunk}, built on first use and returned for every position.
     *
     * <h2>It is a sink, not a chunk model</h2>
     * <p>This returned {@code null} until Sep 2026, and the class comment said the piece's own
     * chunk-touch logging tolerated that. It did. What does <strong>not</strong> tolerate it is
     * vanilla's own {@code StructurePiece#placeBlock}, which calls
     * {@code level.getChunk(pos).markPosForPostprocessing(pos)} for every block in
     * {@code SHAPE_CHECK_BLOCKS} &mdash; fences, walls, panes, doors and <em>iron bars</em>. The gap
     * went unnoticed for as long as it did only because no procedural room shipped such a block;
     * the {@code partition} slot (#74) shipped iron bars and four tests went red at once with an
     * NPE inside vanilla.</p>
     *
     * <p>One instance for every {@link ChunkPos}, deliberately. Nothing reads what is marked, so a
     * per-position chunk would buy nothing but the illusion that chunk boundaries are modelled here
     * &mdash; and they are not. What it MUST do is accept the call without throwing, and be sized
     * by this level's own height range so {@code getSectionIndex} lands inside its array.</p>
     */
    private ChunkAccess chunk(Object self) {
        if (chunk == null) {
            chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                    (LevelHeightAccessor) self, BIOMES, null);
        }
        return chunk;
    }

    private ChunkAccess chunk;

    /**
     * A biome registry holding exactly one placeholder, under {@code minecraft:plains}.
     *
     * <p>Built here rather than taken from {@link #registryAccess}, which was the first attempt and
     * was worse: not every test builds this level with a registry set that carries
     * {@code minecraft:worldgen/biome}, so it threw {@code Missing registry} in tests that had
     * nothing to do with chunks.</p>
     *
     * <p>Nor can it be EMPTY, which was the second attempt. {@code ChunkAccess}'s constructor fills
     * its section array eagerly, and {@code LevelChunkSection} asks the registry for
     * {@code Biomes.PLAINS} by name &mdash; so the one key vanilla reaches for has to be there.
     * Nothing ever reads the biome back; a fully default one is enough, and building a real one
     * would mean pulling in a worldgen fixture to satisfy a field that is never inspected.</p>
     */
    private static final Registry<Biome> BIOMES = plainsOnly();

    private static Registry<Biome> plainsOnly() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Biome placeholder = new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        registry.register(Biomes.PLAINS, placeholder, Lifecycle.stable());
        return registry;
    }

    /** Overworld's range, so a piece placed at a plausible Y is inside it. */
    private static final int MIN_BUILD_HEIGHT = -64;
    private static final int HEIGHT = 384;
}

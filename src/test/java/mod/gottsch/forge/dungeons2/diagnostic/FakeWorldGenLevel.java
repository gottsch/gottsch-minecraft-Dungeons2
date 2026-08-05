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
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
 *   <li><strong>No chunks.</strong> {@code getChunk} returns {@code null}, which the piece's own
 *       chunk-touch logging already tolerates. Nothing here models chunk boundaries, so this cannot
 *       reproduce the "piece skipped in an already-generated chunk" class of bug.</li>
 *   <li><strong>No {@code ServerLevel}.</strong> {@code getLevel()} throws, so pieces that spawn
 *       <em>entities</em> (rooms with pots) cannot be driven through this yet &mdash; entity
 *       creation needs a real {@code ServerLevel}. Corridors and doors are pure block placement and
 *       work today.</li>
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
            // The piece's own chunk-touch logging asks for a chunk and already handles null. No
            // chunk model here at all -- see the class comment.
            case "getChunk":
                return null;
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

    /** Overworld's range, so a piece placed at a plausible Y is inside it. */
    private static final int MIN_BUILD_HEIGHT = -64;
    private static final int HEIGHT = 384;
}

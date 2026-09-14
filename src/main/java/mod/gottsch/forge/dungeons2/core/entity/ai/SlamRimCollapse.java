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
package mod.gottsch.forge.dungeons2.core.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a {@code GroundSlamGoal} does to <em>this mod's</em> blocks: it shakes the standing room out
 * from around the mob.
 *
 * <h2>Why the world half lives here</h2>
 * <p>gmm ships the slam and leaves the block effect a null static, exactly as it leaves the Orc's
 * projectile to a consumer. Whether an attack may break a wall is a statement about whose walls
 * these are, and that is Dungeons2's to make -- the same split {@code SmashBlocksGoal} documents.
 *
 * <h2>It breaks LEDGES, not walls, and that distinction is the whole design</h2>
 * <p>The obvious implementation -- every block in the radius between the mob's knees and its head --
 * takes the arena apart in two or three slams: a boss room's walls are inside six blocks of
 * wherever the boss is standing, so every slam would punch holes in them and the room would be a
 * ruin before the fight ended.
 *
 * <p>So the test is <strong>a block with air above it</strong>: somewhere a player could be
 * standing. A wall's blocks have more wall above them and survive; the lip of a gallery, the top of
 * a pillar, the edge of a rim, a shelf someone has climbed onto -- those have sky above them and
 * come down. That is the behaviour the arena wants (Stone Colossus plan, &sect;4: a slam near the
 * rim collapses the gallery and drops the player into the court) and it arrives without needing to
 * know what a "rim" is.
 *
 * <p><strong>Never its own floor.</strong> The band starts two blocks above the mob's feet, so the
 * colossus cannot slam itself through the floor into the level below -- which in a stacked dungeon
 * would not be a surprise, it would be a hole into the next floor's corridor.
 *
 * @author Mark Gottschling on Sep 13, 2026
 */
public class SlamRimCollapse {

    /**
     * Hardness cap, matched to {@code MinotaurGoalsEvent}: dungeon brick and its kin sit at 1.5-2,
     * so this admits the structure while excluding obsidian and anything a player placed to hold.
     */
    private static final float MAX_HARDNESS = 6.0F;
    /** The lowest row the slam reaches, above the mob's feet. Keeps its own floor out of scope. */
    private static final int MIN_HEIGHT = 2;
    /** A bound on the work and on the damage: a slam is a shock, not a demolition. */
    private static final int MAX_BLOCKS = 40;

    private SlamRimCollapse() {}

    /** Wired onto {@code GroundSlamGoal.blockEffect} in {@code CommonSetup}. */
    public static void collapse(Mob mob, double radius) {
        Level level = mob.level();
        if (level.isClientSide() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        int reach = Mth.ceil(radius);
        int feetY = Mth.floor(mob.getY());
        int topY = feetY + Mth.ceil(mob.getBbHeight());
        int broken = 0;

        for (int y = feetY + MIN_HEIGHT; y <= topY && broken < MAX_BLOCKS; y++) {
            for (int x = -reach; x <= reach && broken < MAX_BLOCKS; x++) {
                for (int z = -reach; z <= reach && broken < MAX_BLOCKS; z++) {
                    if (x * x + z * z > reach * reach) {
                        continue;               // the loop is square; the shockwave is not
                    }
                    BlockPos pos = BlockPos.containing(mob.getX() + x, y, mob.getZ() + z);
                    if (isStandingRoom(level, pos)) {
                        level.destroyBlock(pos, true, mob);
                        broken++;
                    }
                }
            }
        }
    }

    /**
     * A block someone could stand on: solid, breakable, and with air directly above it.
     *
     * <p>The air test is what separates a ledge from a wall -- see the class javadoc. Block
     * entities are excluded for the reason {@code SmashBlocksGoal} excludes them: chests, spawners
     * and the authoring markers all carry one, and "it can break blocks" must not come to mean "it
     * can delete content".</p>
     */
    private static boolean isStandingRoom(Level level, BlockPos pos) {
        if (!level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness <= MAX_HARDNESS;
    }
}

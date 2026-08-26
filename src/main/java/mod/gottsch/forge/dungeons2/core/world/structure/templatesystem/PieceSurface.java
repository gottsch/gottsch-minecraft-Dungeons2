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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * <strong>PROTOTYPE (backlog: mud-stratum floors).</strong> Which surface of a piece a block
 * belongs to, decided from its <em>piece-relative</em> Y &mdash; the gate that lets one weathering
 * rule apply to floors and a different one to everything else, without either rule being keyed on
 * block identity.
 *
 * <h2>Why this is not keyed on the block</h2>
 * <p>The obvious way to give the mud stratum a cobblestone floor with its own decay is a rule on
 * {@code minecraft:cobblestone}, which works only while cobblestone happens to be floor-only. An
 * all-cobble fortified room on the same band would then have its <em>walls</em> eaten by the floor
 * rule, silently, long after the rule was written. Surface is the durable key; block identity is
 * a coincidence of what has been authored so far.</p>
 *
 * <h2>The invariant this rests on: the floor is at relative Y 0</h2>
 * <p>Both halves of the pipeline put the floor on the piece's bottom layer, so
 * {@code worldY - piecePos.getY() == 0} identifies it in both:</p>
 * <ul>
 *   <li><strong>Procedural.</strong> {@code DungeonPiece#placeAll} passes
 *       {@code origin = (anchorX, floorY, anchorZ)} to {@code PieceProcessors}, and
 *       {@code BasicFloorGenerator} emits at exactly {@code floorY} while
 *       {@code BasicWallGenerator} spans {@code [floorY+1 .. floorY+height-2]} and
 *       {@code BasicCeilingGenerator} sits at {@code floorY+height-1}. Nothing but floor is
 *       on layer 0.</li>
 *   <li><strong>Prefab.</strong> Vanilla hands {@code processBlock} the template's placement
 *       origin as {@code piecePos}, and every shipped room / hallway template is authored with
 *       a full slab of floor material at template-local Y 0, walls starting at Y 1. Pinned by
 *       {@code PieceSurfaceTest}, which reads the shipped {@code .nbt} files.</li>
 * </ul>
 *
 * <p>Rotation and mirroring are horizontal, so neither disturbs the Y arithmetic &mdash; unlike
 * the horizontal half of a processor's world, which vanilla rotates on the way out.</p>
 *
 * <h2>Layer 0 is the floor BY DECISION, including under a pit</h2>
 * <p>Once #29 (the floor-height raise) and #3 (pits) land, a room's floor <em>plane</em> may sit
 * well above its lowest layer &mdash; a 20-high room with a 5-deep pit has one at relative Y 5 and
 * another at 0. <strong>This class keeps naming layer 0, and that is the intended behaviour, not a
 * limitation</strong> (Mark, 2026-08-26): in a pitted room the pit's own floor is the surface that
 * weathers on the floor's rules, and in a room with no pit it is the ordinary floor. Both are
 * floors somebody walks on.</p>
 *
 * <p><strong>The topological alternative was considered and rejected.</strong> "A full cube with
 * air above it" needs no plane and survives pits, but it finds every upward-facing surface, not
 * every floor: the top course of a dais, an altar, a fountain rim or a sarcophagus all qualify,
 * and in an authored room those are built from the room's own materials, so a block predicate does
 * not separate them either. A dais top and a floor cell are geometrically identical, so no
 * refinement of that test distinguishes them.</p>
 *
 * <h2>One thing pits will still force a decision about</h2>
 * <p>The two halves reach layer 0 differently, and only the prefab half is settled. A template's
 * local Y 0 is its lowest layer, so a pit sinks the template and its pit floor <em>is</em> layer 0
 * &mdash; exactly as described above. The procedural half takes its origin from
 * {@code DungeonPiece#placeAll} as {@code (anchorX, floorY, anchorZ)}, so unless whoever builds
 * procedural pits sinks that origin with the pit, a procedural pit floor lands at a NEGATIVE
 * relative Y and layer 0 stays the room floor &mdash; the opposite of the prefab. That is a
 * parity break of the kind that decays the two sides of a shared wall differently.
 * {@code PieceSurfaceTest.proceduralRoomHasNothingBelowLayerZero} is the guard: it fails the day
 * a procedural placement drops below the origin.</p>
 *
 * <h2>There is deliberately no {@code CEILING}</h2>
 * <p>A ceiling is at {@code height - 1}, and <strong>{@code processBlock} is never told the
 * piece's height</strong>: it sees one block, the piece origin, and a {@code StructurePlaceSettings}
 * whose bounding box is the <em>chunk</em> box, not the piece's. The template's size is not
 * reachable either. So {@link #ABOVE_FLOOR} is as precise as a per-block gate can be, and a real
 * ceiling gate needs a processor that decides in {@code finalizeProcessing} (which does see the
 * whole block list, and so can compute a maximum Y) rather than per block.</p>
 *
 * <h2>THE EXEMPTION PROBLEM IS NOT SOLVED BY GATING THE FLOOR RULE ALONE</h2>
 * <p>A processor list is chained &mdash; each processor sees the previous one's output &mdash; so
 * adding a floor-gated processor does not stop the general {@code dungeons2:aging} entry in the
 * same file from running over layer 0 as well. Gating only the floor rule makes it additive, not
 * exclusive. (Gottsch raised this twice; it survived the move to a D2-local processor unchanged.)
 * </p>
 *
 * <p><strong>It looks solved today only by coincidence of palettes.</strong> The mud band's
 * general chain has exactly one SOURCE block, {@code minecraft:mud_bricks}, and the floor is now
 * cobblestone speckled with {@code packed_mud} &mdash; neither of which that chain matches, so
 * nothing overlaps. That is an authoring coincidence, not an enforced invariant: the accent was
 * {@code mud_bricks} for the few hours between the pattern landing and Gottsch changing it, and
 * during those hours the general chain was decaying the floor's accent cells on the wall's
 * schedule with nothing to stop it. Any future floor palette that reuses a wall block silently
 * reopens it.</p>
 *
 * <p><strong>The fix, when this is built:</strong> the D2-local processor should own the mud
 * band's aging <em>entirely</em> &mdash; both rule groups, one gated {@link #FLOOR} and one gated
 * {@link #ABOVE_FLOOR} &mdash; and {@code classic_mud_weathering.json} should drop its general
 * {@code dungeons2:aging} entry rather than keep it alongside. Two gates that partition the piece
 * cannot both fire on a cell, so exclusivity is structural instead of a palette accident. This
 * stays entirely D2-local and mud-only, because that file already REPLACES the motif's list
 * wholesale rather than extending it.</p>
 *
 * <h2>Scope: one stratum's own processor, NOT {@code dungeons2:aging}</h2>
 * <p>This gate is <strong>not</strong> going into GottschCore's {@code AgingProcessor}. That
 * processor is shared by every motif and every stratum, and adding a surface field to it would put
 * the concept in front of authoring that has no use for it (Mark, 2026-08-26). The mud stratum is
 * the only band that wants floors decaying on their own schedule, so the gate belongs to a
 * D2-local processor named only by {@code classic_mud_weathering.json}. Nothing else loads it, so
 * nothing else can be broken by it.</p>
 *
 * <h2>Scope: rooms and hallways only</h2>
 * <p>Transition and entrance templates do not obey the convention and should not &mdash; a
 * stairwell's layer 0 is a landing that is partly open, an entrance's is partly terrain. Their
 * pool JSONs simply do not need to name a surface-gated rule; {@link #ANY} is the default and
 * leaves them exactly as they are today.</p>
 */
public enum PieceSurface implements StringRepresentable {

    /** Every block, i.e. the ungated behaviour every rule has today. */
    ANY("any") {
        @Override
        public boolean matches(int relativeY) {
            return true;
        }
    },

    /** The piece's bottom layer, and nothing else. */
    FLOOR("floor") {
        @Override
        public boolean matches(int relativeY) {
            return relativeY == FLOOR_RELATIVE_Y;
        }
    },

    /**
     * Everything the floor is not &mdash; walls, ceilings, props, and the interior air between
     * them. The complement of {@link #FLOOR} rather than a {@code WALL} value, because a per-block
     * gate cannot separate a wall from a ceiling (see the class doc).
     */
    ABOVE_FLOOR("above_floor") {
        @Override
        public boolean matches(int relativeY) {
            return relativeY > FLOOR_RELATIVE_Y;
        }
    };

    /** The layer both pipelines put the floor on. */
    public static final int FLOOR_RELATIVE_Y = 0;

    private final String name;

    PieceSurface(String name) {
        this.name = name;
    }

    /** Whether a block at this piece-relative Y belongs to this surface. */
    public abstract boolean matches(int relativeY);

    /**
     * Whether the block {@code current} sits on, as handed to
     * {@code StructureProcessor#processBlock}, belongs to this surface.
     *
     * @param piecePos the processor's first {@code BlockPos} argument &mdash; the piece origin
     *                 vanilla offsets every block by
     * @param current  the block info being processed, whose {@code pos()} is already in world
     *                 space
     */
    public boolean matches(BlockPos piecePos, StructureTemplate.StructureBlockInfo current) {
        return matches(relativeY(piecePos, current.pos()));
    }

    /** A block's Y measured from the piece's bottom layer. */
    public static int relativeY(BlockPos piecePos, BlockPos worldPos) {
        return worldPos.getY() - piecePos.getY();
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * Failing rather than lenient, for the reason {@code WallPatternEntry.CourseAnchor.CODEC}
     * gives: the set is closed and tiny, so a value outside it is a typo, and reading
     * {@code "floors"} as {@link #ANY} would apply a floor's decay to every wall on the band with
     * nothing logged anywhere.
     */
    public static final Codec<PieceSurface> CODEC = StringRepresentable.fromEnum(PieceSurface::values);
}

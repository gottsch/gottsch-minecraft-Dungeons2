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
package mod.gottsch.forge.dungeons2.core.data;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;

/**
 * Where a secret room's hidden door and its lever go, in floor-local grid coordinates.
 *
 * <p>Worked out once at generation by {@code SecretRoomPlanner}, which is the only place that can
 * see the corridors, and carried on BOTH pieces that need it: the room (to know it may roll a secret
 * scheme, and which doorway the wall patterns must not dress) and the door (to hang the door and the
 * lever). Neither can recompute it on load &mdash; a loaded piece has no layout.</p>
 *
 * <p>The frame: {@code inward} points from the corridor into the room, which is also the hidden
 * door's {@code facing}. The lever hangs on the corridor face of the room's wall cell
 * {@code leverSide} steps along the wall from the door (+1 or -1 along X for a door facing
 * north/south, along Z for one facing east/west). The decoy, when there is room for one, is the
 * mirror image on the other side.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public record SecretDoorway(int x, int z, Direction2D inward, int leverSide, boolean decoy) {

    public SecretDoorway {
        if (inward != Direction2D.NORTH && inward != Direction2D.SOUTH
                && inward != Direction2D.EAST && inward != Direction2D.WEST) {
            throw new IllegalArgumentException("inward must be cardinal: " + inward);
        }
        if (leverSide != 1 && leverSide != -1) {
            throw new IllegalArgumentException("leverSide must be +1 or -1: " + leverSide);
        }
    }

    /** The door cell. */
    public Coords2D door() {
        return new Coords2D(x, z);
    }

    /** The direction from the room out into the corridor: where a sconce on this wall faces. */
    public Direction2D outward() {
        return opposite(inward);
    }

    /** The room wall cell the lever hangs on; the block it powers, which touches the door. */
    public Coords2D leverWall() {
        return beside(leverSide, 0);
    }

    /** The corridor cell the lever itself stands in. */
    public Coords2D lever() {
        return beside(leverSide, 1);
    }

    /** The room wall cell on the other side of the door, where the decoy hangs. */
    public Coords2D decoyWall() {
        return beside(-leverSide, 0);
    }

    /** The corridor cell the decoy torch sconce stands in. */
    public Coords2D decoyCell() {
        return beside(-leverSide, 1);
    }

    /** The corridor cell directly outside the door. */
    public Coords2D outside() {
        return beside(0, 1);
    }

    /** {@code along} steps along the wall and {@code out} steps into the corridor, from the door. */
    private Coords2D beside(int along, int out) {
        int[] o = step(outward());
        // Along the wall is perpendicular to the door's axis: X for a north/south door, Z otherwise.
        boolean alongX = inward == Direction2D.NORTH || inward == Direction2D.SOUTH;
        return new Coords2D(x + o[0] * out + (alongX ? along : 0),
                z + o[1] * out + (alongX ? 0 : along));
    }

    /** Grid step for a cardinal direction; north is -Z, as in Minecraft. */
    public static int[] step(Direction2D direction) {
        return switch (direction) {
            case NORTH -> new int[]{0, -1};
            case SOUTH -> new int[]{0, 1};
            case EAST -> new int[]{1, 0};
            case WEST -> new int[]{-1, 0};
            default -> throw new IllegalArgumentException("not cardinal: " + direction);
        };
    }

    public static Direction2D opposite(Direction2D direction) {
        return switch (direction) {
            case NORTH -> Direction2D.SOUTH;
            case SOUTH -> Direction2D.NORTH;
            case EAST -> Direction2D.WEST;
            case WEST -> Direction2D.EAST;
            default -> throw new IllegalArgumentException("not cardinal: " + direction);
        };
    }
}

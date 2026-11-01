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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

/**
 * How a barrel vault's cross-width is shared out between its barrels: one, or two side by side
 * with a {@code spine} between them, decided the same way for every step of the vault.
 *
 * <h2>The geometry</h2>
 * <p>A double barrel is two single barrels that share a lip. Across the barrel axis, a surface of
 * {@code cross} cells is split into two bays, and each bay is drawn exactly as a single barrel of
 * {@link #bayWidth} cells would be: step {@code k} insets {@code k * step_inset} from both of the
 * bay's edges and runs end wall to end wall. The bay's outer edge is the room's wall, whose plane
 * cell is the lip the springing ring already draws; its inner edge is the spine, which is the lip of
 * BOTH bays &mdash; the shared springing line &mdash; and therefore stays on the ceiling plane.</p>
 *
 * <ul>
 *   <li><strong>Odd</strong> {@code cross}: the bays overlap by one cell, and that cell is the spine.
 *       11 cells is {@code lip, 4 raised, spine, 4 raised, lip}.</li>
 *   <li><strong>Even</strong> {@code cross}: the bays abut, and their two inner lips side by side
 *       are a 2-cell spine. 12 cells is {@code lip, 4 raised, spine, spine, 4 raised, lip}.</li>
 * </ul>
 *
 * <p>The even case widens the spine rather than one bay because both halves then stay mirror
 * images of each other about the room's centre line, and the spine stays centred on it. Making one
 * bay a cell wider would put the spine off-centre and give the two crowns different widths &mdash;
 * the first thing anyone standing at the end wall would see. A doubled spine reads as a deliberate
 * rib; an off-centre one reads as a mistake.</p>
 *
 * <h2>When it falls back to one barrel</h2>
 * <p>Only when each bay can raise at least its first step: a field inset {@code i} from both edges
 * of {@code w} cells is empty unless {@code w > 2i}, so two barrels need
 * {@code bayWidth >= 2 * step_inset + 1}, i.e. {@code cross >= 4 * step_inset + 1} (5 cells at the
 * default inset of 1, 9 at an inset of 2). Below that the vault is drawn as the single barrel it
 * would have been without {@code barrels}, rather than as two flat slivers either side of a rib.
 * The threshold is measured from the FIRST step's inset and decided once per surface, so every step
 * and the spine agree on it; a deeper step that runs out of bay simply stops, as it does in a single
 * barrel.</p>
 *
 * <p>Holds no random and no state: the split is a pure function of the cross-width, which is what
 * lets the step providers and the spine each work it out on their own and still agree.</p>
 *
 * @author Mark Gottschling on 10/29/2026
 */
public final class BarrelBays {

    /** The most barrels one vault may be split into; what the schema's {@code barrels} accepts. */
    public static final int MAX_BARRELS = 2;

    private final int barrels;
    private final int stepInset;

    /**
     * @param barrels   1 or 2
     * @param stepInset the vault's {@code step_inset}: the first step's inset, which decides whether
     *                  a bay is wide enough to raise anything at all
     */
    public BarrelBays(int barrels, int stepInset) {
        if (barrels < 1 || barrels > MAX_BARRELS) {
            throw new IllegalArgumentException("barrels must be 1 to " + MAX_BARRELS + ": " + barrels);
        }
        this.barrels = barrels;
        this.stepInset = Math.max(1, stepInset);
    }

    /** Whether a surface {@code cross} cells across the barrel axis is drawn as two barrels. */
    public boolean doubled(int cross) {
        return barrels == 2 && bayWidth(cross) >= 2 * stepInset + 1;
    }

    /** The width of each bay including both of its lips (so the spine is counted in both). */
    public static int bayWidth(int cross) {
        return (cross + 1) / 2;
    }

    /** The first cross cell of the spine; only meaningful when {@link #doubled}. */
    public static int spineStart(int cross) {
        return bayWidth(cross) - 1;
    }

    /** One past the last cross cell of the spine: one cell for an odd cross-width, two for even. */
    public static int spineEnd(int cross) {
        return cross / 2 + 1;
    }

    /**
     * Calls {@code span} with the {@code [from, to)} cross range a field inset {@code inset} covers:
     * once for a single barrel (from the walls), twice for a doubled one (each bay from its wall and
     * the spine). An empty range is still passed; the caller's loop simply draws nothing.
     */
    public void forEachSpan(int cross, int inset, Span span) {
        if (!doubled(cross)) {
            span.accept(inset, cross - inset);
            return;
        }
        int bay = bayWidth(cross);
        span.accept(inset, bay - inset);
        span.accept(cross - bay + inset, cross - inset);
    }

    /** A {@code [from, to)} range of cross cells. */
    @FunctionalInterface
    public interface Span {
        void accept(int from, int to);
    }
}

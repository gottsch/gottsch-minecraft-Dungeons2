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

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * <strong>How often a band-level ceiling pattern would actually draw.</strong>
 *
 * <p>A measurement, not an assertion &mdash; it prints and passes. Written to answer one question
 * about the mud stratum's authored {@code joists}: a band pattern is tier 2, so it draws in every
 * room whose rolled scheme names no {@code ceiling} slot of its own. Seven of classic's ten
 * rollable schemes name none, which on paper weight is 70% &mdash; and paper weight is exactly the
 * number this codebase has learned not to trust (see {@code SchemeIncidenceTest}: size gates decide
 * which schemes are even eligible before weights are totalled).
 */
class BandCeilingIncidenceProbe {

    private static final ICoords ANCHOR = new Coords(128, 0, 256);
    private static final int SURFACE_Y = 72;
    private static final int DUNGEONS = 60;
    private static final int ENTRANCE_FLOOR = 0;

    /** The mud band's authored gate. A joist costs two rows of headroom. */
    private static final int JOIST_MIN_HEIGHT = 7;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howOftenWouldTheBandsCeilingDraw() {
        MotifConfig config = MotifConfigs.load("classic");

        int rooms = 0;
        int schemeCeiling = 0;
        int bandEligible = 0;
        int tooShort = 0;

        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM).plan();
            if (planned.isEmpty()) {
                continue;
            }
            RandomSource random = RandomSource.create(seed);
            for (FloorLayout floor : planned.get().getFloors()) {
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) {
                        continue;
                    }
                    rooms++;
                    int w = room.getWidth();
                    int d = room.getDepth();
                    int h = room.getHeight();
                    RoomScheme scheme = RoomSchemeSelector.select(
                            config.schemes(), w, d, h, ENTRANCE_FLOOR, random);

                    if (scheme.ceilingFor(w, d, h).isPresent()) {
                        schemeCeiling++;          // tier 1 -- the scheme wins, band never consulted
                    } else if (h >= JOIST_MIN_HEIGHT) {
                        bandEligible++;           // tier 2 -- the band's joists draw
                    } else {
                        tooShort++;               // tier 3 -- gated out, plain mud-brick ceiling
                    }
                }
            }
        }

        System.out.printf("%n=== band ceiling incidence, %d MEDIUM dungeons, %d NORMAL rooms ===%n",
                DUNGEONS, rooms);
        System.out.printf("  scheme's own ceiling wins   %5.1f%% (%d)%n", pct(schemeCeiling, rooms), schemeCeiling);
        System.out.printf("  BAND joists draw            %5.1f%% (%d)%n", pct(bandEligible, rooms), bandEligible);
        System.out.printf("  too short, plain ceiling    %5.1f%% (%d)%n", pct(tooShort, rooms), tooShort);
        System.out.printf("  => joists visible in        %5.1f%% of rooms on this band%n",
                pct(schemeCeiling + bandEligible, rooms));
        System.out.println("     (the first line is joisted_hall/joisted_hall_stone/vaulted_hall,");
        System.out.println("      two of which are ALSO joists -- so the real 'sees a joist' number");
        System.out.println("      is higher than the band's own row alone.)");
    }

    private static double pct(int n, int of) {
        return of == 0 ? 0 : 100.0 * n / of;
    }
}

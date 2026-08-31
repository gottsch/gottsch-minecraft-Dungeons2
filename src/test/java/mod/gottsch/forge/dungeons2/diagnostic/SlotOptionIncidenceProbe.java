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
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
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
 * <strong>How often each slot OPTION actually draws, once a converted scheme is rolled.</strong>
 *
 * <p>A measurement, not an assertion &mdash; it prints and passes. This is the tool #65 asks for:
 * once a slot holds alternatives, "scheme X appears in 3.7% of rooms" stops being a complete
 * sentence, because the scheme is now a distribution rather than a room. The number that matters is
 * per option, and the failure mode it exists to catch is the one the backlog names: a list with no
 * {@code none} entry puts its treatment in <strong>100%</strong> of that scheme's rooms, which is
 * invisible until someone walks the dungeon. Band-level joists reached 55.9% that way and became
 * the mud band's look rather than a room type.</p>
 *
 * <p>Measured on <strong>floor 0</strong>, through {@code forFloor(0)}, because the converted
 * scheme lives on the mud stratum and the projection is what puts it in the list at all.</p>
 */
class SlotOptionIncidenceProbe {

    private static final ICoords ANCHOR = new Coords(128, 0, 256);
    private static final int SURFACE_Y = 72;
    private static final int DUNGEONS = 60;
    private static final int MUD_FLOOR = 0;

    /** The scheme converted on 2026-08-31 -- the two pilastered siblings folded into one. */
    private static final String CONVERTED = "mud_timber_pilastered";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howOftenDoesEachOptionDraw() {
        MotifConfig mud = MotifConfigs.load("classic").forFloor(MUD_FLOOR);

        int rooms = 0;
        int converted = 0;
        int endPilasters = 0;
        int borderFloor = 0;
        int pots = 0;

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
                    // select() resolves the scheme it returns, so what comes back is one room's
                    // worth of choices rather than the authored distribution -- which is exactly
                    // what has to be counted here.
                    RoomScheme scheme = RoomSchemeSelector.select(
                            mud.schemes(), w, d, h, MUD_FLOOR, random);
                    if (!CONVERTED.equals(scheme.name())) {
                        continue;
                    }
                    converted++;
                    if (scheme.wallFor(w, d, h)
                            .map(entry -> entry.patterns().stream().anyMatch(
                                    mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.PatternEntry::isEndPilasters))
                            .orElse(false)) {
                        endPilasters++;
                    }
                    if (scheme.floorFor(w, d, h).isPresent()) {
                        borderFloor++;
                    }
                    if (scheme.potsFor(w, d, h).isPresent()) {
                        pots++;
                    }
                }
            }
        }

        System.out.printf("%n=== slot-option incidence, %d MEDIUM dungeons, %d NORMAL rooms on floor %d ===%n",
                DUNGEONS, rooms, MUD_FLOOR);
        System.out.printf("  '%s' rolled        %5.1f%% of rooms (%d)%n",
                CONVERTED, pct(converted, rooms), converted);
        System.out.printf("    ... of those, end pilasters   %5.1f%% (%d)%n", pct(endPilasters, converted), endPilasters);
        System.out.printf("    ... of those, border footing  %5.1f%% (%d)%n", pct(borderFloor, converted), borderFloor);
        System.out.printf("    ... of those, pots            %5.1f%% (%d)%n", pct(pots, converted), pots);
        System.out.println("  (all three were authored 1:1 against a `none`, so ~50% each is the");
        System.out.println("   expected reading; 100% on any line means its `none` entry is missing.)");
    }

    private static double pct(int n, int of) {
        return of == 0 ? 0 : 100.0 * n / of;
    }
}

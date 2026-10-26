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
package mod.gottsch.forge.dungeons2.core.world.structure;

import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prefabs ignore the water already in the ground: the dry element places with {@code keepLiquids}
 * off, and every shipped pool uses it.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class DrySinglePoolElementTest {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String POOL_ROOT = "/data/dungeons2/worldgen/template_pool";

    @Test
    void placesWithoutKeepingTheWorldsLiquids() {
        DrySinglePoolElement element = new DrySinglePoolElement(
                Either.left(new ResourceLocation("dungeons2:rooms/classic/7x13_sewer_1")),
                Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID);
        StructurePlaceSettings settings = element.getSettings(Rotation.NONE,
                new BoundingBox(0, 0, 0, 15, 15, 15), false);
        assertFalse(settings.shouldKeepLiquids(),
                "keepLiquids on would waterlog any stair or slab placed where the ground had water");
        // still everything vanilla's single element installs
        assertFalse(settings.getProcessors().isEmpty());
    }

    /** A vanilla single element left in a pool would waterlog its prefab again. */
    @Test
    void everyShippedPoolUsesTheDryElement() {
        URL url = DrySinglePoolElementTest.class.getResource(POOL_ROOT);
        if (url == null) {
            fail("no pools at " + POOL_ROOT);
        }
        int dry = 0;
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                String json = Files.readString(file);
                assertFalse(json.contains("\"minecraft:single_pool_element\""),
                        file.getFileName() + " still uses minecraft:single_pool_element");
                if (json.contains("\"dungeons2:dry_single_pool_element\"")) {
                    dry++;
                }
            }
        } catch (IOException | URISyntaxException unreadable) {
            throw new UncheckedIOException(new IOException(unreadable));
        }
        assertTrue(dry > 10, "expected the shipped pools to use the dry element, found " + dry + " files");
    }
}

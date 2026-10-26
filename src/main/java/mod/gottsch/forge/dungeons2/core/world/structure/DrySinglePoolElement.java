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
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * {@code dungeons2:dry_single_pool_element}: a vanilla single pool element that ignores the water
 * already in the ground it is placed into.
 *
 * <p>Vanilla's {@link SinglePoolElement} places with {@code keepLiquids} on, so
 * {@code StructureTemplate.placeInWorld} reads the world's fluid at every cell and waterlogs any
 * stair, slab, trapdoor, wall or bar that lands where an aquifer or cave had water &mdash; and a second
 * pass waterlogs those beside a water source. A prefab dug through wet rock came out with water in
 * half its trim. No processor can stop it: it happens after processing. With {@code keepLiquids}
 * off, a block is waterlogged only if the template saved it so, and authored water is placed as
 * any other block. (1.20.5 added {@code liquid_settings} for this; 1.20.1 has nothing.)
 *
 * <p>The JSON is vanilla's single element exactly ({@code location}, {@code processors},
 * {@code projection}), so {@code PoolElementIds}, {@code template_limits} and
 * {@code template_biomes} read it unchanged. Only newly generated chunks are affected.
 *
 * @author Mark Gottschling on 10/26/2026
 */
public class DrySinglePoolElement extends SinglePoolElement {

    public static final Codec<DrySinglePoolElement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            templateCodec(), processorsCodec(), projectionCodec()
    ).apply(instance, DrySinglePoolElement::new));

    protected DrySinglePoolElement(Either<ResourceLocation, StructureTemplate> template,
                                   Holder<StructureProcessorList> processors,
                                   StructureTemplatePool.Projection projection) {
        super(template, processors, projection);
    }

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox box, boolean keepJigsaws) {
        return super.getSettings(rotation, box, keepJigsaws).setKeepLiquids(false);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return Registration.DRY_SINGLE_POOL_ELEMENT.get();
    }

    @Override
    public String toString() {
        return "Dry" + super.toString();
    }
}

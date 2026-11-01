/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Metadata for one template prefab in the {@link TemplateCatalog}.
 *
 * <p>Carries everything the planner needs to <em>choose</em> a template and
 * <em>reserve</em> its footprint without touching the actual {@code .nbt} file
 * &mdash; the file is loaded later in the Forge shell at render time.</p>
 *
 * <p>{@code id} is a namespaced resource location string. {@code width} /
 * {@code depth} / {@code height} are the template's bounding-box dimensions
 * in blocks. {@code motifTags} restrict which {@code DungeonMotif} values the
 * template fits (empty = any motif). {@code sizeTags} restrict which
 * {@link DungeonSize} tiers can pick this template (empty = any).</p>
 *
 * <p>{@code biomeFitnessTags} are free-form hints (e.g. {@code "flat-only"},
 * {@code "underwater-ok"}) for future placement logic; unused in v1.
 * {@code connectorTags} reserves space for Phase 8 jigsaw-connector metadata
 * used when stitching template rooms/corridors into the maze.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class TemplateEntry {
    private String id;
    private int width;
    private int depth;
    private int height;
    private EnumSet<DungeonSize> sizeTags = EnumSet.noneOf(DungeonSize.class);
    private List<String> motifTags = new ArrayList<>();
    private List<String> biomeFitnessTags = new ArrayList<>();
    private List<String> connectorTags = new ArrayList<>();

    public TemplateEntry() {}

    public TemplateEntry(String id, int width, int depth, int height) {
        this.id = id;
        this.width = width;
        this.depth = depth;
        this.height = height;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public EnumSet<DungeonSize> getSizeTags() {
        if (sizeTags == null) sizeTags = EnumSet.noneOf(DungeonSize.class);
        return sizeTags;
    }
    public void setSizeTags(EnumSet<DungeonSize> sizeTags) { this.sizeTags = sizeTags; }

    public List<String> getMotifTags() {
        if (motifTags == null) motifTags = new ArrayList<>();
        return motifTags;
    }
    public void setMotifTags(List<String> motifTags) { this.motifTags = motifTags; }

    public List<String> getBiomeFitnessTags() {
        if (biomeFitnessTags == null) biomeFitnessTags = new ArrayList<>();
        return biomeFitnessTags;
    }
    public void setBiomeFitnessTags(List<String> biomeFitnessTags) { this.biomeFitnessTags = biomeFitnessTags; }

    public List<String> getConnectorTags() {
        if (connectorTags == null) connectorTags = new ArrayList<>();
        return connectorTags;
    }
    public void setConnectorTags(List<String> connectorTags) { this.connectorTags = connectorTags; }

    /** True if this entry is compatible with the given motif/size, ignoring null/empty filters. */
    public boolean matches(String motifValue, DungeonSize size) {
        if (!getMotifTags().isEmpty() && motifValue != null && !getMotifTags().contains(motifValue)) {
            return false;
        }
        if (!getSizeTags().isEmpty() && size != null && !getSizeTags().contains(size)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "TemplateEntry{id=" + id +
                ", size=" + width + "x" + depth + "x" + height +
                ", motifs=" + motifTags +
                ", sizes=" + sizeTags +
                '}';
    }
}

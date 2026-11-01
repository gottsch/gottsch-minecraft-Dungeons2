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
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Registry of template metadata, organized by {@link Category}.
 *
 * <p>All four categories are defined up front (entrances, transitions, rooms,
 * corridors) so Phase 8 only adds <em>data</em> (template files + entries)
 * and <em>callers</em> (planner integration), never structural refactors.</p>
 *
 * <p>In v1, only {@link Category#ENTRANCE} and {@link Category#TRANSITION} are
 * populated. {@link Category#ROOM} and {@link Category#CORRIDOR} stay empty
 * until Phase 8 unlocks mixed-mode templates within floors.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports. Loaded from a config file at
 * startup in the Forge shell; the catalog itself only stores metadata, never
 * actual template content (that lives in {@code .nbt} files).</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class TemplateCatalog {

    /** The four template categories. Rooms and corridors are reserved for Phase 8. */
    public enum Category {
        ENTRANCE,
        TRANSITION,
        ROOM,
        CORRIDOR
    }

    private final Map<Category, List<TemplateEntry>> byCategory = new EnumMap<>(Category.class);

    public TemplateCatalog() {
        for (Category c : Category.values()) {
            byCategory.put(c, new ArrayList<>());
        }
    }

    /** Adds an entry to a category. */
    public TemplateCatalog add(Category category, TemplateEntry entry) {
        byCategory.get(category).add(entry);
        return this;
    }

    /** All entries in a category, in registration order. Immutable view. */
    public List<TemplateEntry> getAll(Category category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    /** Entries in a category that match the given motif and size. */
    public List<TemplateEntry> getMatching(Category category, String motifValue, DungeonSize size) {
        List<TemplateEntry> out = new ArrayList<>();
        for (TemplateEntry entry : byCategory.get(category)) {
            if (entry.matches(motifValue, size)) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Deterministic pick from {@link #getMatching}. Returns null if no entry matches.
     * Callers must pass a seeded {@link Random} so the choice is reproducible.
     */
    public TemplateEntry pick(Category category, String motifValue, DungeonSize size, Random random) {
        List<TemplateEntry> matching = getMatching(category, motifValue, size);
        if (matching.isEmpty()) {
            return null;
        }
        return matching.get(random.nextInt(matching.size()));
    }

    /** True if a category has at least one entry. */
    public boolean isPopulated(Category category) {
        return !byCategory.get(category).isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TemplateCatalog{");
        boolean first = true;
        for (Category c : Category.values()) {
            if (!first) sb.append(", ");
            sb.append(c).append("=").append(byCategory.get(c).size());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }
}

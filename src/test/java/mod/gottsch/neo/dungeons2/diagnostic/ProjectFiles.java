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
package mod.gottsch.neo.dungeons2.diagnostic;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves a path in the project for the tests that read source files, {@code build.gradle} or
 * other files that are not on the classpath.
 *
 * <p>On Forge the tests ran with the project root as the working directory, so a bare
 * {@code Path.of("src/...")} worked. ModDevGradle's unit-test runner starts the game with
 * {@code --gameDir .} in {@code build/minecraft-junit}, so the working directory is that folder
 * instead. This walks up from the working directory to the folder holding
 * {@code settings.gradle}, which works under Gradle and from an IDE alike.</p>
 *
 * @author Mark Gottschling on 11/02/2026
 */
public final class ProjectFiles {

    private static final Path ROOT = findRoot();

    private ProjectFiles() {}

    /** {@code first} and {@code more} joined, resolved against the project root. */
    public static Path path(String first, String... more) {
        return ROOT.resolve(Path.of(first, more));
    }

    private static Path findRoot() {
        Path start = Path.of("").toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("settings.gradle"))) {
                return dir;
            }
        }
        return start;
    }
}

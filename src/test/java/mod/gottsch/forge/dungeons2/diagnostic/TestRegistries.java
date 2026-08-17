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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigRegistries;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.SpawnerMarkerProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.DecorationProcessor;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A {@link RegistryAccess} carrying the datapack registries {@code postProcess} actually reads,
 * decoded from the shipped resources on the classpath.
 *
 * <p>Vanilla's {@code Bootstrap.bootStrap()} populates {@link BuiltInRegistries} &mdash; blocks,
 * block entity types, processor <em>types</em> &mdash; but nothing datapack-driven, because that is
 * loaded from a {@code ResourceManager} by a running server. So a piece driven headlessly through
 * {@code postProcess} finds no {@code dungeons2:motif_config} and no
 * {@code minecraft:worldgen/processor_list}, and every lookup degrades to a default: the piece
 * renders as bare stone brick and skips weathering entirely, which is a green test asserting almost
 * nothing.</p>
 *
 * <p>This fills exactly those two gaps, off the same files a player gets, so a test sees the real
 * shipped content and picks up an authoring change with no code change &mdash; the same reasoning
 * (and the same classpath-walking) as {@link MotifConfigs}, one layer lower.</p>
 *
 * <h2>Scope</h2>
 * <p>Deliberately not a general datapack loader. It adds two registries; everything else is
 * whatever {@code Bootstrap} put in {@link BuiltInRegistries}. Adding a third is a few lines, and
 * the failure mode if you forget is loud &mdash; the helper that wanted it returns its default.</p>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
public final class TestRegistries {

    private static final String MOTIF_ROOT = "/data/dungeons2/dungeons2/motif_config";
    private static final String PROCESSOR_ROOT = "/data/dungeons2/worldgen/processor_list";

    private static RegistryAccess cached;

    private TestRegistries() {}

    /**
     * Built once and reused: decoding every motif fragment and processor list is not free, and the
     * result is immutable. Callers must have run {@code Bootstrap.bootStrap()} first.
     */
    public static synchronized RegistryAccess get() {
        if (cached == null) {
            cached = build();
        }
        return cached;
    }

    private static RegistryAccess build() {
        registerProcessorTypes();
        // The built-in half first: the datapack codecs below resolve block ids through it, so it has
        // to exist before they are decoded.
        RegistryAccess.Frozen builtin = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        RegistryOps<JsonElement> ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, builtin);

        Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries = new LinkedHashMap<>();
        builtin.registries().forEach(entry -> registries.put(entry.key(), entry.value()));
        registries.put(MotifConfigRegistries.MOTIF_CONFIG, motifConfigRegistry(ops));
        registries.put(Registries.PROCESSOR_LIST, processorListRegistry(ops));
        return new RegistryAccess.ImmutableRegistryAccess(registries);
    }

    /**
     * Puts {@code dungeons2:aging} and {@code dungeons2:decoration} into the built-in
     * {@code STRUCTURE_PROCESSOR} registry, which is what makes the shipped weathering list
     * decodable at all.
     *
     * <h2>Why this cannot go through the RegistryAccess above</h2>
     * <p>{@code StructureProcessorType.SINGLE_CODEC} dispatches on
     * <strong>{@code BuiltInRegistries.STRUCTURE_PROCESSOR}</strong> &mdash; the static field, read
     * directly, not the registry the decoding {@code RegistryOps} was handed. So an overlay in a
     * {@code RegistryAccess} is never consulted and the type stays unknown; the failure is a
     * thoroughly unhelpful "Not a json array", because the {@code Codec.either} in
     * {@code DIRECT_CODEC} reports its <em>second</em> branch's error and swallows the real one.</p>
     *
     * <p>In production these are registered by Forge's {@code DeferredRegister} during mod loading
     * (see {@code Registration.STRUCTURE_PROCESSORS}), which unfreezes the vanilla registry, adds to
     * it and refreezes. No mod loading happens in a plain unit test, so this does the same thing
     * directly. {@code MappedRegistry.unfreeze()} is Forge's own hook for exactly this.</p>
     *
     * <p>The names come from {@code Registration}'s constants rather than string literals, so a
     * rename cannot leave the test registering something the datapack no longer names.</p>
     */
    private static void registerProcessorTypes() {
        ResourceLocation aging = new ResourceLocation(Dungeons.MOD_ID, Registration.AGING_PROCESSOR_NAME);
        if (BuiltInRegistries.STRUCTURE_PROCESSOR.containsKey(aging)) {
            return;
        }
        MappedRegistry<StructureProcessorType<?>> registry =
                (MappedRegistry<StructureProcessorType<?>>) BuiltInRegistries.STRUCTURE_PROCESSOR;
        registry.unfreeze();

        // Each codec needs a supplier of the very type it is being registered as, so the type is
        // held in a one-slot array and filled in once it exists -- the same knot Registration ties
        // with a RegistryObject. Written out twice rather than factored into a generic helper:
        // the codec factories take Supplier<StructureProcessorType<?>>, and threading a wildcard
        // through a type parameter costs more unchecked casts than the duplication saves.
        StructureProcessorType<?>[] agingSelf = new StructureProcessorType<?>[1];
        Codec<AgingProcessor> agingCodec = AgingProcessor.codec(() -> agingSelf[0]);
        StructureProcessorType<AgingProcessor> agingType = () -> agingCodec;
        agingSelf[0] = agingType;
        Registry.register(registry, aging, agingType);

        StructureProcessorType<?>[] decorationSelf = new StructureProcessorType<?>[1];
        Codec<DecorationProcessor> decorationCodec = DecorationProcessor.codec(() -> decorationSelf[0]);
        StructureProcessorType<DecorationProcessor> decorationType = () -> decorationCodec;
        decorationSelf[0] = decorationType;
        Registry.register(registry,
                new ResourceLocation(Dungeons.MOD_ID, Registration.DECORATION_PROCESSOR_NAME), decorationType);

        // #10's spawner marker. Registered here for the same reason as the two above -- without it
        // the shipped list stops decoding entirely and every test that touches weathering fails at
        // once, which is exactly what happened when this processor was first added to the JSON.
        StructureProcessorType<?>[] spawnerSelf = new StructureProcessorType<?>[1];
        Codec<SpawnerMarkerProcessor> spawnerCodec = SpawnerMarkerProcessor.codec(() -> spawnerSelf[0]);
        StructureProcessorType<SpawnerMarkerProcessor> spawnerType = () -> spawnerCodec;
        spawnerSelf[0] = spawnerType;
        Registry.register(registry,
                new ResourceLocation(Dungeons.MOD_ID, Registration.SPAWNER_PROCESSOR_NAME), spawnerType);

        registry.freeze();
    }

    /**
     * Every {@code motif_config} file, registered under the id the datapack would give it, so
     * {@code MotifConfigHelper}'s path matching and id-order fold run exactly as they do in game.
     * Both authoring layouts are handled: a flat {@code <motif>.json} and a {@code <motif>/} folder.
     */
    private static Registry<MotifConfigFragment> motifConfigRegistry(RegistryOps<JsonElement> ops) {
        MappedRegistry<MotifConfigFragment> registry =
                new MappedRegistry<>(MotifConfigRegistries.MOTIF_CONFIG, Lifecycle.stable());
        for (Path file : jsonFilesUnder(MOTIF_ROOT)) {
            String id = relativeId(MOTIF_ROOT, file);
            MotifConfigFragment fragment = decode(file, json ->
                    MotifConfigFragment.CODEC.parse(ops, json));
            registry.register(
                    ResourceKey.create(MotifConfigRegistries.MOTIF_CONFIG,
                            new ResourceLocation(Dungeons.MOD_ID, id)),
                    fragment, Lifecycle.stable());
        }
        return registry.freeze();
    }

    private static Registry<StructureProcessorList> processorListRegistry(RegistryOps<JsonElement> ops) {
        MappedRegistry<StructureProcessorList> registry =
                new MappedRegistry<>(Registries.PROCESSOR_LIST, Lifecycle.stable());
        for (Path file : jsonFilesUnder(PROCESSOR_ROOT)) {
            String id = relativeId(PROCESSOR_ROOT, file);
            StructureProcessorList list = decode(file, json ->
                    StructureProcessorType.DIRECT_CODEC.parse(ops, json));
            registry.register(
                    ResourceKey.create(Registries.PROCESSOR_LIST, new ResourceLocation(Dungeons.MOD_ID, id)),
                    list, Lifecycle.stable());
        }
        return registry.freeze();
    }

    /**
     * The datapack id a file would be registered under: its path below {@code root}, minus
     * {@code .json}, with {@code /} separators whatever the filesystem uses.
     */
    private static String relativeId(String root, Path file) {
        Path base = resource(root);
        String relative = base.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".json".length());
    }

    /** Every {@code .json} under {@code root}, recursively, in a stable order. */
    private static List<Path> jsonFilesUnder(String root) {
        Path dir = resource(root);
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IllegalStateException("no shipped resources under " + root
                    + " -- has the resource folder moved?");
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(f -> f.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("could not walk " + root, e);
        }
    }

    private static <T> T decode(Path file, java.util.function.Function<JsonElement,
            com.mojang.serialization.DataResult<T>> decoder) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = parseLenient(reader);
            // getOrThrow, not .result(): a shipped file that no longer decodes is a real failure and
            // the message is the only thing that says which file and why.
            return decoder.apply(json).getOrThrow(false, error -> {
                throw new IllegalStateException("could not decode " + file + ": " + error);
            });
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    /**
     * Reads JSON the way the game's datapack loader does, which is not the way strict
     * {@code Gson.fromJson} does.
     *
     * <p>{@code classic_weathering.json} is heavily commented ({@code //}) and carries a UTF-8 BOM,
     * and both are fine in game because Minecraft parses datapack JSON through a <em>lenient</em>
     * {@link JsonReader}. Reading it strictly here fails on the first comment line — and that would
     * read as "the weathering list is broken" rather than "this test reads JSON differently from the
     * game", which is a bad hour to spend.</p>
     */
    private static JsonElement parseLenient(Reader reader) throws Exception {
        try (JsonReader json = new JsonReader(reader)) {
            json.setLenient(true);
            return JsonParser.parseReader(json);
        }
    }

    private static Path resource(String path) {
        try {
            URL url = TestRegistries.class.getResource(path);
            return url == null ? null : Paths.get(url.toURI());
        } catch (Exception e) {
            throw new IllegalStateException("could not resolve resource " + path, e);
        }
    }
}

package mod.gottsch.forge.dungeons2.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.Locale;

/**
 * Debug command: generate a dungeon at a position, through the <strong>real</strong> worldgen path.
 *
 * <p>Usage: {@code /d2-generate <pos> [size] [floors] [motif]}. Only the position is required;
 * anything omitted is rolled from the seed exactly as natural generation would roll it.</p>
 *
 * <h2>What this runs, and why that matters</h2>
 * <p>This is {@code /place structure dungeons2:dungeon} with arguments &mdash; it looks up the
 * structure and hands it to vanilla's own {@link PlaceCommand#placeStructure}, so what gets built is
 * the {@code DungeonStructure} pipeline in full: the jigsaw-assembled entrance, real
 * {@code StructurePiece}s, and {@code postProcess} (which is where {@code settleJoinShapes} and the
 * decoration/weathering pass live).</p>
 *
 * <p>It did not always. Until Aug 04 2026 this command was a Phase 2/3 relic that drove
 * {@code DungeonLayoutRenderer} directly and wrote blocks straight to the world, with a hand-carved
 * 3x3 ladder shaft standing in for the entrance &mdash; authored before the jigsaw entrance existed
 * and never revisited. That made it actively misleading in exactly the two places bugs were hiding:
 * it showed a synthetic entrance rather than {@code entrance/surface_entrance}, and, like every
 * other headless path in this project, it skipped {@code postProcess}. Two of the four defects found
 * in game on Aug 03 lived in that gap.</p>
 *
 * <h2>What changed for the worse</h2>
 * <p>Two things, both inherited from {@code /place} and both worth knowing before debugging with
 * this.</p>
 * <ul>
 *   <li><strong>The target chunks must already be loaded</strong>, and vanilla can silently skip
 *       pieces landing in chunks that are already fully generated. The old command wrote blocks
 *       unconditionally and never had this problem.</li>
 *   <li><strong>The seed is no longer the position alone.</strong> {@code DungeonStructure} seeds
 *       from {@code chunkPos.toLong() ^ worldSeed}, so reproducing a dungeon headlessly now needs
 *       the world seed too. The effective seed is printed on success for exactly that reason
 *       &mdash; feed it to {@code ./gradlew floorplan -Pseed=...}.</li>
 * </ul>
 *
 * @author Mark Gottschling
 */
public class SpawnDungeonCommand {

    /** The structure this command places. Registered from {@code worldgen/structure/dungeon.json}. */
    private static final ResourceKey<Structure> DUNGEON = ResourceKey.create(
            Registries.STRUCTURE, new ResourceLocation(Dungeons.MOD_ID, "dungeon"));

    private static final SuggestionProvider<CommandSourceStack> SIZES = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(DungeonSize.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), builder);

    private static final SuggestionProvider<CommandSourceStack> MOTIFS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(DungeonMotif.values()).map(DungeonMotif::getValue), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("d2-generate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> spawn(context, null, null, null))
                        .then(Commands.argument("size", StringArgumentType.word())
                                .suggests(SIZES)
                                .executes(context -> spawn(context, size(context), null, null))
                                .then(Commands.argument("floors", IntegerArgumentType.integer(1, 8))
                                        .executes(context -> spawn(context, size(context), floors(context), null))
                                        .then(Commands.argument("motif", StringArgumentType.word())
                                                .suggests(MOTIFS)
                                                .executes(context -> spawn(context, size(context), floors(context),
                                                        StringArgumentType.getString(context, "motif"))))))));
    }

    private static DungeonSize size(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "size");
        try {
            return DungeonSize.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Brigadier's own word() argument can't reject this, and a typo'd size silently falling
            // through to the seed roll is the kind of "it generated, just not what I asked for"
            // that costs an afternoon.
            throw new IllegalArgumentException("unknown size '" + raw + "'; expected one of "
                    + Arrays.toString(DungeonSize.values()));
        }
    }

    private static int floors(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "floors");
    }

    private static int spawn(CommandContext<CommandSourceStack> context,
                             DungeonSize size, Integer floors, String motif) {
        CommandSourceStack source = context.getSource();
        try {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
            ServerLevel level = source.getLevel();

            if (motif != null && DungeonMotif.getByValue(motif) == null) {
                source.sendFailure(Component.literal("Unknown motif '" + motif + "'."));
                return 0;
            }

            Holder.Reference<Structure> structure = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getHolder(DUNGEON)
                    .orElse(null);
            if (structure == null) {
                source.sendFailure(Component.literal(
                        "Structure " + DUNGEON.location() + " is not registered -- is the datapack loaded?"));
                return 0;
            }

            // The seed DungeonStructure will use, computed the same way it does. Printed so a
            // dungeon built here can be reproduced headlessly by the floorplan tool.
            long seed = new ChunkPos(pos).toLong() ^ level.getSeed();

            DungeonStructure.DebugOverrides overrides =
                    (size == null && floors == null && motif == null)
                            ? null
                            : new DungeonStructure.DebugOverrides(size, floors, motif);

            int result = DungeonStructure.withDebugOverrides(overrides, () -> {
                try {
                    return PlaceCommand.placeStructure(source, structure, pos);
                } catch (CommandSyntaxException e) {
                    // Almost always "chunks not loaded". Rethrown unwrapped below.
                    throw new PlacementFailed(e);
                }
            });

            source.sendSuccess(() -> Component.literal(
                    "Dungeon generated at " + pos.getX() + " " + pos.getZ()
                            + "\n  size=" + (size == null ? "(rolled)" : size)
                            + "  floors=" + (floors == null ? "(rolled)" : floors)
                            + "  motif=" + (motif == null ? DungeonMotif.CLASSIC.getValue() : motif)
                            + "\n  planner seed: " + seed
                            + "\n  reproduce: ./gradlew floorplan -Pseed=" + seed), true);
            return result;
        } catch (PlacementFailed e) {
            source.sendFailure(Component.literal("d2-generate: " + e.getCause().getMessage()
                    + " (the target chunks have to be loaded -- try teleporting there first)"));
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("d2-generate: " + e.getMessage()));
        } catch (Exception e) {
            // Vanilla's dispatcher reports an exception here as a bare chat message and nothing
            // reaches the logs, so log it through our own logger before giving up on it.
            Dungeons.LOGGER.error("d2-generate failed: ", e);
            source.sendFailure(Component.literal("d2-generate error: " + e.getMessage()));
        }
        return 0;
    }

    /** Carries a checked {@link CommandSyntaxException} out of the {@code Supplier} lambda. */
    private static class PlacementFailed extends RuntimeException {
        PlacementFailed(CommandSyntaxException cause) {
            super(cause);
        }
    }
}

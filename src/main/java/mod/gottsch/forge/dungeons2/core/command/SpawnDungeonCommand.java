package mod.gottsch.forge.dungeons2.core.command;

import com.mojang.brigadier.CommandDispatcher;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonGenerator;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;

public class SpawnDungeonCommand {

    /**
     *
     * @param dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher
                .register(Commands.literal("d2-generate")
                        .requires(source -> {
                            return source.hasPermission(2);
                        })
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(source -> {
                                    return spawn(source.getSource(), BlockPosArgument.getLoadedBlockPos(source, "pos"));
                                })
                        )
                );
    }

    private static int spawn(CommandSourceStack sourceStack, BlockPos pos) {

        try {
            // TODO generate a dungeon at pos without any checks or rules
            // ie can generate in the area without any support etc
//            MazeLevelGenerator2D generator = new MazeLevelGenerator2D.Builder()
//                    .with($ -> {
//                        $.width = 75;
//                        $.height = 75;
//                        $.numberOfRooms = 25;
//                        $.attemptsMax = 500;
//                        $.runFactor = 1.0;
//                        $.curveFactor = 0.75;
//                        $.minCorridorSize = 75;
//                        $.maxCorridorSize = 150;
//                    }).build();
//
//            Optional<ILevel2D> level = generator.generate();

            DungeonGenerator gen = new DungeonGenerator();
            gen.generate(sourceStack.getLevel(), sourceStack.getLevel().getRandom(), new Coords(sourceStack.getPosition()));

        } catch(Exception e) {
            Dungeons.LOGGER.error("an error occurred: ", e);
        }

        return 1;
    }
}

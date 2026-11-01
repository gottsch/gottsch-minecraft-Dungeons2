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
package mod.gottsch.forge.dungeons2.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mod.gottsch.forge.dungeons2.core.world.DungeonLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code /d2-locate}: the nearest dungeon in a particular biome, for checking that a biome mod's
 * biomes get dungeons without teleporting about until one turns up.
 * <ul>
 *   <li>{@code /d2-locate biome <biome|#tag> [rings]} - in that biome, or any biome in that tag</li>
 *   <li>{@code /d2-locate mod <namespace> [rings]} - in any biome of that namespace, e.g. terralith</li>
 * </ul>
 * <p>{@code rings} is how many placement regions out to search (default {@value #DEFAULT_RINGS};
 * a region is the structure set's spacing, 40 chunks). It runs on the server thread and can take a few
 * seconds at the far end, since every candidate start has its surface height and biome read from the
 * noise. Only a candidate in a wanted biome is checked for a real start, which may generate that one
 * chunk to {@code STRUCTURE_STARTS}, as vanilla's locate does.</p>
 *
 * @author Mark Gottschling on 10/29/2026
 */
public final class LocateDungeonCommand {

    private static final int DEFAULT_RINGS = 32;
    private static final int MAX_RINGS = 100;

    private LocateDungeonCommand() {}

    private static final SuggestionProvider<CommandSourceStack> NAMESPACES = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().registryAccess().registryOrThrow(Registries.BIOME)
                    .keySet().stream().map(id -> id.getNamespace()).distinct(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext build) {
        dispatcher.register(Commands.literal("d2-locate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("biome")
                        .then(Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(build, Registries.BIOME))
                                .executes(context -> byBiome(context, DEFAULT_RINGS))
                                .then(Commands.argument("rings", IntegerArgumentType.integer(1, MAX_RINGS))
                                        .executes(context -> byBiome(context, IntegerArgumentType.getInteger(context, "rings"))))))
                .then(Commands.literal("mod")
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .suggests(NAMESPACES)
                                .executes(context -> byMod(context, DEFAULT_RINGS))
                                .then(Commands.argument("rings", IntegerArgumentType.integer(1, MAX_RINGS))
                                        .executes(context -> byMod(context, IntegerArgumentType.getInteger(context, "rings")))))));
    }

    private static int byBiome(CommandContext<CommandSourceStack> context, int rings) throws CommandSyntaxException {
        ResourceOrTagArgument.Result<Biome> biome = ResourceOrTagArgument.getResourceOrTag(context, "biome", Registries.BIOME);
        return locate(context.getSource(), biome, biome.asPrintable(), rings);
    }

    private static int byMod(CommandContext<CommandSourceStack> context, int rings) {
        String namespace = StringArgumentType.getString(context, "namespace");
        Predicate<Holder<Biome>> inMod = holder -> holder.unwrapKey()
                .map(key -> key.location().getNamespace().equals(namespace)).orElse(false);
        return locate(context.getSource(), inMod, "any " + namespace + " biome", rings);
    }

    private static int locate(CommandSourceStack source, Predicate<Holder<Biome>> biomes, String wanted, int rings) {
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.literal("d2-locate: dungeons generate in the overworld only."));
            return 0;
        }
        BlockPos from = BlockPos.containing(source.getPosition());
        Optional<DungeonLocator.Found> found = DungeonLocator.nearest(level, from, rings, biomes);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No dungeon in " + wanted + " within " + rings
                    + " regions (" + rings * 40 * 16 + " blocks)."));
            return 0;
        }
        BlockPos at = found.get().entrance();
        int distance = Mth.floor(Mth.sqrt((float) from.distSqr(new BlockPos(at.getX(), from.getY(), at.getZ()))));
        String biomeName = found.get().biome() == null ? "?" : found.get().biome().unwrapKey()
                .map(key -> key.location().toString()).orElse("?");
        Component coordinates = ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", at.getX(), "~", at.getZ()))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/tp @s " + at.getX() + " ~ " + at.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.literal("Nearest dungeon in " + biomeName + " is at ")
                .append(coordinates).append(" (" + distance + " blocks away)"), false);
        return distance;
    }
}

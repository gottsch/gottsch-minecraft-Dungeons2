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
package mod.gottsch.forge.dungeons2.core.event;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.world.BossRoomLock;
import mod.gottsch.forge.dungeons2.core.world.BossRooms;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A boss room cannot be dismantled until its boss is dead.
 *
 * <p>The design problem this exists for: a boss room's reward is behind its boss, but a room is
 * made of blocks, and a player with a pickaxe can tunnel in through the floor, take the chest and
 * leave without ever meeting what the room was built around. Every other part of the encounter
 * &mdash; the tier, the escort, the reward &mdash; assumes the fight happened.</p>
 *
 * <h2>What is protected, and until when</h2>
 * <p>Every block inside the boss room piece's bounding box: walls, floor, ceiling and the contents.
 * The floor matters as much as the walls, since tunnelling <em>under</em> the chest is the specific
 * hole being closed. Protection ends the moment a boss dies inside that box, and the room is
 * recorded cleared permanently &mdash; the room is a lock on the fight, not a museum afterwards.</p>
 *
 * <h2>The asymmetry with SmashBlocksGoal is deliberate</h2>
 * <p>A Minotaur may break these walls and a player may not. That reads oddly written down and
 * correctly in play: a mob breaching a wall <em>is</em> the fight, and a player breaching one is
 * skipping it. Mechanically they do not even collide &mdash; the goal calls {@code destroyBlock}
 * directly and never raises {@link BlockEvent.BreakEvent}, which is what this listens to.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BossRoomProtectionEvent {

    /**
     * The mob sets whose members count as "the boss" for the purpose of unlocking a room.
     *
     * <p>The boss sets only &mdash; deliberately not the escort sets. Killing the escort and
     * leaving the boss alive is exactly the outcome the lock is meant to refuse to reward, and a
     * room that opened when the last grave zombie fell would be no lock at all.</p>
     *
     * <p>Read from the mob sets rather than hard-coded as entity types so that adding a boss to a
     * tier arms it here with no code change, which is how every other boss-tier decision in this
     * mod is made.</p>
     */
    private static final List<ResourceLocation> BOSS_MOB_SETS = List.of(
            new ResourceLocation(Dungeons.MOD_ID, "small_dungeon_boss"),
            new ResourceLocation(Dungeons.MOD_ID, "medium_dungeon_boss"),
            new ResourceLocation(Dungeons.MOD_ID, "large_dungeon_boss"));

    private static final String BLOCKED_MESSAGE = "message.dungeons2.boss_room_sealed";

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        // A creative player is editing, not playing, and locking them out of their own world would
        // be a bug report rather than a protected fight.
        if (player != null && player.isCreative()) {
            return;
        }
        BlockPos pos = event.getPos();
        Optional<BoundingBox> room = BossRooms.at(level, pos);
        if (room.isEmpty()) {
            return;
        }
        if (BossRoomLock.get(level).isCleared(BossRooms.keyOf(room.get()))) {
            return;
        }
        event.setCanceled(true);
        if (player != null) {
            // Above the hotbar rather than in chat: it fires per attempted swing, and a player
            // holding the button down would otherwise fill their chat log with it.
            player.displayClientMessage(Component.translatable(BLOCKED_MESSAGE), true);
        }
    }

    /**
     * A blast may not take out a boss room wall either.
     *
     * <p>TNT is the obvious way round a break gate: a player who cannot mine the floor can place a
     * charge on it instead, and the blast never raises {@link BlockEvent.BreakEvent}. Affected
     * positions inside an uncleared room are dropped from the blast list rather than the explosion
     * being cancelled, so the charge still goes off, still throws the player, and still destroys
     * everything <em>outside</em> the room &mdash; only the protected shell survives.</p>
     *
     * <p>This catches creeper blasts as well as placed charges. That is wanted: a boss room opened
     * by its own escort blowing up would be the same skipped fight, arrived at by luck.</p>
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) {
            return;
        }
        List<BoundingBox> rooms = unclearedRoomsAt(level,
                BlockPos.containing(event.getExplosion().getPosition()));
        if (rooms.isEmpty()) {
            return;
        }
        affected.removeIf(pos -> isInAny(rooms, pos));
    }

    /**
     * Nor may a piston pull the room apart.
     *
     * <p>The other way round a break gate, and the quieter one: a sticky piston can pull a wall
     * block out from the far side, and a piston can shove a hole's worth of blocks into a room, all
     * without a break event. Cancelled outright rather than filtered &mdash; unlike a blast, a
     * partially-honoured push has no sensible meaning.</p>
     *
     * <p>Both ends of the move are checked. The piston itself may be outside the room while the
     * blocks it moves are inside it, which is precisely how the exploit would be built.</p>
     */
    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BoundingBox> rooms = unclearedRoomsAt(level, event.getPos());
        if (rooms.isEmpty()) {
            return;
        }
        if (isInAny(rooms, event.getPos()) || isInAny(rooms, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        // null for the move types that shift no blocks; resolve() false means the push was going to
        // fail anyway, and there is nothing to protect against.
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        for (BlockPos pos : resolver.getToPush()) {
            if (isInAny(rooms, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (isInAny(rooms, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** The boss rooms of the dungeon at {@code pos} that still hold a living boss. */
    private static List<BoundingBox> unclearedRoomsAt(ServerLevel level, BlockPos pos) {
        List<BoundingBox> all = BossRooms.allAt(level, pos);
        if (all.isEmpty()) {
            return all;
        }
        BossRoomLock lock = BossRoomLock.get(level);
        return all.stream()
                .filter(room -> !lock.isCleared(BossRooms.keyOf(room)))
                .toList();
    }

    private static boolean isInAny(List<BoundingBox> rooms, BlockPos pos) {
        return rooms.stream().anyMatch(room -> room.isInside(pos));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isBoss(entity.getType())) {
            return;
        }
        // The boss's own position, not its killer's: a boss lured to the doorway and killed one
        // block outside has still been fought, but one that wandered off down a corridor should not
        // unlock a room the player never entered.
        BossRooms.at(level, entity.blockPosition())
                .ifPresent(room -> BossRoomLock.get(level).markCleared(BossRooms.keyOf(room)));
    }

    /**
     * Whether this type is named by any boss mob set.
     *
     * <p>Resolved on each call rather than cached: {@code MobSetDataRegistry} is refilled on every
     * datapack reload, and a cache built at mod-init would hold the pre-reload roster forever. The
     * call happens once per boss death, which is not a rate worth optimising for.</p>
     */
    private static boolean isBoss(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) {
            return false;
        }
        Set<ResourceLocation> bosses = BOSS_MOB_SETS.stream()
                .map(MobSetDataRegistry::get)
                .flatMap(Optional::stream)
                .flatMap(data -> data.getMobs().stream())
                .map(mob -> mob.id())
                .collect(Collectors.toSet());
        return bosses.contains(id);
    }
}

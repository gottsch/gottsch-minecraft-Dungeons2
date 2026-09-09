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
import mod.gottsch.forge.dungeons2.core.world.BossRoomRegistry;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * You cannot dismantle the room around a living boss.
 *
 * <p>The design problem: a boss room's reward sits behind its boss, but a room is made of blocks, and
 * a player with a pickaxe (or TNT, or a piston) can tunnel in through the floor, take the chest and
 * leave without ever meeting what the room was built around.</p>
 *
 * <h2>The room is recorded when it GENERATES</h2>
 * <p>{@code SpawnerMarkerProcessor} measures the piece holding the boss spawner and files it in
 * {@link BossRoomRegistry}. That is the only moment the room's extent is knowable exactly, and it is
 * knowable for free: the block list being placed <em>is</em> the room. Everything here is then two
 * containment tests &mdash; which recorded room holds this block, and is a boss alive inside it.</p>
 *
 * <p>Three earlier attempts are worth remembering, because each looked reasonable:</p>
 * <ol>
 *   <li><strong>Ask the structure at runtime.</strong> Never worked once. {@code getStructureAt}
 *       reads the chunk's references back to the structure, vanilla writes those only within eight
 *       chunks of a structure's start, and a dungeon sprawls past 128 blocks &mdash; so from inside
 *       its own boss room the dungeon is invisible. Failed closed and silently, for a day.</li>
 *   <li><strong>A box around the boss.</strong> Cheap and visibly wrong: round where a room is
 *       square, and sliding about as the boss paced. Rejected outright (Mark, 2026-09-08): "it
 *       either needs to fit exactly on the boss room, or don't do it. Having one wall possibly
 *       breakable while the other wall and partial hallway is protected, doesn't work."</li>
 *   <li><strong>Flood-fill the room at runtime.</strong> Exact while the room is sealed &mdash; and
 *       the weathering pass punches holes through walls routinely, after which the fill escapes into
 *       the corridor beyond. It also paid for a fill on every block break near a boss.</li>
 * </ol>
 *
 * <p>Recording at generation answers all three. The measurement happens before decay, before a
 * player, before anything: <strong>weathering holes, open doors and existing breaches change
 * nothing</strong>, because the room was measured when it was still pristine. Rotation is likewise
 * irrelevant &mdash; the positions recorded are the ones actually written to the world.</p>
 *
 * <h2>What this deliberately does not cover</h2>
 * <p><strong>A boss that has not spawned yet guards nothing</strong>, because the protection asks
 * whether a boss is alive in the room. The boss room's spawner is a proximity spawner, so
 * approaching the chest is very likely to have released it first, but a player tunnelling from far
 * enough out could in principle beat it.</p>
 *
 * <p><strong>Rooms in worlds generated before this existed are not recorded</strong>, and cannot be
 * &mdash; there is no way to recover their bounds afterwards. They are unprotected for ever.</p>
 *
 * <h2>The asymmetry with SmashBlocksGoal is deliberate</h2>
 * <p>A Minotaur may break these walls and a player may not. A mob breaching a wall <em>is</em> the
 * fight; a player breaching one is skipping it. They do not collide mechanically either &mdash; the
 * goal calls {@code destroyBlock} directly and never raises {@link BlockEvent.BreakEvent}.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BossRoomProtectionEvent {

    /**
     * The mob sets whose members guard the blocks around them.
     *
     * <p>The boss sets only, deliberately not the escort sets: a room that opened when the last
     * grave zombie fell would be no lock. Read from the mob sets rather than hard-coded as entity
     * types, so adding a boss to a tier arms it here with no code change.</p>
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
        // Search wide enough that a boss anchored across the room still counts: the anchor, not the
        // boss, is the centre of the volume, and the two can be up to its restriction radius apart.
        if (guarded(level, event.getPos())) {
            event.setCanceled(true);
            if (player != null) {
                // Above the hotbar rather than in chat: it fires per attempted swing, and a player
                // holding the button down would otherwise fill their chat log with it.
                player.displayClientMessage(Component.translatable(BLOCKED_MESSAGE), true);
            }
        }
    }

    /**
     * A blast may not take the room out either.
     *
     * <p>Guarded positions are dropped from the blast list rather than the explosion being
     * cancelled, so the charge still goes off, still throws the player, and still destroys
     * everything outside the guarded volume. Creeper blasts count too: a boss room opened by its own
     * escort exploding is the same skipped fight, arrived at by luck.</p>
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
        affected.removeIf(pos -> guarded(level, pos));
    }

    /**
     * Nor may a piston pull it apart.
     *
     * <p>Cancelled outright rather than filtered &mdash; unlike a blast, a partly-honoured push has
     * no sensible meaning. Both ends are checked, because the exploit is a piston <em>outside</em>
     * the guarded volume moving blocks that are inside it.</p>
     */
    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (guarded(level, event.getPos()) || guarded(level, event.getFaceOffsetPos())) {
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
            if (guarded(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (guarded(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Whether {@code pos} is in a boss room that still holds a living boss.
     *
     * <p>Two cheap tests and no search of the world: which recorded room contains the block (a
     * containment check against a handful of boxes), then whether any living boss is inside that
     * same room. The room came from {@code SpawnerMarkerProcessor} when it generated, so it is the
     * template's real extent &mdash; every wall, the ceiling and the floor plane, at whatever size
     * and rotation was placed, and unaffected by anything that happened to the room afterwards.</p>
     */
    private static boolean guarded(ServerLevel level, BlockPos pos) {
        Optional<BoundingBox> room = BossRoomRegistry.get(level).roomAt(pos);
        if (room.isEmpty()) {
            return false;
        }
        return !bossesIn(level, room.get()).isEmpty();
    }

    /** Living bosses inside a room. Bounded by the room, so this never scans the wider world. */
    private static List<Mob> bossesIn(ServerLevel level, BoundingBox room) {
        AABB area = new AABB(room.minX(), room.minY(), room.minZ(),
                room.maxX() + 1.0D, room.maxY() + 1.0D, room.maxZ() + 1.0D);
        return level.getEntitiesOfClass(Mob.class, area,
                mob -> mob.isAlive() && isBoss(mob.getType()));
    }

    /**
     * Whether this type is named by any boss mob set.
     *
     * <p>Resolved on each call rather than cached: {@code MobSetDataRegistry} is refilled on every
     * datapack reload, and a cache built at mod-init would hold the pre-reload roster for ever. The
     * calls happen on block breaks and explosions, which is not a rate worth optimising for.</p>
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

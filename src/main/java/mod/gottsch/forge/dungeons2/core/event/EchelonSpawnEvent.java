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
import mod.gottsch.forge.dungeons2.core.config.EchelonConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigHelper;
import mod.gottsch.forge.dungeons2.core.integration.EchelonsIntegration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Scales a mob Dungeons2 spawned by the depth it was spawned at, through the Enemy Echelons API.
 * Everything here is inert unless EE is installed &mdash; see {@link EchelonsIntegration}.
 *
 * <h2>Why {@code FinalizeSpawn}</h2>
 * <p>EE keeps the FIRST difficulty a mob is given, and Stronger Mobs Below gives one on
 * {@code EntityJoinLevelEvent}. {@code FinalizeSpawn} fires before the mob is added, on both of
 * Dungeons2's spawner kinds: GottschCore's {@code SpawnUtil} posts it ahead of
 * {@code addFreshEntity}, and Forge patches vanilla's {@code BaseSpawner} to post it unconditionally
 * before {@code tryAddFreshEntityWithPassengers}. So depth wins over world Y without any event
 * priority games, and on every mob, not just the ones whose handler happens to sort first.</p>
 *
 * <h2>How a spawn says where it came from</h2>
 * <ul>
 *   <li><strong>Proximity spawner</strong> ({@code DungeonSpawnerBlockEntity}): GottschCore's
 *       {@code SpawnUtil} passes no spawner to the event, so the block entity announces itself
 *       through {@link #during} around its own spawn loop. Server thread, synchronous &mdash; the
 *       event fires inside that call or not at all.</li>
 *   <li><strong>Vanilla cage</strong>: the event does carry the {@code BaseSpawner}, and its block
 *       entity's Forge persistent data holds {@link #ORIGIN}, written at generation. Not the
 *       entity tag in {@code SpawnData}: a spawn entry with anything besides {@code id} makes
 *       vanilla skip {@code finalizeSpawn}, and a skeleton would come out of the cage without its
 *       bow.</li>
 * </ul>
 * <p>A spawn with neither &mdash; natural spawns, other mods' spawners, vanilla dungeons' cages
 * &mdash; is not ours and is left alone.</p>
 *
 * <h2>Bosses are never scaled, by anyone</h2>
 * <p>Mark, 2026-09-23: no "level 2 Beholder". A boss's fight is tuned by hand, so every
 * {@code DungeonsEntities.MINI_BOSSES} mob is pinned at difficulty 0, whatever spawned it. Twice:
 * in {@code FinalizeSpawn}, before this class would scale it by depth, and again on
 * {@code EntityJoinLevelEvent} at HIGHEST priority, for any route that skips finalize. HIGHEST is
 * what beats SMB's own join handler, which would otherwise scale a boss by its (deep) world Y. The
 * pin is a no-op on a mob that already has a difficulty, so a chunk reload changes nothing.</p>
 *
 * <h2>Authored spawners carry no floor</h2>
 * <p>A spawner placed by a template marker (every prefab room, every boss room) reads
 * {@code UNKNOWN_FLOOR}, because nothing in a jigsaw placement knows its dungeon floor (see
 * {@code SpawnerTagParityTest#theAuthoredPathCannotKnowItsFloor}). Those mobs are left to SMB.</p>
 *
 * @author Mark Gottschling on Sep 23, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EchelonSpawnEvent {

    /** Compound key in a vanilla cage's Forge persistent data. */
    public static final String ORIGIN = "dungeons2";
    public static final String MOTIF = "motif";
    public static final String FLOOR_INDEX = "floorIndex";

    /**
     * On a scaled mob's persistent data: which motif's {@code echelon} block scaled it, so its XP
     * can be scaled by the same table when it dies. Present only on mobs Dungeons2 scaled.
     */
    public static final String SCALED_BY_MOTIF = "dungeons2:echelonMotif";

    /** Where a spawn is happening, when a proximity spawner has said so. */
    public record Origin(String motif, int floorIndex) {}

    private static final ThreadLocal<Origin> CURRENT = new ThreadLocal<>();

    /**
     * Runs {@code spawn} with every mob it finalizes attributed to {@code motif}/{@code floorIndex}.
     * Restores the previous origin afterwards rather than clearing it, so a spawn that triggers
     * another (a warlord's escort) nests correctly.
     */
    public static void during(String motif, int floorIndex, Runnable spawn) {
        if (motif == null || motif.isBlank() || floorIndex < 0) {
            spawn.run();
            return;
        }
        Origin previous = CURRENT.get();
        CURRENT.set(new Origin(motif, floorIndex));
        try {
            spawn.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The persistent-data compound a generated vanilla cage carries. */
    public static CompoundTag originTag(String motif, int floorIndex) {
        CompoundTag tag = new CompoundTag();
        tag.putString(MOTIF, motif);
        tag.putInt(FLOOR_INDEX, floorIndex);
        return tag;
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getLevel().isClientSide() || !EchelonsIntegration.isLoaded()) {
            return;
        }
        Mob mob = event.getEntity();
        if (MiniBossAnchorEvent.isMiniBoss(mob)) {
            EchelonsIntegration.pinUnscaled(mob);
            return;
        }
        Optional<Origin> origin = originOf(event.getSpawner());
        if (origin.isEmpty()) {
            return;
        }
        MotifConfig motif = MotifConfigHelper.get(event.getLevel().registryAccess(), origin.get().motif());
        Optional<EchelonConfig> echelon = motif.echelon();
        OptionalInt difficulty = motif.bandFor(origin.get().floorIndex())
                .map(band -> band.drawDifficulty(mob.getRandom()))
                .orElse(OptionalInt.empty());
        // Either half missing means the motif has no opinion, and the mob stays SMB's to scale.
        if (echelon.isEmpty() || difficulty.isEmpty()) {
            return;
        }
        if (EchelonsIntegration.apply(mob, echelon.get(), difficulty.getAsInt())) {
            mob.getPersistentData().putString(SCALED_BY_MOTIF, origin.get().motif());
            Dungeons.LOGGER.info("[D2-ECHELON] {} at {} floorIndex={} motif={} difficulty={}",
                    net.minecraft.world.entity.EntityType.getKey(mob.getType()),
                    mob.blockPosition().toShortString(), origin.get().floorIndex(),
                    origin.get().motif(), difficulty.getAsInt());
        }
    }

    /** The backstop for bosses: see the class javadoc. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || !MiniBossAnchorEvent.isMiniBoss(mob)) {
            return;
        }
        EchelonsIntegration.pinUnscaled(mob);
    }

    /**
     * XP, which EE only scales from its GLOBAL registry and so never from ours. LOW priority so
     * this runs after EE's own handler and has the last word: if SMB's config happens to cover the
     * mob, EE will have multiplied SMB's {@code xpFactor} by OUR difficulty, a figure nobody
     * authored. Computed from the original XP, so it replaces that rather than compounding it.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || !mob.getPersistentData().contains(SCALED_BY_MOTIF, Tag.TAG_STRING)) {
            return;
        }
        OptionalInt difficulty = EchelonsIntegration.difficultyOf(mob);
        if (difficulty.isEmpty()) {
            return;
        }
        RegistryAccess access = mob.level().registryAccess();
        MotifConfigHelper.get(access, mob.getPersistentData().getString(SCALED_BY_MOTIF)).echelon()
                .ifPresent(echelon -> event.setDroppedExperience(
                        echelon.scaleXp(event.getOriginalExperience(), difficulty.getAsInt())));
    }

    private static Optional<Origin> originOf(BaseSpawner spawner) {
        if (spawner != null) {
            BlockEntity cage = spawner.getSpawnerBlockEntity();
            if (cage == null || !cage.getPersistentData().contains(ORIGIN, Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            CompoundTag tag = cage.getPersistentData().getCompound(ORIGIN);
            return Optional.of(new Origin(tag.getString(MOTIF), tag.getInt(FLOOR_INDEX)));
        }
        return Optional.ofNullable(CURRENT.get());
    }
}

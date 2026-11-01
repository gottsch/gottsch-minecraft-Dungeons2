package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.SecretDoorway;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.secret.SecretRoomPlanner.RoomKey;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonCorridorPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonDoorPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonPieceEmitter;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonRoomPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.core.world.structure.SecretRooms;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes the first secret room the classic motif grows, with its door and the corridor in front of
 * it, to {@code build/secret-room.jsonl} &mdash; one {@code x y z id {props}} line per block, in
 * render order (corridors, room, door; last writer wins), undecorated. It is input for the
 * isometric renderer, which draws a room from real assets without launching the game.
 */
class SecretRoomSnapshot {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void writeTheFirstSecretRoom() throws IOException {
        MotifConfig motif = MotifConfigs.load("classic");
        for (long seed = 0; seed < 60; seed++) {
            DungeonLayout layout = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic",
                    new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM).withCorridorWidth(3)
                    .withCorridorStyles(DungeonStructure.corridorStyleWeights(motif.corridor()))
                    .plan().orElseThrow();
            Map<RoomKey, SecretDoorway> hideable = SecretRooms.hideable(layout, motif, id -> true);
            List<StructurePiece> pieces = new ArrayList<>(DungeonPieceEmitter.emitTerrain(layout, 0, 0));
            pieces.addAll(DungeonPieceEmitter.emitDoors(layout, 0, 0));
            if (SecretRooms.apply(pieces, hideable, Optional.empty(), motif) == 0) {
                continue;
            }
            DungeonDoorPiece door = pieces.stream()
                    .filter(p -> p instanceof DungeonDoorPiece d && d.getSecret() != null)
                    .map(p -> (DungeonDoorPiece) p).findFirst().orElseThrow();
            int floorIndex = door.getFloorIndex();
            SecretDoorway secret = door.getSecret();
            MotifConfig floorMotif = motif.forFloor(floorIndex);
            DungeonRoomPiece room = pieces.stream()
                    .filter(p -> p instanceof DungeonRoomPiece r && secret.equals(r.getSecretDoorway()))
                    .map(p -> (DungeonRoomPiece) p).findFirst().orElseThrow();

            List<BlockPlacement> out = new ArrayList<>();
            int reach = 6;
            for (StructurePiece p : pieces) {
                if (p instanceof DungeonCorridorPiece c && c.getFloorIndex() == floorIndex) {
                    for (BlockPlacement b : c.renderPlacements(floorMotif)) {
                        if (Math.abs(b.getX() - secret.x()) <= reach && Math.abs(b.getZ() - secret.z()) <= reach) {
                            out.add(b);
                        }
                    }
                }
            }
            out.addAll(room.renderRoom(floorMotif).getBlocks());
            out.addAll(door.renderPlacements(floorMotif, id -> true));

            StringBuilder sb = new StringBuilder();
            sb.append("# seed ").append(seed).append(" floor ").append(floorIndex).append(" room ")
                    .append(room.getRoom().getId()).append(' ').append(room.rolledScheme(floorMotif).name())
                    .append(" inward ").append(secret.inward()).append('\n');
            for (BlockPlacement b : out) {
                sb.append(b.getX()).append(' ').append(b.getY()).append(' ').append(b.getZ()).append(' ')
                        .append(b.getBlockId()).append(' ').append(b.getProperties().toString()
                                .replace(" ", "")).append('\n');
            }
            Path file = Path.of("build/secret-room.jsonl");
            Files.writeString(file, sb.toString());
            System.out.println("[secret snapshot] " + file.toAbsolutePath() + " seed " + seed);
            return;
        }
        throw new AssertionError("no secret room in 60 seeds");
    }
}

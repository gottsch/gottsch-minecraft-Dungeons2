package mod.gottsch.forge.dungeons2.core.generator.dungeon;

////////////// processing constants ///////////////
public enum CellType {
    ROCK(0),
    WALL(1),
    CONNECTOR(2),
    DOOR(3),
    CORRIDOR(4),
    ROOM(5);

    final int id;

    CellType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}

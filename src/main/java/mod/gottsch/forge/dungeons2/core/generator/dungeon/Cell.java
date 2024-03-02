package mod.gottsch.forge.dungeons2.core.generator.dungeon;

public class Cell {
    private int x;
    private int y;

    private int regionId;
    private int sectionId;

    private CellType type;

    // the direction the corridor is aligned to (if any ex. a corner doesn't have direction)
    private Direction2D direction; // TODO would this be part of section?

    public Cell(int x, int y) {
        this(x, y, CellType.ROCK);
    }

    public Cell(int x, int y, CellType type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getRegionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }

    public CellType getType() {
        return type;
    }

    public void setType(CellType type) {
        this.type = type;
    }
}

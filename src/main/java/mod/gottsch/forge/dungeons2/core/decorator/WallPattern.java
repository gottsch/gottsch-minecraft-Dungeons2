package mod.gottsch.forge.dungeons2.core.decorator;

public enum WallPattern {
    CORNER,
    TOP_CORNER,
    BOTTOM_CORNER,
    WALL,
    ALTERNATE_WALL;

    public WallPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}

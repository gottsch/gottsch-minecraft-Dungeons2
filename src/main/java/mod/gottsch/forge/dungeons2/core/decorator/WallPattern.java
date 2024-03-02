package mod.gottsch.forge.dungeons2.core.decorator;

public enum WallPattern {
    TOP_CORNER,
    BOTTOM_CORNER;

    public WallPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}

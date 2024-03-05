package mod.gottsch.forge.dungeons2.core.pattern.wall;

import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;

public enum WallPattern implements IPatternEnum {
    CORNER,
    TOP_CORNER,
    BOTTOM_CORNER,
    WALL,
    ALTERNATE_WALL,
    WINDOW;

    public WallPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name().toLowerCase();
    }
}

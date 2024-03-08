package mod.gottsch.forge.dungeons2.core.pattern.floor;

import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;

public enum FloorDrainagePattern implements IPatternEnum {
    CORNER,
    ALTERNATE_CORNER,
    CENTER,
    ALTERNATE_CENTER,
    GUTTER,
    ALTERNATE_GUTTER;

    public FloorDrainagePattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name();
    }
}

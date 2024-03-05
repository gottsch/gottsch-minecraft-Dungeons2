package mod.gottsch.forge.dungeons2.core.pattern.floor;

import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;

public enum CorridorFloorPattern implements IPatternEnum {
    FLOOR,
    ALTERNATE_FLOOR,
    INTERSECTION,
    THRESHOLD;

    public CorridorFloorPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name();
    }
}

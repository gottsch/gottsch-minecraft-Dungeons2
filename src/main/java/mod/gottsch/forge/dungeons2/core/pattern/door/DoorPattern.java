package mod.gottsch.forge.dungeons2.core.pattern.door;

import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;

/**
 * @author Mark Gottschling on March 4, 2024
 *
 */
public enum DoorPattern implements IPatternEnum {
    FLOOR,
    ALTERNATE_FLOOR,
    LINTEL,
    ALTERNATE_LINTEL,
    DOOR;

    public DoorPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name();
    }
}

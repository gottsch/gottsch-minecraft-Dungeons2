package mod.gottsch.forge.dungeons2.core.pattern.ceiling;

import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;

/**
 * @author Mark Gottschling on March 4, 2024
 *
 */
public enum CorridorCeilingPattern implements IPatternEnum {
    CEILING,
    ALTERNATE_CEILING,
    INTERSECTION,
    THRESHOLD;

    public CorridorCeilingPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name();
    }
}
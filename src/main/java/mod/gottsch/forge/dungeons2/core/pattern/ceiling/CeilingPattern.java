package mod.gottsch.forge.dungeons2.core.pattern.ceiling;

import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;

/**
 * @author Mark Gottschling on March 4, 2024
 *
 */
public enum CeilingPattern implements IPatternEnum {
    CORNER,
    CENTER,
    CEILING,
    ALTERNATE_CEILING;

    public CeilingPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name();
    }
}

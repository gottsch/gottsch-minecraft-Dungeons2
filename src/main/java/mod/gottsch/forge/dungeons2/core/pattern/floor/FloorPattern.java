package mod.gottsch.forge.dungeons2.core.pattern.floor;

// TODO this is too generic for all floor decorators
// TODO ie. should have a enum for each type of decorator
// DefaultBorderDecorator would fetch a different set of block than
// the BasaltBorderDecorator or the SewerBorderDecorator which may or may not
// all use the FloorBorderPattern
// TODO so should have FloorPattern (FLOOR, ALT_FLOOR), FloorBorderPattern(CORNER, BORDER, ALT_BORDER),
// FloorSewerBorderPattern(CORNER, BORDER, ALT_BORDER) etc.
// TODO this doesn't seem like the most extendable solution
// this would require a ton of enums, BUT the enums are used for keys,
// which you will have lots of keys regardless
// OR should these blocks just be hard-coded? no. because don't need like a DesertSimpleFloorDecorator,
// just need to supply a different motif.


import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;

/**
 * @author Mark Gottschling on March 2, 2024
 *
 */
public enum FloorPattern implements IPatternEnum {
    CORNER,
    CENTER,
    FLOOR,
    ALTERNATE_FLOOR;

    public FloorPattern valueOfIgnoreCase(String value) {
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String getName() {
        return name();
    }
}

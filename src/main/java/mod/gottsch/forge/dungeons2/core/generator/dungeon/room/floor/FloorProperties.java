package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

/**
 * @author Mark Gottschling on Mar 2, 2024
 *
 */
public class FloorProperties {

    public boolean border;
    public boolean paddedBorder;
    public boolean cornerGrates;

    public void setPaddedBorder(boolean border) {
        paddedBorder = border;
        if (border) setBorder(false);
    }

    public void setBorder(boolean border) {
        this.border = border;
        if (border) setPaddedBorder(false);
    }

    public void setCornerGrates(boolean grates) {
        this.cornerGrates = grates;
    }
}

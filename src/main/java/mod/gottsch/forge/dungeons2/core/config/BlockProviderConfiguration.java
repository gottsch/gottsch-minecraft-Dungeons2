package mod.gottsch.forge.dungeons2.core.config;

import java.util.List;

/**
 *
 * @author Mark Gottschling on Mar 5, 2023
 *
 */
public class BlockProviderConfiguration {
    List<Motif> motifs;
    // TODO add decay mappings

    List<BlockSet> blockSets;

    public static class BlockSet {
        private String id;
        private String motif;
        private String pattern;
        private List<PatternElement> elements;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMotif() {
            return motif;
        }

        public void setMotif(String motif) {
            this.motif = motif;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public List<PatternElement> getElements() {
            return elements;
        }

        public void setElements(List<PatternElement> elements) {
            this.elements = elements;
        }
    }

    /*
     *
     */
    public static class Motif {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Motif{" +
                    "id='" + id + '\'' +
                    '}';
        }
    }

    /*
     *
     */
    public static class PatternElement {
        private String id;
        private String block;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBlock() {
            return block;
        }

        public void setBlock(String block) {
            this.block = block;
        }
    }

    public List<Motif> getMotifs() {
        return motifs;
    }

    public void setMotifs(List<Motif> motifs) {
        this.motifs = motifs;
    }

    public List<BlockSet> getBlockSets() {
        return blockSets;
    }

    public void setBlockSets(List<BlockSet> blockSets) {
        this.blockSets = blockSets;
    }

    @Override
    public String toString() {
        return "BlockProviderConfiguration{" +
                "motifs=" + motifs +
                '}';
    }
}

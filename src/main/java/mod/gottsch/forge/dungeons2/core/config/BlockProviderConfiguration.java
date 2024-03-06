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

    /*
     *
     */
    public static class Motif {
        private String name;
        private List<Pattern> patterns;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Pattern> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<Pattern> patterns) {
            this.patterns = patterns;
        }

        @Override
        public String toString() {
            return "Motif{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    public static class Pattern {
        private String name;
        private List<PatternElement> elements;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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
    public static class PatternElement {
        private String name;
        private String block;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

    @Override
    public String toString() {
        return "BlockProviderConfiguration{" +
                "motifs=" + motifs +
                '}';
    }
}

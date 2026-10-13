package org.itmo.inds.golden;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The golden blocks that deliberate fixes changed, listed in {@value #FILE} (a test resource of this package): one
 * entry per fix, naming the block labels and input sets whose blocks may differ from the records.
 * <p>
 * Format: lines starting with {@code #} are comments; an entry is a line {@code <issue> <block labels> <input sets>},
 * the lists comma-separated, {@code *} for all input sets, e.g. {@code 17 Level,Level-clone G-k3-n1000}.
 */
final class GoldenDivergences {
    /**
     * Name of the divergence list.
     */
    static final String FILE = "golden-divergences.txt";

    /**
     * One fix: the blocks it may change.
     *
     * @param issue       the issue the fix closes
     * @param blockLabels labels of the blocks it may change
     * @param inputSets   input sets of the blocks it may change; empty for all
     */
    record Entry(int issue, Set<String> blockLabels, Set<String> inputSets) {
        /**
         * @param startLine a block start line {@code S <scenario> <block label> <input set> <params>}
         * @return whether this fix may change the block
         */
        boolean covers(final String startLine) {
            final String[] fields = startLine.split(" ", 5);
            return blockLabels.contains(fields[2]) && (inputSets.isEmpty() || inputSets.contains(fields[3]));
        }

        @Override
        public String toString() {
            return "#" + issue;
        }
    }

    private static List<Entry> entries;

    private GoldenDivergences() {
    }

    /**
     * @return the entries of {@value #FILE}, read once; empty if the file is not on the classpath
     * @throws IOException           if the file cannot be read
     * @throws IllegalStateException if an entry is malformed
     */
    static synchronized List<Entry> entries() throws IOException {
        if (entries == null) {
            final List<Entry> read = new ArrayList<>();
            final InputStream is = GoldenDivergences.class.getResourceAsStream(FILE);
            if (is != null) {
                try (final BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    for (String line = in.readLine(); line != null; line = in.readLine()) {
                        if (!line.isBlank() && !line.startsWith("#")) {
                            read.add(parse(line.trim()));
                        }
                    }
                }
            }
            entries = List.copyOf(read);
        }
        return entries;
    }

    private static Entry parse(final String line) {
        final String[] fields = line.split("\\s+");
        if (fields.length != 3) {
            throw new IllegalStateException(FILE + ": expected <issue> <block labels> <input sets>: " + line);
        }
        final Set<String> inputSets = fields[2].equals("*") ? Set.of() : Set.of(fields[2].split(","));
        return new Entry(Integer.parseInt(fields[0]), Set.of(fields[1].split(",")), inputSets);
    }
}

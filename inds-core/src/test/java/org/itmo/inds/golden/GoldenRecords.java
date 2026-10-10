package org.itmo.inds.golden;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * Splits golden records into blocks, both the golden files and the replayed lines (format: package documentation,
 * "Files"; line kinds: {@link RecordKind}). A block is keyed by its start line and holds the lines after it up to
 * and including its end line.
 */
final class GoldenRecords {
    /**
     * First line of every golden records file.
     */
    static final String HEADER = "# inds-golden-records v1";

    private GoldenRecords() {
    }

    /**
     * Reads the blocks with the given block labels from a golden records file; other blocks and comment lines are
     * skipped.
     *
     * @param gz          the gzipped file content; closed by this method
     * @param file        the file name, for error messages
     * @param blockLabels block labels to keep (see {@link Target#blockLabels()})
     * @return start line → block lines, in file order
     * @throws IOException if the content cannot be read or does not start with {@link #HEADER}
     */
    static Map<String, List<String>> read(final InputStream gz, final String file, final Set<String> blockLabels)
            throws IOException {
        try (final BufferedReader in = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(gz, 1 << 16), StandardCharsets.UTF_8))) {
            if (!HEADER.equals(in.readLine())) {
                throw new IOException("Unexpected header in " + file);
            }
            final Iterator<String> lines = in.lines().filter(line -> !RecordKind.COMMENT.isKindOf(line)).iterator();
            return split(lines, startLine -> blockLabels.contains(blockLabel(startLine)), file);
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Splits replayed record lines ({@link RecordBuffer#lines()}) into blocks.
     *
     * @param lines record lines, starting with a block start line
     * @return start line → block lines, in input order
     */
    static Map<String, List<String>> blocks(final List<String> lines) {
        return split(lines.iterator(), startLine -> true, "replayed records");
    }

    /**
     * @param startLine a block start line {@code S <scenario> <block label> <input set> <params>}
     * @return its block label
     */
    static String blockLabel(final String startLine) {
        return startLine.split(" ", 4)[2];
    }

    /**
     * @param lines  record lines without comments
     * @param keep   decides by its start line whether a block is kept
     * @param source where the lines come from, for error messages
     * @return start line → lines of the kept blocks, in input order
     * @throws IllegalStateException if a line comes before the first block start, or a kept block occurs twice
     */
    private static Map<String, List<String>> split(final Iterator<String> lines, final Predicate<String> keep,
                                                   final String source) {
        final Map<String, List<String>> blocks = new LinkedHashMap<>();
        List<String> current = null;
        boolean keepCurrent = false;
        while (lines.hasNext()) {
            final String line = lines.next();
            if (RecordKind.BLOCK_START.isKindOf(line)) {
                current = new ArrayList<>();
                keepCurrent = keep.test(line);
                if (keepCurrent && blocks.put(line, current) != null) {
                    throw new IllegalStateException("duplicate block in " + source + ": " + line);
                }
            } else if (current == null) {
                throw new IllegalStateException(source + ": line before the first block start: " + line);
            } else if (keepCurrent) {
                current.add(line);
            }
        }
        return blocks;
    }
}

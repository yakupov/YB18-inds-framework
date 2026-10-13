package org.itmo.inds.golden;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The golden-master comparison used by the golden tests of all modules (terms: package documentation): for a
 * {@link Target}, replays every input set on the ported implementation and requires the replayed blocks to equal the
 * target's blocks in the golden file, line by line, except the blocks of deliberate fixes ({@link GoldenDivergences}).
 * Example from {@link GoldenMasterTest}:
 * <pre>{@code
 * Stream.of(
 *         GoldenCheck.test(Target.DC1, Replays.incremental(JfbSorter::new)),
 *         GoldenCheck.test(Target.LEVEL, Replays.population(JfbyPopulation::new)),
 *         GoldenCheck.test(Target.TREAP, GoldenMasterTest::treap2d))
 * }</pre>
 * Other modules use it through the inds-core test-jar (same package name). Without the golden files on the classpath
 * the tests are skipped.
 */
final class GoldenCheck {
    /**
     * Replays a target's scenarios for one input set on the ported implementation.
     */
    @FunctionalInterface
    interface Replay {
        /**
         * Runs the target's scenarios on {@code input} and writes their blocks, as the legacy recorder did. Called
         * concurrently for different input sets: must not share mutable state between calls.
         *
         * @param target the target being replayed (its labels go into the block start lines)
         * @param input  the input set
         * @param out    receives the record lines
         */
        void replay(Target target, InputSet input, RecordBuffer out);
    }

    /**
     * Name of the golden input sets file, a test resource of this package.
     */
    static final String INPUTS = "inputs.txt.gz";

    private static final int MAX_REPORTED_DIFFERENCES = 10;

    private static List<InputSet> inputs;

    private GoldenCheck() {
    }

    /**
     * @param target the recorded target to compare with
     * @param replay replays the target's scenarios on the ported implementation
     * @return a JUnit test running {@link #check}, named after the legacy build and the target's labels, e.g.
     * {@code nds2018-r2019.2: [Level, Level-clone]}
     */
    static DynamicTest test(final Target target, final Replay replay) {
        return DynamicTest.dynamicTest(target.source().buildName() + ": " + target.blockLabels(),
                () -> check(target, replay));
    }

    /**
     * Compares the replayed blocks of {@code target} with its golden blocks. A block may differ only if an entry of
     * {@link GoldenDivergences} covers it (a deliberate fix), and each entry that names one of the target's block
     * labels must cover at least one differing block of the target (otherwise it is stale). Fails if the golden file
     * has no block of the target, or listing the first problems (start line, line position and record kind; golden
     * content is not printed). Skipped (JUnit assumption) if the golden files are not on the classpath.
     *
     * @param target the recorded target to compare with
     * @param replay replays the target's scenarios on the ported implementation
     * @throws IOException if a golden file cannot be read
     */
    static void check(final Target target, final Replay replay) throws IOException {
        final String file = target.source().recordsFile();
        Assumptions.assumeTrue(available(INPUTS) && available(file),
                "golden files not on the classpath: golden-master test skipped");
        final Map<String, List<String>> expected = goldenBlocks(target);
        assertFalse(expected.isEmpty(), () -> file + " has no blocks labelled " + target.blockLabels());
        final List<GoldenDivergences.Entry> divergences = GoldenDivergences.entries().stream()
                .filter(entry -> !Collections.disjoint(entry.blockLabels(), target.blockLabels())).toList();
        final List<String> problems = problems(expected, replayedBlocks(target, replay), divergences);
        if (!problems.isEmpty()) {
            final List<String> reported = problems.stream().limit(MAX_REPORTED_DIFFERENCES).toList();
            fail(problems.size() + " problems (" + file + " has " + expected.size() + " blocks of " + target
                    + "), first ones:\n  " + String.join("\n  ", reported));
        }
    }

    /**
     * @param name a golden file name, e.g. {@link #INPUTS} or {@code nds2018-r2019.2.txt.gz}
     * @return whether the file is on the classpath
     */
    static boolean available(final String name) {
        return GoldenCheck.class.getResource(name) != null;
    }

    /**
     * @return the golden input sets, read once and shared (read-only)
     * @throws IOException if {@link #INPUTS} cannot be read
     */
    static synchronized List<InputSet> inputs() throws IOException {
        if (inputs == null) {
            try (final InputStream is = open(INPUTS)) {
                inputs = InputsIO.read(is);
            }
        }
        return inputs;
    }

    /**
     * @return the target's golden blocks: start line → block lines
     */
    private static Map<String, List<String>> goldenBlocks(final Target target) throws IOException {
        final String file = target.source().recordsFile();
        try (final InputStream is = open(file)) {
            return GoldenRecords.read(is, file, target.blockLabels());
        }
    }

    /**
     * Replays all input sets. They are independent and the replays single-threaded, so input sets are replayed in
     * parallel; the blocks keep the input set order.
     *
     * @return the replayed blocks: start line → block lines
     */
    private static Map<String, List<String>> replayedBlocks(final Target target, final Replay replay)
            throws IOException {
        final List<List<String>> perInputSet = inputs().parallelStream().map(input -> {
            final RecordBuffer out = new RecordBuffer();
            replay.replay(target, input, out);
            return out.lines();
        }).toList();
        final List<String> all = new ArrayList<>();
        perInputSet.forEach(all::addAll);
        return GoldenRecords.blocks(all);
    }

    /**
     * @param expected    golden blocks
     * @param actual      replayed blocks
     * @param divergences the divergence entries that name block labels of the target
     * @return one line per block that is missing on either side, or differs without a divergence entry covering it,
     * {@code <start line>: <what differs>} (golden blocks first, in file order); then one line per stale entry
     */
    private static List<String> problems(final Map<String, List<String>> expected,
                                         final Map<String, List<String>> actual,
                                         final List<GoldenDivergences.Entry> divergences) {
        final Set<String> startLines = new LinkedHashSet<>(expected.keySet());
        startLines.addAll(actual.keySet());
        final List<String> found = new ArrayList<>();
        final Set<GoldenDivergences.Entry> used = new HashSet<>();
        for (final String startLine : startLines) {
            final Optional<String> difference = difference(expected.get(startLine), actual.get(startLine));
            if (difference.isEmpty()) {
                continue;
            }
            final List<GoldenDivergences.Entry> covering = divergences.stream()
                    .filter(entry -> entry.covers(startLine)).toList();
            if (covering.isEmpty() || !expected.containsKey(startLine) || !actual.containsKey(startLine)) {
                found.add(startLine + ": " + difference.get());
            } else {
                used.addAll(covering);
            }
        }
        divergences.stream().filter(entry -> !used.contains(entry)).forEach(entry -> found.add(
                entry + " in " + GoldenDivergences.FILE + ": no block of this target differs (stale entry)"));
        return found;
    }

    /**
     * @param expected lines of a golden block, null if the golden file has no such block
     * @param actual   lines of the replayed block, null if the replay produced no such block
     * @return what differs, or empty if the blocks are equal
     */
    private static Optional<String> difference(final List<String> expected, final List<String> actual) {
        if (expected == null) {
            return Optional.of("not in golden file");
        }
        if (actual == null) {
            return Optional.of("not replayed");
        }
        final int common = Math.min(expected.size(), actual.size());
        for (int i = 0; i < common; ++i) {
            final String expectedLine = expected.get(i);
            final String actualLine = actual.get(i);
            if (!expectedLine.equals(actualLine)) {
                final String kind = RecordKind.of(expectedLine).map(RecordKind::name).orElse("dump line");
                // the replayed end line names the exception, if one was thrown
                final String replayedEnd = RecordKind.BLOCK_END.isKindOf(actualLine) ? ", replay: " + actualLine : "";
                return Optional.of("line " + (i + 1) + " (" + kind + ") differs" + replayedEnd);
            }
        }
        return expected.size() == actual.size()
                ? Optional.empty()
                : Optional.of("length " + actual.size() + " instead of " + expected.size() + " lines");
    }

    /**
     * @return the gzipped content of a golden file from the classpath; the caller closes it
     * @throws IOException if the file is not on the classpath
     */
    private static InputStream open(final String name) throws IOException {
        final InputStream is = GoldenCheck.class.getResourceAsStream(name);
        if (is == null) {
            throw new IOException("golden file not on the classpath: " + name);
        }
        return is;
    }
}

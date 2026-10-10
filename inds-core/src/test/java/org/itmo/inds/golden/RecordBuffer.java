package org.itmo.inds.golden;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the record lines of a replay in memory, in the golden records format (package documentation, "Files";
 * line kinds: {@link RecordKind}): one string per line, without the file header. The only writer of the format, so
 * the text of the fields ({@code r=}, {@code exc ... at ...}, ...) is spelled out here and nowhere else. Also defines
 * after which steps a scenario writes step records and full dumps, as the legacy recorder did.
 */
final class RecordBuffer {
    /**
     * Written where a record has no value (no parameters, no return value).
     */
    static final String NONE = "-";

    private final List<String> lines = new ArrayList<>();

    /**
     * Starts a block: {@code S <scenario> <block label> <input set> <params>}.
     *
     * @param target   the recorded target
     * @param scenario the scenario; the block label is {@link Target#blockLabel(Scenario)}
     * @param input    the input set
     * @param params   scenario parameters, e.g. {@code expected=50}; {@link #NONE} if there are none
     */
    void begin(final Target target, final Scenario scenario, final InputSet input, final String params) {
        lines.add(RecordKind.BLOCK_START.line(
                scenario.id() + " " + target.blockLabel(scenario) + " " + input.id() + " " + params));
    }

    /**
     * Records the state after a step: {@code s <step> r=<return> n=<size> h=<digest>}.
     *
     * @param step        step number, 1-based (0 for one-shot scenarios)
     * @param returnValue the operation's return value as text
     * @param size        the size reported after the step
     * @param digest      {@link StateEncoder#digest()} of the state after the step
     */
    void step(final int step, final String returnValue, final int size, final String digest) {
        lines.add(RecordKind.STEP.line(step + " r=" + returnValue + " n=" + size + " h=" + digest));
    }

    /**
     * Records the state after a step of an operation without return value.
     *
     * @param step   step number, 1-based (0 for one-shot scenarios)
     * @param size   the size after the step
     * @param digest {@link StateEncoder#digest()} of the state after the step
     */
    void step(final int step, final int size, final String digest) {
        step(step, NONE, size, digest);
    }

    /**
     * Records a full dump of the state after a step: {@code D <step>}, the dump lines, {@code /D}.
     *
     * @param step    step number
     * @param encoder the encoder of the same state, created with dump
     */
    void dump(final int step, final StateEncoder encoder) {
        final String text = encoder.dumpText();
        lines.add(RecordKind.DUMP_START.line(Integer.toString(step)));
        if (!text.isEmpty()) {
            lines.addAll(List.of(text.substring(0, text.length() - (text.endsWith("\n") ? 1 : 0)).split("\n", -1)));
        }
        lines.add(RecordKind.DUMP_END.line(""));
    }

    /**
     * Records an additional result: {@code i <key> <value>}.
     *
     * @param key   what is recorded, e.g. {@code probe-ranks}
     * @param value the result as text without line breaks
     */
    void info(final String key, final String value) {
        lines.add(RecordKind.INFO.line(key + " " + value));
    }

    /**
     * Ends a block that ran to completion: {@code E ok}.
     */
    void ok() {
        lines.add(RecordKind.BLOCK_END.line("ok"));
    }

    /**
     * Ends a block whose operation threw: {@code E exc <exception class> at <step>}.
     *
     * @param thrown the exception thrown by the code under test
     * @param step   the step at which it was thrown (0 for one-shot scenarios and setup)
     */
    void failed(final Throwable thrown, final int step) {
        lines.add(RecordKind.BLOCK_END.line("exc " + thrown.getClass().getName() + " at " + step));
    }

    /**
     * Ends a block that was not run because the input set has fewer than two levels:
     * {@code E skipped fewer-than-two-levels}.
     */
    void skippedFewerThanTwoLevels() {
        lines.add(RecordKind.BLOCK_END.line("skipped fewer-than-two-levels"));
    }

    /**
     * @return the lines recorded so far (live list)
     */
    List<String> lines() {
        return lines;
    }

    /**
     * @param step step number, 1-based
     * @param n    number of steps of the scenario
     * @return whether a step record is written after {@code step}: every step up to n = 1000, otherwise every 100th
     * step and the last one
     */
    static boolean recordAt(final int step, final int n) {
        return n <= 1000 || step % 100 == 0 || step == n;
    }

    /**
     * @param step step number, 1-based
     * @param n    number of steps of the scenario
     * @return whether a full dump is written after {@code step}: every step up to n = 50, at n / 2 and n up to
     * n = 1000, never above
     */
    static boolean dumpAt(final int step, final int n) {
        if (n <= 50) {
            return true;
        }
        if (n > 1000) {
            return false;
        }
        return step == n / 2 || step == n;
    }
}

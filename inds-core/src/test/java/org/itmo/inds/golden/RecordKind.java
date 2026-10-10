package org.itmo.inds.golden;

import java.util.Optional;

/**
 * The kinds of lines in golden records, each identified by the prefix it starts with (format: package documentation,
 * "Files"). The single owner of these prefixes: {@link RecordBuffer} writes with them, {@link GoldenRecords} splits
 * with them. Lines inside a dump (between {@link #DUMP_START} and {@link #DUMP_END}) have no kind of their own; their
 * format belongs to {@link StateEncoder}.
 */
enum RecordKind {
    /**
     * {@code S <scenario> <block label> <input set> <params>}.
     */
    BLOCK_START("S "),
    /**
     * {@code s <step> r=<return> n=<size> h=<digest>}.
     */
    STEP("s "),
    /**
     * {@code D <step>}, followed by the dump lines.
     */
    DUMP_START("D "),
    /**
     * {@code /D}.
     */
    DUMP_END("/D"),
    /**
     * {@code i <key> <value>}.
     */
    INFO("i "),
    /**
     * {@code E <status>}.
     */
    BLOCK_END("E "),
    /**
     * {@code # ...}, written by the recorder (metadata); not part of any block.
     */
    COMMENT("#");

    private final String prefix;

    RecordKind(final String prefix) {
        this.prefix = prefix;
    }

    /**
     * @param content the rest of the line, after the prefix
     * @return a line of this kind
     */
    String line(final String content) {
        return prefix + content;
    }

    /**
     * @param line a record line
     * @return whether the line is of this kind
     */
    boolean isKindOf(final String line) {
        return line.startsWith(prefix);
    }

    /**
     * @param line a record line
     * @return its kind; empty for a line inside a dump
     */
    static Optional<RecordKind> of(final String line) {
        for (final RecordKind kind : values()) {
            if (kind.isKindOf(line)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}

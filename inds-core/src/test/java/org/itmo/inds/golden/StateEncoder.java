package org.itmo.inds.golden;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical encoding of an observable state (a population snapshot, a sorter result), the same as the legacy
 * recorder's: every call adds one element to a SHA-256 digest and, optionally, a line to a text dump. Binary layout
 * (big-endian): a tag byte, then the fields as ints / longs; doubles as {@code doubleToRawLongBits}. Changing the
 * encoding changes every digest, so the golden records would no longer match.
 * <p>
 * Typical use, for a population with its levels:
 * <pre>{@code
 * StateEncoder e = new StateEncoder(withDump);
 * e.population(size, levels.size());
 * for each level i: e.level(i, members.size()); for each member: e.member(payload, objectives, crowdingDistance);
 * out.step(step, Integer.toString(returnValue), size, e.digest());
 * if (withDump) out.dump(step, e);
 * }</pre>
 */
public final class StateEncoder {
    private final MessageDigest sha256;
    private final StringBuilder dump;
    private final ByteBuffer scratch = ByteBuffer.allocate(16);

    /**
     * @param withDump whether to build the text dump ({@link #dumpText()}) besides the digest
     */
    public StateEncoder(boolean withDump) {
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        dump = withDump ? new StringBuilder() : null;
    }

    private void tag(int kind) {
        sha256.update((byte) kind);
    }

    private void putInt(int value) {
        scratch.clear();
        scratch.putInt(value);
        sha256.update(scratch.array(), 0, 4);
    }

    private void putLong(long value) {
        scratch.clear();
        scratch.putLong(value);
        sha256.update(scratch.array(), 0, 8);
    }

    /**
     * Adds the header of a state.
     *
     * @param size   the size the population reports (sorter results: number of elements)
     * @param levels number of levels; -1 for sorter results, which have none
     */
    public void population(int size, int levels) {
        tag('P');
        putInt(size);
        putInt(levels);
        if (dump != null) {
            dump.append("p ").append(size).append(' ').append(levels).append('\n');
        }
    }

    /**
     * Adds the header of a level; its members follow.
     *
     * @param index the level's rank, 0-based
     * @param count number of members
     */
    public void level(int index, int count) {
        tag('L');
        putInt(index);
        putInt(count);
        if (dump != null) {
            dump.append("l ").append(index).append(' ').append(count).append('\n');
        }
    }

    /**
     * Adds a population member.
     *
     * @param payload          the member's index in the input set, or -1 if unknown
     * @param objectives       the member's objectives
     * @param crowdingDistance the member's crowding distance
     */
    public void member(int payload, double[] objectives, double crowdingDistance) {
        tag('M');
        putInt(payload);
        putInt(objectives.length);
        for (final double objective : objectives) {
            putLong(Double.doubleToRawLongBits(objective));
        }
        putLong(Double.doubleToRawLongBits(crowdingDistance));
        if (dump != null) {
            dump.append("m ").append(payload);
            for (final double objective : objectives) {
                dump.append(' ').append(InputsIO.hex(objective));
            }
            dump.append(" cd=").append(InputsIO.hex(crowdingDistance)).append('\n');
        }
    }

    /**
     * Adds a point without payload or crowding distance (for the 2D treap, which keeps raw arrays).
     *
     * @param objectives the point
     */
    public void point(double[] objectives) {
        tag('X');
        putInt(objectives.length);
        for (final double objective : objectives) {
            putLong(Double.doubleToRawLongBits(objective));
        }
        if (dump != null) {
            dump.append("x");
            for (final double objective : objectives) {
                dump.append(' ').append(InputsIO.hex(objective));
            }
            dump.append('\n');
        }
    }

    /**
     * Adds one entry of a sorter result; entries are added in result order.
     *
     * @param payload the element's index in the input set
     * @param rank    its rank
     */
    public void ranked(int payload, int rank) {
        tag('R');
        putInt(payload);
        putInt(rank);
        if (dump != null) {
            dump.append("r ").append(payload).append(' ').append(rank).append('\n');
        }
    }

    /**
     * Finishes the digest; call once, after the last element.
     *
     * @return the first 64 bits of the SHA-256 digest as 16 hex characters (enough to detect any regression)
     */
    public String digest() {
        return HexFormat.of().formatHex(sha256.digest(), 0, 8);
    }

    /**
     * @return the text dump, one line per element; empty if the encoder was created without dump
     */
    public String dumpText() {
        return dump == null ? "" : dump.toString();
    }
}

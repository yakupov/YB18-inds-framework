package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The content of one treap node: the points of the population with the same coordinates {@code (x1, x2)} (the first
 * two objectives, minimized), stored once each by {@code equals}. The coordinates are copied into the bucket, so the
 * treap never reads the points' objective arrays. Buckets are equal ({@code equals}, {@code hashCode}) iff their
 * coordinates are, so the population finds the bucket of a point in a hash map. A bucket without points is a search key
 * (see {@link TwoDimTreap#getMinP()}).
 *
 * <p>Not thread-safe; the members can be added while the bucket is in a treap (the treap orders by coordinates only).
 *
 * @param <T> payload type of the points
 */
public final class TwoDimBucket<T> {
    private final double x1, x2;
    /** The first member; {@code null} for a search key. */
    private final Individual<T> first;
    /**
     * All members in the order of insertion, created with the second one: most buckets hold one point and need no
     * list. Lookup by {@code equals} scans it.
     */
    private List<Individual<T>> all;

    /**
     * @param first the first member; at least two objectives
     */
    TwoDimBucket(Individual<T> first) {
        this.x1 = first.getObjectives()[0];
        this.x2 = first.getObjectives()[1];
        this.first = first;
    }

    /**
     * A search key without members.
     */
    TwoDimBucket(double x1, double x2) {
        this.x1 = x1;
        this.x2 = x2;
        this.first = null;
    }

    public double getX1() {
        return x1;
    }

    public double getX2() {
        return x2;
    }

    /**
     * @return the members in the order of insertion (a read-only view)
     */
    public List<Individual<T>> getMembers() {
        if (all != null) {
            return Collections.unmodifiableList(all);
        }
        return first == null ? Collections.emptyList() : Collections.singletonList(first);
    }

    /**
     * @return whether a member equals {@code individual}
     */
    boolean contains(Individual<T> individual) {
        return all != null ? all.contains(individual) : individual.equals(first);
    }

    /**
     * Adds a point with the same coordinates unless an equal point is a member already.
     *
     * @return whether the point was added
     */
    boolean add(Individual<T> individual) {
        if (contains(individual)) {
            return false;
        }
        if (all == null) {
            all = new ArrayList<>(2);
            all.add(first);
        }
        all.add(individual);
        return true;
    }

    public int compareX1(TwoDimBucket<?> o) {
        return Double.compare(x1, o.x1);
    }

    public int compareX2(TwoDimBucket<?> o) {
        return Double.compare(x2, o.x2);
    }

    /**
     * @return the sum of the comparisons of both coordinates (not normalized): negative iff this bucket's points
     * dominate {@code o}'s, positive iff {@code o}'s dominate these, zero if the coordinates are equal or neither
     * dominates
     */
    public int compareDom(TwoDimBucket<?> o) {
        int xc = Double.compare(x1, o.x1);
        int yc = Double.compare(x2, o.x2);
        return xc + yc;
    }

    /**
     * Buckets are equal iff their coordinates are ({@link Double#compare}), whatever their members.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TwoDimBucket<?> that)) return false;
        return Double.compare(x1, that.x1) == 0 && Double.compare(x2, that.x2) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(x1) + Double.hashCode(x2);
    }

    @Override
    public String toString() {
        return String.format("ind %s %s", x1, x2);
    }
}

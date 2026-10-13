package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The content of a treap node: the points with the same coordinates {@code (x1, x2)} (the first two objectives), each
 * once by {@code equals}. A bucket without points is a search key.
 *
 * @param <T> payload type of the points
 */
public final class TwoDimBucket<T> {
    private final double x1, x2;
    /** The member while there is at most one; then {@code all} is {@code null}. */
    private Individual<T> first;
    /** Created with the second member, which most buckets never get. */
    private List<Individual<T>> all;

    TwoDimBucket(Individual<T> first) {
        this(first.getObjectives()[0], first.getObjectives()[1]);
        this.first = first;
    }

    /**
     * A search key.
     */
    TwoDimBucket(double x1, double x2) {
        this.x1 = x1;
        this.x2 = x2;
    }

    public double getX1() {
        return x1;
    }

    public double getX2() {
        return x2;
    }

    /**
     * @return the members in the order of insertion (read-only)
     */
    public List<Individual<T>> getMembers() {
        if (all != null) {
            return Collections.unmodifiableList(all);
        }
        return first == null ? Collections.emptyList() : Collections.singletonList(first);
    }

    public int size() {
        return all != null ? all.size() : first == null ? 0 : 1;
    }

    boolean contains(Individual<T> individual) {
        return all != null ? all.contains(individual) : individual.equals(first);
    }

    /**
     * @return whether the point was added: false if an equal point is a member
     */
    boolean add(Individual<T> individual) {
        if (contains(individual)) {
            return false;
        }
        if (all == null) {
            all = new ArrayList<>(2);
            if (first != null) {
                all.add(first);
                first = null;
            }
        }
        all.add(individual);
        return true;
    }

    /**
     * @return the index of {@code member} (the same object) in {@link #getMembers()}, or -1
     */
    int indexOf(Individual<T> member) {
        if (all == null) {
            return member == first ? 0 : -1;
        }
        for (int i = 0; i < all.size(); ++i) {
            if (all.get(i) == member) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return whether the bucket is empty now
     */
    boolean remove(Individual<T> member) {
        if (all == null) {
            first = null;
            return true;
        }
        all.remove(indexOf(member));
        return all.isEmpty();
    }

    TwoDimBucket<T> copy() {
        final TwoDimBucket<T> copy = new TwoDimBucket<>(x1, x2);
        copy.first = first;
        if (all != null) {
            copy.all = new ArrayList<>(all);
        }
        return copy;
    }

    public int compareX1(TwoDimBucket<?> o) {
        return Double.compare(x1, o.x1);
    }

    public int compareX2(TwoDimBucket<?> o) {
        return Double.compare(x2, o.x2);
    }

    /**
     * @return negative iff this bucket's points dominate {@code o}'s, positive iff {@code o}'s dominate these, zero
     * otherwise (not normalized)
     */
    public int compareDom(TwoDimBucket<?> o) {
        final int xc = Double.compare(x1, o.x1);
        final int yc = Double.compare(x2, o.x2);
        return xc + yc;
    }

    @Override
    public String toString() {
        return String.format("ind %s %s", x1, x2);
    }
}

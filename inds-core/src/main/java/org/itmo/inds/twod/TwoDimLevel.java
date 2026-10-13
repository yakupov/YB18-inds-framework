package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.List;

/**
 * A level of a {@link TwoDimTreapPopulation} snapshot: the members in increasing {@code x1} with their crowding
 * distances. Read-only.
 *
 * @param <T> payload type of the points
 */
@Immutable
final class TwoDimLevel<T> implements NonDominationLevel<T> {
    private final List<Individual<T>> members;

    /**
     * @param members not copied: must not change afterwards
     */
    TwoDimLevel(List<Individual<T>> members) {
        this.members = Collections.unmodifiableList(members);
    }

    @Override
    public List<Individual<T>> getMembers() {
        return members;
    }

    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public MemberAdditionResult addMembers(@Nonnull List<Individual<T>> addends) {
        throw new UnsupportedOperationException("A level of a 2D population snapshot is read-only");
    }

    /**
     * Dominance by the first two objectives. Of the members with {@code x1} not larger than the point's, the last one
     * has the smallest {@code x2}: the only candidate.
     */
    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull Individual<T> point) {
        final double[] objectives = point.getObjectives();
        int low = 0;
        int high = members.size() - 1;
        int candidate = -1;
        while (low <= high) {
            final int middle = (low + high) >>> 1;
            if (Double.compare(members.get(middle).getObjectives()[0], objectives[0]) <= 0) {
                candidate = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (candidate < 0) {
            return false;
        }
        final double[] member = members.get(candidate).getObjectives();
        return Double.compare(member[0], objectives[0]) + Double.compare(member[1], objectives[1]) < 0;
    }

    @Override
    public TwoDimLevel<T> copy() {
        return this;
    }

    @Override
    public String toString() {
        return "TwoDimLevel{members=" + members + '}';
    }
}

package org.itmo.inds.stress;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.oracle.Oracle;
import org.itmo.inds.oracle.PopulationInvariants;
import org.itmo.inds.oracle.ReferencePopulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What must hold for a population after concurrent insertion. Each check returns a description of the first violation,
 * or empty. The payload of an individual is the index of its point.
 */
final class StressInvariants {
    private StressInvariants() {
    }

    /**
     * Without eviction: every distinct point is present once, in its oracle rank; the levels are non-dominated;
     * {@code size()} and the snapshot agree with the number of distinct points.
     */
    static Optional<String> afterInsertion(final ManagedPopulation<Integer> population, final double[][] points) {
        final PopulationInvariants invariants = new PopulationInvariants(points, Oracle.ranks(points));
        final PopulationSnapshot<Integer> snapshot = population.getSnapshot();
        final List<NonDominationLevel<Integer>> levels = snapshot.getLevels();
        return invariants.levelsViolation(levels)
                .or(() -> dominatedMemberViolation(levels))
                .or(() -> {
                    final int distinct = invariants.distinctCount();
                    return population.size() == distinct && snapshot.getSize() == distinct
                            ? Optional.empty()
                            : Optional.of("size() = " + population.size() + ", snapshot size = " + snapshot.getSize()
                                    + ", distinct points = " + distinct);
                });
    }

    /**
     * With eviction, which removes points that depend on the interleaving: the remaining points are inserted ones,
     * intact and present once; levels are non-empty and non-dominated; the level of each point is its rank among the
     * remaining points; {@code size()}, the snapshot and the members agree; the size is at least the expected size (or
     * the number of distinct points, if less) and at most the expected size times {@code maxSizeFactor}.
     *
     * @param inserted all inserted points
     */
    static Optional<String> afterInsertionWithEviction(final ManagedPopulation<Integer> population,
                                                       final double[][] inserted, final long expectedSize,
                                                       final double maxSizeFactor) {
        final PopulationSnapshot<Integer> snapshot = population.getSnapshot();
        final List<NonDominationLevel<Integer>> levels = snapshot.getLevels();
        return remainingPointsViolation(levels, inserted)
                .or(() -> dominatedMemberViolation(levels))
                .or(() -> rankViolation(levels))
                .or(() -> sizeViolation(population.size(), snapshot.getSize(), levels, inserted, expectedSize,
                        maxSizeFactor));
    }

    /**
     * Checks with a naive dominance test, independent of the code under test and of the oracle.
     */
    private static Optional<String> dominatedMemberViolation(final List<NonDominationLevel<Integer>> levels) {
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            final List<Individual<Integer>> members = levels.get(levelIndex).getMembers();
            for (final Individual<Integer> a : members) {
                for (final Individual<Integer> b : members) {
                    if (a != b && ReferencePopulation.dominates(a.getObjectives(), b.getObjectives())) {
                        return Optional.of("level " + levelIndex + " not non-dominated");
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> remainingPointsViolation(final List<NonDominationLevel<Integer>> levels,
                                                             final double[][] inserted) {
        final boolean[] seen = new boolean[inserted.length];
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            final List<Individual<Integer>> members = levels.get(levelIndex).getMembers();
            if (members.isEmpty()) {
                return Optional.of("level " + levelIndex + " is empty");
            }
            for (final Individual<Integer> member : members) {
                final Integer id = member.getPayload();
                if (id == null || id < 0 || id >= inserted.length) {
                    return Optional.of("level " + levelIndex + " holds a point that was not inserted: payload " + id);
                }
                if (!Arrays.equals(member.getObjectives(), inserted[id])) {
                    return Optional.of("point " + id + " has changed objectives");
                }
                if (seen[id]) {
                    return Optional.of("point " + id + " is present more than once");
                }
                seen[id] = true;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> rankViolation(final List<NonDominationLevel<Integer>> levels) {
        final List<double[]> remaining = new ArrayList<>();
        final List<Integer> levelIndices = new ArrayList<>();
        final List<Integer> ids = new ArrayList<>();
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            for (final Individual<Integer> member : levels.get(levelIndex).getMembers()) {
                remaining.add(member.getObjectives());
                levelIndices.add(levelIndex);
                ids.add(member.getPayload());
            }
        }
        final int[] ranks = Oracle.ranks(remaining.toArray(new double[0][]));
        for (int i = 0; i < ranks.length; ++i) {
            if (ranks[i] != levelIndices.get(i)) {
                return Optional.of("point " + ids.get(i) + " is in level " + levelIndices.get(i)
                        + ", its rank among the remaining points is " + ranks[i]);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> sizeViolation(final int size, final int snapshotSize,
                                                  final List<NonDominationLevel<Integer>> levels,
                                                  final double[][] inserted, final long expectedSize,
                                                  final double maxSizeFactor) {
        final int memberCount = levels.stream().mapToInt(level -> level.getMembers().size()).sum();
        if (size != snapshotSize || size != memberCount) {
            return Optional.of("size() = " + size + ", snapshot size = " + snapshotSize + ", members = " + memberCount);
        }
        final int distinct = (int) Arrays.stream(inserted).map(Arrays::toString).distinct().count();
        final long minSize = Math.min(expectedSize, distinct);
        if (size < minSize || size > expectedSize * maxSizeFactor) {
            return Optional.of("size " + size + " outside [" + minSize + ", " + expectedSize * maxSizeFactor
                    + "] (expected size " + expectedSize + ", " + distinct + " distinct points)");
        }
        return Optional.empty();
    }
}

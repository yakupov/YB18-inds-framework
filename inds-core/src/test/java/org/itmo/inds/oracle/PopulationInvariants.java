package org.itmo.inds.oracle;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The expected state of a population after a set of points was inserted, each as an individual whose payload is its
 * index in that set. Populations keep a set of points (a point equal to a present one by objective vector is not
 * added), so the invariants are about distinct points: each is present exactly once, carrying the payload of one of
 * its copies and its unchanged objectives, in the level given by its {@link Oracle} rank, with a crowding distance
 * that is not NaN.
 */
public final class PopulationInvariants {
    private final double[][] points;
    private final int[] oracleRanks;
    private final int[] distinctIndexOf;
    private final int distinctCount;

    /**
     * @param points      the inserted points, by payload; not modified
     * @param oracleRanks {@link Oracle#ranks} of {@code points}
     */
    public PopulationInvariants(final double[][] points, final int[] oracleRanks) {
        this.points = points;
        this.oracleRanks = oracleRanks;
        final Map<List<Double>, Integer> distinctIndices = new HashMap<>();
        distinctIndexOf = new int[points.length];
        for (int i = 0; i < points.length; ++i) {
            final List<Double> objectives = Arrays.stream(points[i]).boxed().toList();
            distinctIndexOf[i] = distinctIndices.computeIfAbsent(objectives, key -> distinctIndices.size());
        }
        distinctCount = distinctIndices.size();
    }

    /**
     * @return the number of distinct points (by objective vector), i.e. the expected population size
     */
    public int distinctCount() {
        return distinctCount;
    }

    /**
     * @param levels the levels of the population, rank 0 first
     * @return a description of the first violated invariant, or empty if the levels hold every distinct point once,
     * in its oracle rank, with a crowding distance that is not NaN
     */
    public Optional<String> levelsViolation(final List<? extends NonDominationLevel<Integer>> levels) {
        final int[] copiesSeen = new int[distinctCount];
        int memberCount = 0;
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            final List<Individual<Integer>> members = levels.get(levelIndex).getMembers();
            if (members.isEmpty()) {
                return Optional.of("level " + levelIndex + " is empty");
            }
            for (final Individual<Integer> member : members) {
                final Optional<String> violation = memberViolation(member, levelIndex, copiesSeen);
                if (violation.isPresent()) {
                    return violation;
                }
                ++memberCount;
            }
        }
        if (memberCount != distinctCount) {
            return Optional.of(memberCount + " of " + distinctCount + " distinct points present");
        }
        return Optional.empty();
    }

    private Optional<String> memberViolation(final Individual<Integer> member, final int levelIndex,
                                             final int[] copiesSeen) {
        final Integer id = member.getPayload();
        if (id == null || id < 0 || id >= points.length) {
            return Optional.of("unknown member " + id + " in level " + levelIndex);
        }
        if (++copiesSeen[distinctIndexOf[id]] > 1) {
            return Optional.of("point " + id + " present twice");
        }
        if (!Arrays.equals(points[id], member.getObjectives())) {
            return Optional.of("objectives of point " + id + " changed");
        }
        if (oracleRanks[id] != levelIndex) {
            return Optional.of("point " + id + " in level " + levelIndex + ", oracle rank " + oracleRanks[id]);
        }
        if (Double.isNaN(member.getCrowdingDistance())) {
            return Optional.of("point " + id + " has crowding distance NaN");
        }
        return Optional.empty();
    }
}

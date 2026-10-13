package org.itmo.inds.oracle;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.PopulationSnapshot;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Oracle cross-check of a population, single-threaded: inserts the points one by one (unlimited expected size, so
 * nothing is evicted) and compares with {@link Oracle} after every insertion (N &lt;= 100) or after every 50th and the
 * last one. The value returned by {@code addIndividual} must be the oracle rank of the inserted point at that moment;
 * the levels must satisfy the {@link PopulationInvariants}. {@link #checkClone} checks a population and its clone.
 */
public final class PopulationOracle {
    private PopulationOracle() {
    }

    /**
     * @param factory creates the empty population under test
     * @param points  the points to insert, in order; not modified
     * @return a description of the first mismatch, or empty
     */
    public static Optional<String> check(final Supplier<ManagedPopulation<Integer>> factory, final double[][] points) {
        final ManagedPopulation<Integer> population = factory.get();
        final int n = points.length;
        for (int i = 0; i < n; ++i) {
            final int returnedRank = population.addIndividual(new FitnessAndCdIndividual<>(points[i].clone(), i));
            final int step = i + 1;
            final double[][] inserted = Arrays.copyOf(points, step);
            final int[] oracleRanks = Oracle.ranks(inserted);
            if (returnedRank != oracleRanks[i]) {
                return Optional.of("step " + step + ": returned rank " + returnedRank + ", oracle " + oracleRanks[i]);
            }
            if (isCheckpoint(step, n)) {
                final Optional<String> violation = new PopulationInvariants(inserted, oracleRanks)
                        .levelsViolation(population.getSnapshot().getLevels());
                if (violation.isPresent()) {
                    return violation.map(description -> "step " + step + ": " + description);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Oracle check of {@code clone()}, single-threaded: inserts the first third of the points, clones the population,
     * then inserts the second third into the original only and the rest into the copy only. The copy must be of the
     * original's class, and each of the two must then hold exactly its own points: the {@link PopulationInvariants},
     * and {@code size()} and the snapshot size equal to the number of distinct points.
     *
     * @param factory creates the empty population under test, with unlimited expected size
     * @param points  the points, at least three; not modified
     * @return a description of the first mismatch, or empty
     */
    public static Optional<String> checkClone(final Supplier<ManagedPopulation<Integer>> factory,
                                              final double[][] points) {
        final int n = points.length;
        final int first = n / 3;
        final int second = 2 * n / 3;
        final ManagedPopulation<Integer> original = factory.get();
        for (int i = 0; i < first; ++i) {
            original.addIndividual(new FitnessAndCdIndividual<>(points[i].clone(), i));
        }
        final ManagedPopulation<Integer> copy = original.clone();
        if (copy.getClass() != original.getClass()) {
            return Optional.of("the copy is a " + copy.getClass().getSimpleName());
        }
        for (int i = first; i < second; ++i) {
            original.addIndividual(new FitnessAndCdIndividual<>(points[i].clone(), i));
        }
        final double[][] copyPoints = new double[first + n - second][];
        for (int i = 0; i < copyPoints.length; ++i) {
            copyPoints[i] = i < first ? points[i] : points[second + i - first];
        }
        for (int i = first; i < copyPoints.length; ++i) {
            copy.addIndividual(new FitnessAndCdIndividual<>(copyPoints[i].clone(), i));
        }
        return stateViolation(original, Arrays.copyOf(points, second)).map(description -> "original: " + description)
                .or(() -> stateViolation(copy, copyPoints).map(description -> "copy: " + description));
    }

    /**
     * @return a description of the first violation, or empty if the population holds exactly {@code inserted} (by
     * payload): the {@link PopulationInvariants}, and {@code size()} and the snapshot size equal to the number of
     * distinct points
     */
    private static Optional<String> stateViolation(final ManagedPopulation<Integer> population,
                                                   final double[][] inserted) {
        final PopulationInvariants invariants = new PopulationInvariants(inserted, Oracle.ranks(inserted));
        final PopulationSnapshot<Integer> snapshot = population.getSnapshot();
        final int distinct = invariants.distinctCount();
        return invariants.levelsViolation(snapshot.getLevels())
                .or(() -> population.size() == distinct && snapshot.getSize() == distinct
                        ? Optional.empty()
                        : Optional.of("size() = " + population.size() + ", snapshot size = " + snapshot.getSize()
                        + ", distinct points = " + distinct));
    }

    /**
     * @return whether the whole state is compared after insertion {@code step} (1-based) of {@code n}
     */
    public static boolean isCheckpoint(final int step, final int n) {
        return n <= 100 || step % 50 == 0 || step == n;
    }
}

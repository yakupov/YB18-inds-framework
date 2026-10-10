package org.itmo.inds.oracle;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.ManagedPopulation;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Oracle cross-check of a population, single-threaded: inserts the points one by one (unlimited expected size, so
 * nothing is evicted) and compares with {@link Oracle} after every insertion (N &lt;= 100) or after every 50th and the
 * last one. The value returned by {@code addIndividual} must be the oracle rank of the inserted point at that moment;
 * the levels must satisfy the {@link PopulationInvariants}.
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
     * @return whether the whole state is compared after insertion {@code step} (1-based) of {@code n}
     */
    public static boolean isCheckpoint(final int step, final int n) {
        return n <= 100 || step % 50 == 0 || step == n;
    }
}

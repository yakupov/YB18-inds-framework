package org.itmo.inds.stress;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.stress.ConcurrentInsertion.Failure;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Concurrency stress tests: several threads insert a seeded point set into one population, then
 * {@link StressInvariants} must hold. Two scenarios: without eviction, and with small expected sizes (eviction).
 * <p>
 * Repetitions per case: {@code -Dinds.stress.repetitions} (default 100, 10 000 with {@code -Pstress}) and
 * {@code -Dinds.stress.evictionRepetitions} (default 20, 1 000 with {@code -Pstress}). A failure names the repetition,
 * whose points and split are reproducible from it, the interleaving is not. {@code -Dinds.stress.populations=A,B}
 * limits the populations. Subclasses name the populations ({@link #populations()}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractConcurrentInsertionStressTest {
    private static final int REPETITIONS = Integer.getInteger("inds.stress.repetitions", 100);
    private static final int EVICTION_REPETITIONS = Integer.getInteger("inds.stress.evictionRepetitions", 20);
    private static final List<String> ONLY_POPULATIONS = Arrays.stream(
            System.getProperty("inds.stress.populations", "").split(",")).filter(name -> !name.isEmpty()).toList();
    private static final int[] THREADS = {2, 6, 12, 24};
    private static final long[] EXPECTED_SIZES = {5, 50};
    private static final int MAX_REPORTED_FAILURES = 10;

    /**
     * @param name          name in test reports and in {@code -Dinds.stress.populations}
     * @param factory       creates an empty population without a size limit
     * @param boundedFactory creates an empty population with the given expected size
     * @param maxSizeFactor after an insertion without contention the population holds at most
     *                      {@code expected size * maxSizeFactor} points: 1 if every insertion above the expected size
     *                      evicts, the deletion threshold if eviction works in batches
     */
    public record PopulationUnderTest(String name, Supplier<ManagedPopulation<Integer>> factory,
                                      LongFunction<ManagedPopulation<Integer>> boundedFactory,
                                      double maxSizeFactor) {
        @Override
        @Nonnull
        public String toString() {
            return name;
        }
    }

    /**
     * One repetition of a scenario.
     */
    private interface Repetition {
        Optional<Failure> run(ExecutorService pool, int repetition) throws InterruptedException;
    }

    protected abstract List<PopulationUnderTest> populations();

    Stream<Arguments> cases() {
        return selectedPopulations().flatMap(population ->
                Arrays.stream(THREADS).mapToObj(threads -> Arguments.of(population, threads)));
    }

    Stream<Arguments> evictionCases() {
        return selectedPopulations().flatMap(population -> Arrays.stream(THREADS).boxed().flatMap(threads ->
                Arrays.stream(EXPECTED_SIZES).mapToObj(expectedSize -> Arguments.of(population, threads, expectedSize))));
    }

    private Stream<PopulationUnderTest> selectedPopulations() {
        return populations().stream()
                .filter(population -> ONLY_POPULATIONS.isEmpty() || ONLY_POPULATIONS.contains(population.name()));
    }

    @ParameterizedTest(name = "{0}, {1} threads")
    @MethodSource("cases")
    void concurrentInsertionKeepsInvariants(final PopulationUnderTest population, final int threads)
            throws InterruptedException {
        repeat(population + " with " + threads + " threads", threads, REPETITIONS, (pool, repetition) -> {
            final double[][] points = ConcurrentInsertion.points(repetition);
            final ManagedPopulation<Integer> instance = population.factory().get();
            return ConcurrentInsertion.insert(pool, instance, points, threads, repetition)
                    .or(() -> violation(StressInvariants.afterInsertion(instance, points)));
        });
    }

    /**
     * After the threads finish, one more point, worse than all others in the first objective, is inserted without
     * contention: a batch-evicting population may have skipped an eviction pass while another one was running.
     */
    @ParameterizedTest(name = "{0}, {1} threads, expected size {2}")
    @MethodSource("evictionCases")
    void concurrentInsertionWithEvictionKeepsInvariants(final PopulationUnderTest population, final int threads,
                                                        final long expectedSize) throws InterruptedException {
        repeat(population + " with " + threads + " threads, expected size " + expectedSize, threads,
                EVICTION_REPETITIONS, (pool, repetition) -> {
                    final double[][] points = ConcurrentInsertion.points(repetition);
                    final ManagedPopulation<Integer> instance = population.boundedFactory().apply(expectedSize);
                    return ConcurrentInsertion.insert(pool, instance, points, threads, repetition)
                            .or(() -> insertOneMore(instance, points, repetition)
                                    .or(() -> violation(StressInvariants.afterInsertionWithEviction(instance,
                                            withOneMore(points, repetition), expectedSize, population.maxSizeFactor()))));
                });
    }

    private static void repeat(final String label, final int threads, final int repetitions,
                               final Repetition scenario) throws InterruptedException {
        final ExecutorService pool = ConcurrentInsertion.newPool(threads);
        final List<String> reported = new ArrayList<>();
        int failedCount = 0;
        try {
            for (int repetition = 0; repetition < repetitions; ++repetition) {
                final Optional<Failure> failure = scenario.run(pool, repetition);
                if (failure.isEmpty()) {
                    continue;
                }
                if (++failedCount <= MAX_REPORTED_FAILURES) {
                    reported.add("repetition " + repetition + ": " + failure.get().description());
                }
                if (failure.get().hang()) {
                    break;
                }
            }
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                reported.add("workers still running 1 min after shutdownNow");
            }
        }
        if (failedCount > 0) {
            fail(label + ": " + failedCount + " of " + repetitions
                    + " repetitions failed, first ones:\n  " + String.join("\n  ", reported));
        }
    }

    private static Optional<Failure> violation(final Optional<String> description) {
        return description.map(text -> new Failure(text, false));
    }

    private static Optional<Failure> insertOneMore(final ManagedPopulation<Integer> population,
                                                   final double[][] points, final int repetition) {
        try {
            population.addIndividual(new FitnessAndCdIndividual<>(lastPoint(points, repetition).clone(), points.length));
            return Optional.empty();
        } catch (final RuntimeException e) {
            return Optional.of(new Failure("insertion after the workers threw " + e, false));
        }
    }

    private static double[][] withOneMore(final double[][] points, final int repetition) {
        final double[][] inserted = Arrays.copyOf(points, points.length + 1);
        inserted[points.length] = lastPoint(points, repetition);
        return inserted;
    }

    /**
     * @return a new point: the first objective is above all generated ones, which are below 1
     */
    private static double[] lastPoint(final double[][] points, final int repetition) {
        final Random random = new Random(~(long) repetition * 31);
        final double[] point = new double[points[0].length];
        point[0] = 1.5;
        for (int j = 1; j < point.length; ++j) {
            point[j] = random.nextDouble();
        }
        return point;
    }
}

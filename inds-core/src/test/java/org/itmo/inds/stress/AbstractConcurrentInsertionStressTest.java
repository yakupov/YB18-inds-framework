package org.itmo.inds.stress;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.oracle.Oracle;
import org.itmo.inds.oracle.PopulationInvariants;
import org.itmo.inds.oracle.ReferencePopulation;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Concurrency stress tests: several threads add disjoint parts of a seeded shuffle of a seeded point set to one
 * population.
 * <ul>
 * <li>Without eviction (unlimited expected size) the population afterwards must satisfy the
 * {@link PopulationInvariants} (every distinct point present exactly once, in its oracle rank), have non-dominated
 * levels and a consistent size.
 * <li>With eviction (small expected sizes) the final ranks are not determined by the points, so only invariants that
 * hold in every interleaving are checked: no hang or exception, every remaining point is an inserted one and present
 * once, levels are non-empty and non-dominated, {@code size()} agrees with the snapshot, and the size is at least the
 * expected size and, after one more insertion without contention, at most the expected size times the population's
 * {@link PopulationUnderTest#maxSizeFactor() factor}. (Fine skips an eviction pass while another one runs, so its
 * size may exceed the bound until the next insertion.)
 * </ul>
 * <p>
 * Repetitions per case: {@code -Dinds.stress.repetitions} (default 100; 10 000 with {@code -Pstress}); points per
 * repetition: {@code -Dinds.stress.points} (default 500). A failure names the repetition, whose data and split are
 * reproducible from it (the interleaving is not). Further knobs for diagnosis: {@code inds.stress.populations} (comma
 * list of population names), {@code inds.stress.duplicates} / {@code inds.stress.ties} (fractions, default 0.1),
 * {@code inds.stress.dims} (default 2,3,4,5), {@code inds.stress.expectedSizes} (eviction test, default 5,50),
 * {@code inds.stress.timeoutSeconds} (hang detection, default 60).
 * <p>
 * Subclasses name the populations under test ({@link #populations()}): {@code ConcurrentInsertionStressTest}
 * (inds-core) and {@code EnluConcurrentInsertionStressTest} (inds-baselines, via inds-core's test-jar).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractConcurrentInsertionStressTest {
    private static final int REPETITIONS = Integer.getInteger("inds.stress.repetitions", 100);
    private static final int EVICTION_REPETITIONS = Integer.getInteger("inds.stress.evictionRepetitions", 20);
    private static final int POINTS = Integer.getInteger("inds.stress.points", 500);
    private static final int TIMEOUT_SECONDS = Integer.getInteger("inds.stress.timeoutSeconds", 60);
    private static final List<String> ONLY_POPULATIONS = Arrays.stream(
            System.getProperty("inds.stress.populations", "").split(",")).filter(name -> !name.isEmpty()).toList();
    private static final double DUPLICATES = Double.parseDouble(System.getProperty("inds.stress.duplicates", "0.1"));
    private static final double TIES = Double.parseDouble(System.getProperty("inds.stress.ties", "0.1"));
    private static final int[] DIMS = Arrays.stream(System.getProperty("inds.stress.dims", "2,3,4,5").split(","))
            .mapToInt(Integer::parseInt).toArray();
    private static final long[] EXPECTED_SIZES = Arrays.stream(
            System.getProperty("inds.stress.expectedSizes", "5,50").split(",")).mapToLong(Long::parseLong).toArray();
    private static final int[] THREADS = {2, 6, 12, 24};
    private static final int MAX_REPORTED_FAILURES = 10;

    /**
     * A population under test.
     *
     * @param name          name in test reports and in {@code -Dinds.stress.populations}
     * @param factory       creates an empty population with unlimited expected size
     * @param boundedFactory creates an empty population with the given expected size
     * @param maxSizeFactor the population holds at most {@code expected size * maxSizeFactor} points after an
     *                      insertion without contention: 1 if every insertion above the expected size evicts, the
     *                      deletion threshold if eviction works in batches
     * @param nonDominatedWithEviction whether the levels are checked for non-domination after concurrent insertion
     *                      with eviction; false for populations known to violate it
     */
    public record PopulationUnderTest(String name, Supplier<ManagedPopulation<Integer>> factory,
                                      LongFunction<ManagedPopulation<Integer>> boundedFactory,
                                      double maxSizeFactor, boolean nonDominatedWithEviction) {
        @Override
        @Nonnull
        public String toString() {
            return name;
        }
    }

    /**
     * Why a repetition failed.
     */
    private enum FailureKind {
        INVARIANT,
        EXCEPTION,
        /**
         * Workers did not finish in time; they keep blocking the thread pool.
         */
        HANG
    }

    private record Failure(FailureKind kind, String description) {
    }

    /**
     * One repetition of a scenario on a pool of workers.
     */
    private interface Repetition {
        Optional<Failure> run(ExecutorService pool, int repetition) throws InterruptedException;
    }

    /**
     * @return the populations under test
     */
    protected abstract List<PopulationUnderTest> populations();

    Stream<Arguments> cases() {
        return populations().stream()
                .filter(population -> ONLY_POPULATIONS.isEmpty() || ONLY_POPULATIONS.contains(population.name()))
                .flatMap(population -> Arrays.stream(THREADS).mapToObj(threads -> Arguments.of(population, threads)));
    }

    Stream<Arguments> evictionCases() {
        return cases().flatMap(arguments -> Arrays.stream(EXPECTED_SIZES)
                .mapToObj(expectedSize -> Arguments.of(arguments.get()[0], arguments.get()[1], expectedSize)));
    }

    @ParameterizedTest(name = "{0}, {1} threads")
    @MethodSource("cases")
    void concurrentInsertionKeepsInvariants(final PopulationUnderTest population, final int threads)
            throws InterruptedException {
        repeat(population + " with " + threads + " threads", threads, REPETITIONS,
                (pool, repetition) -> run(pool, population, threads, repetition));
    }

    @ParameterizedTest(name = "{0}, {1} threads, expected size {2}")
    @MethodSource("evictionCases")
    void concurrentInsertionWithEvictionKeepsInvariants(final PopulationUnderTest population, final int threads,
                                                        final long expectedSize) throws InterruptedException {
        repeat(population + " with " + threads + " threads, expected size " + expectedSize, threads,
                EVICTION_REPETITIONS,
                (pool, repetition) -> runWithEviction(pool, population, threads, expectedSize, repetition));
    }

    /**
     * Runs the repetitions on a pool of {@code threads} workers and fails the test if any failed.
     */
    private static void repeat(final String label, final int threads, final int repetitions,
                               final Repetition scenario) throws InterruptedException {
        // Daemon workers: a worker that never finishes (a hang) must not keep the test JVM alive.
        final ExecutorService pool = Executors.newFixedThreadPool(threads, task -> {
            final Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
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
                if (failure.get().kind() == FailureKind.HANG) {
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

    /**
     * Seeded points: number of objectives from the dimension list by repetition, some exact duplicates of earlier
     * points, some ties in the first objective.
     */
    static double[][] points(final int repetition) {
        final Random random = new Random(0x5EEDL * 1_000_003L + repetition);
        final int k = DIMS[repetition % DIMS.length];
        final double[][] points = new double[POINTS][];
        for (int i = 0; i < POINTS; ++i) {
            if (i > 0 && random.nextDouble() < DUPLICATES) {
                points[i] = points[random.nextInt(i)].clone();
                continue;
            }
            points[i] = new double[k];
            for (int j = 0; j < k; ++j) {
                points[i][j] = random.nextDouble();
            }
            if (random.nextDouble() < TIES) {
                points[i][0] = Math.floor(points[i][0] * 8) / 8;
            }
        }
        return points;
    }

    /**
     * @return a seeded random permutation of {@code 0 .. n - 1} (Fisher-Yates)
     */
    private static int[] shuffle(final int n, final int repetition) {
        final Random random = new Random(~(long) repetition);
        final int[] order = new int[n];
        for (int i = 0; i < n; ++i) {
            order[i] = i;
        }
        for (int i = n - 1; i > 0; --i) {
            final int j = random.nextInt(i + 1);
            final int swapped = order[i];
            order[i] = order[j];
            order[j] = swapped;
        }
        return order;
    }

    /**
     * One repetition: concurrent insertion, then the invariants.
     *
     * @return the failure, or empty
     */
    private static Optional<Failure> run(final ExecutorService pool, final PopulationUnderTest populationUnderTest,
                                         final int threads, final int repetition) throws InterruptedException {
        final double[][] points = points(repetition);
        final ManagedPopulation<Integer> population = populationUnderTest.factory().get();
        return insertConcurrently(pool, population, points, threads, repetition)
                .or(() -> invariantViolation(population, points)
                        .map(description -> new Failure(FailureKind.INVARIANT, description)));
    }

    /**
     * One repetition with eviction: concurrent insertion into a population with a small expected size, then the
     * invariants that hold in every interleaving (see the class comment). Before they are checked, one more point
     * (worse in the first objective than all others) is inserted without contention, so that a skipped eviction pass
     * of a batch-evicting population is made up.
     *
     * @return the failure, or empty
     */
    private static Optional<Failure> runWithEviction(final ExecutorService pool,
                                                     final PopulationUnderTest populationUnderTest,
                                                     final int threads, final long expectedSize,
                                                     final int repetition) throws InterruptedException {
        final double[][] points = points(repetition);
        final ManagedPopulation<Integer> population = populationUnderTest.boundedFactory().apply(expectedSize);
        return insertConcurrently(pool, population, points, threads, repetition)
                .or(() -> insertLastPointAndCheck(population, points, expectedSize, populationUnderTest,
                        repetition));
    }

    private static Optional<Failure> insertLastPointAndCheck(final ManagedPopulation<Integer> population,
                                                             final double[][] points, final long expectedSize,
                                                             final PopulationUnderTest populationUnderTest,
                                                             final int repetition) {
        final double[][] inserted = Arrays.copyOf(points, points.length + 1);
        inserted[points.length] = lastPoint(points, repetition);
        try {
            population.addIndividual(new FitnessAndCdIndividual<>(inserted[points.length].clone(), points.length));
        } catch (final RuntimeException e) {
            return Optional.of(new Failure(FailureKind.EXCEPTION, "insertion after the workers threw " + e));
        }
        return evictionViolation(population, inserted, expectedSize, populationUnderTest)
                .map(description -> new Failure(FailureKind.INVARIANT, description));
    }

    /**
     * @return the failure of a worker (with the shape of the data added), or empty if all workers finished normally
     */
    private static Optional<Failure> insertConcurrently(final ExecutorService pool,
                                                        final ManagedPopulation<Integer> population,
                                                        final double[][] points, final int threads,
                                                        final int repetition) throws InterruptedException {
        final List<Future<?>> workers = startWorkers(pool, population, points, shuffle(points.length, repetition),
                threads);
        return awaitWorkers(workers).map(failure -> new Failure(failure.kind(), failure.description() + " (k = "
                + points[0].length + ", " + population.getSnapshot().getLevels().size() + " levels)"));
    }

    /**
     * @return a point with the first objective above all generated ones (they are below 1), so it is a new point
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

    /**
     * Starts {@code threads} workers that wait for each other, then each insert one contiguous part of {@code order}.
     */
    private static List<Future<?>> startWorkers(final ExecutorService pool, final ManagedPopulation<Integer> population,
                                                final double[][] points, final int[] order, final int threads) {
        final int n = order.length;
        final CyclicBarrier start = new CyclicBarrier(threads);
        final List<Future<?>> workers = new ArrayList<>();
        for (int worker = 0; worker < threads; ++worker) {
            final int from = (int) ((long) n * worker / threads);
            final int to = (int) ((long) n * (worker + 1) / threads);
            workers.add(pool.submit(() -> {
                start.await();
                for (int i = from; i < to; ++i) {
                    final int id = order[i];
                    population.addIndividual(new FitnessAndCdIndividual<>(points[id].clone(), id));
                }
                return null;
            }));
        }
        return workers;
    }

    /**
     * Waits for all workers, together at most {@link #TIMEOUT_SECONDS}.
     *
     * @return the first worker exception or a hang, or empty if all workers finished normally
     */
    private static Optional<Failure> awaitWorkers(final List<Future<?>> workers) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        for (final Future<?> worker : workers) {
            try {
                worker.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (final ExecutionException e) {
                return Optional.of(new Failure(FailureKind.EXCEPTION, "worker threw " + e.getCause()));
            } catch (final TimeoutException e) {
                final long running = workers.stream().filter(future -> !future.isDone()).count();
                return Optional.of(new Failure(FailureKind.HANG, running + " of " + workers.size()
                        + " workers not finished after " + TIMEOUT_SECONDS + " s"));
            }
        }
        return Optional.empty();
    }

    /**
     * @return a description of the first violated invariant of the final state, or empty
     */
    private static Optional<String> invariantViolation(final ManagedPopulation<Integer> population,
                                                       final double[][] points) {
        final PopulationInvariants invariants = new PopulationInvariants(points, Oracle.ranks(points));
        final PopulationSnapshot<Integer> snapshot = population.getSnapshot();
        return invariants.levelsViolation(snapshot.getLevels())
                .or(() -> dominatedMemberViolation(snapshot.getLevels()))
                .or(() -> sizeViolation(population.size(), snapshot.getSize(), invariants.distinctCount()));
    }

    /**
     * Checks that no member of a level dominates another one, with a naive dominance test independent of the code
     * under test and of the oracle.
     */
    private static Optional<String> dominatedMemberViolation(final List<NonDominationLevel<Integer>> levels) {
        for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
            final List<Individual<Integer>> members = levels.get(levelIndex).getMembers();
            for (int a = 0; a < members.size(); ++a) {
                for (int b = 0; b < members.size(); ++b) {
                    if (a != b && ReferencePopulation.dominates(members.get(a).getObjectives(),
                            members.get(b).getObjectives())) {
                        return Optional.of("level " + levelIndex + " not non-dominated");
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> sizeViolation(final int size, final int snapshotSize, final int distinctCount) {
        return size == distinctCount && snapshotSize == distinctCount
                ? Optional.empty()
                : Optional.of("size() = " + size + ", snapshot size = " + snapshotSize + ", distinct points = "
                        + distinctCount);
    }

    /**
     * Invariants of a population after concurrent insertion with eviction that hold in every interleaving.
     *
     * @param inserted the inserted points; the payload of an individual is its index in this array
     * @return a description of the first violated invariant, or empty
     */
    private static Optional<String> evictionViolation(final ManagedPopulation<Integer> population,
                                                      final double[][] inserted, final long expectedSize,
                                                      final PopulationUnderTest populationUnderTest) {
        final PopulationSnapshot<Integer> snapshot = population.getSnapshot();
        final List<NonDominationLevel<Integer>> levels = snapshot.getLevels();
        final int[] memberCount = new int[1];
        return remainingPointsViolation(levels, inserted, memberCount)
                .or(() -> populationUnderTest.nonDominatedWithEviction()
                        ? dominatedMemberViolation(levels) : Optional.empty())
                .or(() -> evictionSizeViolation(population.size(), snapshot.getSize(), memberCount[0],
                        distinctCount(inserted), expectedSize, populationUnderTest.maxSizeFactor()));
    }

    /**
     * Checks that every level has members and every member is one of the inserted points, intact and present once;
     * stores the number of members in {@code memberCount[0]}.
     */
    private static Optional<String> remainingPointsViolation(final List<NonDominationLevel<Integer>> levels,
                                                             final double[][] inserted, final int[] memberCount) {
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
                ++memberCount[0];
            }
        }
        return Optional.empty();
    }

    private static int distinctCount(final double[][] points) {
        return (int) Arrays.stream(points).map(Arrays::toString).distinct().count();
    }

    private static Optional<String> evictionSizeViolation(final int size, final int snapshotSize,
                                                          final int memberCount, final int distinctCount,
                                                          final long expectedSize, final double maxSizeFactor) {
        final long minSize = Math.min(expectedSize, distinctCount);
        if (size != snapshotSize || size != memberCount) {
            return Optional.of("size() = " + size + ", snapshot size = " + snapshotSize + ", members = "
                    + memberCount);
        }
        if (size < minSize || size > expectedSize * maxSizeFactor) {
            return Optional.of("size " + size + " outside [" + minSize + ", " + expectedSize * maxSizeFactor
                    + "] (expected size " + expectedSize + ", " + distinctCount + " distinct points)");
        }
        return Optional.empty();
    }
}

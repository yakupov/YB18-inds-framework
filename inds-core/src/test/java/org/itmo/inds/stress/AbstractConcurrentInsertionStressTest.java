package org.itmo.inds.stress;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.oracle.Oracle;
import org.itmo.inds.oracle.PopulationInvariants;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Concurrency stress test: several threads add disjoint parts of a seeded shuffle of a seeded point set to one
 * population (no eviction); afterwards the population must satisfy the {@link PopulationInvariants} (every distinct
 * point present exactly once, in its oracle rank), have non-dominated levels and a consistent size.
 * <p>
 * Repetitions per case: {@code -Dinds.stress.repetitions} (default 100; 10 000 with {@code -Pstress}); points per
 * repetition: {@code -Dinds.stress.points} (default 500). A failure names the repetition, whose data and split are
 * reproducible from it (the interleaving is not). Further knobs for diagnosis: {@code inds.stress.populations} (comma
 * list of population names), {@code inds.stress.duplicates} / {@code inds.stress.ties} (fractions, default 0.1),
 * {@code inds.stress.dims} (default 2,3,4,5), {@code inds.stress.timeoutSeconds} (hang detection, default 60).
 * <p>
 * Subclasses name the populations under test ({@link #populations()}): {@code ConcurrentInsertionStressTest}
 * (inds-core) and {@code EnluConcurrentInsertionStressTest} (inds-baselines, via inds-core's test-jar).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractConcurrentInsertionStressTest {
    private static final int REPETITIONS = Integer.getInteger("inds.stress.repetitions", 100);
    private static final int POINTS = Integer.getInteger("inds.stress.points", 500);
    private static final int TIMEOUT_SECONDS = Integer.getInteger("inds.stress.timeoutSeconds", 60);
    private static final List<String> ONLY_POPULATIONS = Arrays.stream(
            System.getProperty("inds.stress.populations", "").split(",")).filter(name -> !name.isEmpty()).toList();
    private static final double DUPLICATES = Double.parseDouble(System.getProperty("inds.stress.duplicates", "0.1"));
    private static final double TIES = Double.parseDouble(System.getProperty("inds.stress.ties", "0.1"));
    private static final int[] DIMS = Arrays.stream(System.getProperty("inds.stress.dims", "2,3,4,5").split(","))
            .mapToInt(Integer::parseInt).toArray();
    private static final int[] THREADS = {2, 6, 12, 24};
    private static final int MAX_REPORTED_FAILURES = 10;

    /**
     * A population under test.
     *
     * @param name    name in test reports and in {@code -Dinds.stress.populations}
     * @param factory creates an empty population with unlimited expected size
     */
    public record PopulationUnderTest(String name, Supplier<ManagedPopulation<Integer>> factory) {
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
     * @return the populations under test
     */
    protected abstract List<PopulationUnderTest> populations();

    Stream<Arguments> cases() {
        return populations().stream()
                .filter(population -> ONLY_POPULATIONS.isEmpty() || ONLY_POPULATIONS.contains(population.name()))
                .flatMap(population -> Arrays.stream(THREADS).mapToObj(threads -> Arguments.of(population, threads)));
    }

    @ParameterizedTest(name = "{0}, {1} threads")
    @MethodSource("cases")
    void concurrentInsertionKeepsInvariants(final PopulationUnderTest population, final int threads)
            throws InterruptedException {
        // Daemon workers: a worker that never finishes (a hang) must not keep the test JVM alive.
        final ExecutorService pool = Executors.newFixedThreadPool(threads, task -> {
            final Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        final List<String> reported = new ArrayList<>();
        int failedCount = 0;
        try {
            for (int repetition = 0; repetition < REPETITIONS; ++repetition) {
                final Optional<Failure> failure = run(pool, population, threads, repetition);
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
            fail(population + " with " + threads + " threads: " + failedCount + " of " + REPETITIONS
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
        final List<Future<?>> workers = startWorkers(pool, population, points, shuffle(points.length, repetition),
                threads);
        final Optional<Failure> workerFailure = awaitWorkers(workers);
        if (workerFailure.isPresent()) {
            return workerFailure.map(failure -> new Failure(failure.kind(), failure.description() + " (k = "
                    + points[0].length + ", " + population.getSnapshot().getLevels().size() + " levels)"));
        }
        return invariantViolation(population, points)
                .map(description -> new Failure(FailureKind.INVARIANT, description));
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
                    if (a != b && dominates(members.get(a).getObjectives(), members.get(b).getObjectives())) {
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
     * Pareto dominance, minimization: {@code a} is nowhere worse than {@code b} and somewhere better.
     */
    private static boolean dominates(final double[] a, final double[] b) {
        boolean strict = false;
        for (int i = 0; i < a.length; ++i) {
            if (a[i] > b[i]) {
                return false;
            }
            if (a[i] < b[i]) {
                strict = true;
            }
        }
        return strict;
    }
}

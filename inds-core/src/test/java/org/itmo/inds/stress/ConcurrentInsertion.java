package org.itmo.inds.stress;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.ManagedPopulation;

import java.util.ArrayList;
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

/**
 * Inserts a seeded point set into a population from several threads at once. The payload of an individual is the index
 * of its point.
 */
final class ConcurrentInsertion {
    private static final int POINTS = 500;
    private static final int[] DIMS = {2, 3, 4, 5};
    private static final double DUPLICATES = 0.1;
    private static final double TIES = 0.1;
    private static final int TIMEOUT_SECONDS = 60;

    /**
     * @param hang whether workers did not finish in time: they keep running, so the pool is not usable any more
     */
    record Failure(String description, boolean hang) {
    }

    private ConcurrentInsertion() {
    }

    /**
     * @return a pool whose threads do not keep the JVM alive, so that a hung worker cannot block the exit
     */
    static ExecutorService newPool(final int threads) {
        return Executors.newFixedThreadPool(threads, task -> {
            final Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Points of a repetition: 2 to 5 objectives (by repetition), 10 % exact duplicates of earlier points, 10 % ties in
     * the first objective. All objectives are below 1.
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
     * Starts {@code threads} workers together; each inserts one contiguous part of a seeded shuffle of the points.
     *
     * @return the first exception of a worker, or a hang; empty if all workers finished
     */
    static Optional<Failure> insert(final ExecutorService pool, final ManagedPopulation<Integer> population,
                                    final double[][] points, final int threads, final int repetition)
            throws InterruptedException {
        final int[] order = shuffle(points.length, repetition);
        final CyclicBarrier start = new CyclicBarrier(threads);
        final List<Future<?>> workers = new ArrayList<>();
        for (int worker = 0; worker < threads; ++worker) {
            final int from = (int) ((long) order.length * worker / threads);
            final int to = (int) ((long) order.length * (worker + 1) / threads);
            workers.add(pool.submit(() -> {
                start.await();
                for (int i = from; i < to; ++i) {
                    population.addIndividual(new FitnessAndCdIndividual<>(points[order[i]].clone(), order[i]));
                }
                return null;
            }));
        }
        return await(workers).map(failure -> new Failure(failure.description() + " (k = " + points[0].length + ", "
                + population.getSnapshot().getLevels().size() + " levels)", failure.hang()));
    }

    private static Optional<Failure> await(final List<Future<?>> workers) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        for (final Future<?> worker : workers) {
            try {
                worker.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (final ExecutionException e) {
                return Optional.of(new Failure("worker threw " + e.getCause(), false));
            } catch (final TimeoutException e) {
                final long running = workers.stream().filter(future -> !future.isDone()).count();
                return Optional.of(new Failure(running + " of " + workers.size() + " workers not finished after "
                        + TIMEOUT_SECONDS + " s", true));
            }
        }
        return Optional.empty();
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
}

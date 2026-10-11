package org.itmo.inds.benchmarks;

import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.FitnessAndCdIndividual;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

abstract class ConcurrentInsertionDriver {
    protected abstract ConcurrentInsertionData getPreparedTestData();

    protected abstract int nThreads();

    int sortUsingJFBY() {
        final ConcurrentInsertionData testData = getPreparedTestData();
        final ManagedPopulation population = testData.getJfbyPopulation();
        int rs = 0;
        for (double[] doubles : testData.getAddends()) {
            rs = population.addIndividual(new FitnessAndCdIndividual<>(doubles, null));
        }
        return rs;
    }

    int sortUsingCJFBY() {
        return sortUsingConcurrentJFBY(getPreparedTestData().getCjfbyPopulation());
    }

    int sortUsingCJFBYAlt() {
        return sortUsingConcurrentJFBY(getPreparedTestData().getCjfbyPopulationAlt());
    }

    int sortUsingSyncJFBY() {
        return sortUsingConcurrentJFBY(getPreparedTestData().getLevelLockJFBYPopulation());
    }

    int sortUsingTsJFBY() {
        return sortUsingConcurrentJFBY(getPreparedTestData().getTsJfbyPopulation());
    }

    private int sortUsingConcurrentJFBY(final ManagedPopulation population) {
        final ExecutorService es = Executors.newFixedThreadPool(nThreads());
        try {
            final ConcurrentInsertionData testData = getPreparedTestData();
            final int nThreads = nThreads();
            final CountDownLatch latch = new CountDownLatch(nThreads);
            final AtomicInteger atomicInteger = new AtomicInteger(0);
            for (int i = 0; i < nThreads; ++i) {
                final int threadId = i;
                es.submit(() -> {
                    int res = 0;
                    try {
                        for (double[] doubles : testData.getConcurrentAddends().get(threadId)) {
                            res += population.addIndividual(new FitnessAndCdIndividual<>(doubles, null));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                        atomicInteger.set(res);
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return atomicInteger.get();
        } finally {
            es.shutdownNow();
        }
    }

}

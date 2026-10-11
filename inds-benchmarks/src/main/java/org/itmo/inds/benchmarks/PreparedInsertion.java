package org.itmo.inds.benchmarks;

/**
 * A measured operation with its state. Benchmarks call {@link #reset()} before every measured invocation (not
 * measured), then {@link #run()} (measured). Not thread-safe: JMH calls both from one thread.
 */
interface PreparedInsertion {
    /**
     * Restores the state that {@link #run()} changes, e.g. builds a fresh population.
     */
    void reset();

    /**
     * @return a value derived from the result (e.g. the rank of the inserted point), so that the work is not
     * eliminated as dead code
     */
    int run();
}

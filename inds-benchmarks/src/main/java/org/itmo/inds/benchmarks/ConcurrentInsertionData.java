package org.itmo.inds.benchmarks;

import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.population.JfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;

import java.util.List;
import java.util.Map;

public class ConcurrentInsertionData {
    private final JfbyPopulation jfbyPopulation;
    private final CoarseLockJfbyPopulation tsJfbyPopulation;
    private final CasJfbyPopulation cjfbyPopulation;
    private final CasJfbyPopulation cjfbyPopulationAlt;
    private final ManagedPopulation levelLockJFBYPopulation;
    private final List<double[]> addends;
    private final Map<Integer, List<double[]>> concurrentAddends;

    /**
     * Test data
     *
     * @param jfbyPopulation          For JFBY
     * @param tsJfbyPopulation        For synchronized JFBY
     * @param cjfbyPopulation         For CJFBY
     * @param cjfbyPopulationAlt      For CJFBY, another point insertion algorithm
     * @param levelLockJFBYPopulation For LevelLock JFBY
     * @param addends                 Points to add, in the given order
     * @param concurrentAddends       Points to add, split into thread-specific tasks
     */
    public ConcurrentInsertionData(JfbyPopulation jfbyPopulation,
                    CoarseLockJfbyPopulation tsJfbyPopulation, CasJfbyPopulation cjfbyPopulation,
                    CasJfbyPopulation cjfbyPopulationAlt,
                    ManagedPopulation levelLockJFBYPopulation, List<double[]> addends,
                    Map<Integer, List<double[]>> concurrentAddends) {
        this.jfbyPopulation = jfbyPopulation;
        this.tsJfbyPopulation = tsJfbyPopulation;
        this.cjfbyPopulation = cjfbyPopulation;
        this.cjfbyPopulationAlt = cjfbyPopulationAlt;
        this.levelLockJFBYPopulation = levelLockJFBYPopulation;
        this.addends = addends;
        this.concurrentAddends = concurrentAddends;
    }

    public JfbyPopulation getJfbyPopulation() {
        return jfbyPopulation;
    }

    public CoarseLockJfbyPopulation getTsJfbyPopulation() {
        return tsJfbyPopulation;
    }

    public CasJfbyPopulation getCjfbyPopulation() {
        return cjfbyPopulation;
    }

    public CasJfbyPopulation getCjfbyPopulationAlt() {
        return cjfbyPopulationAlt;
    }

    public ManagedPopulation getLevelLockJFBYPopulation() {
        return levelLockJFBYPopulation;
    }

    public List<double[]> getAddends() {
        return addends;
    }

    public Map<Integer, List<double[]>> getConcurrentAddends() {
        return concurrentAddends;
    }
}

package org.itmo.inds.golden;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A recorded legacy algorithm: the name the legacy recorder gave it, the legacy build it was recorded with and the
 * scenarios recorded for it. A target is identified by build and recorded name together: {@code Fine} exists in two
 * builds. Its blocks carry a block label derived from the recorded name ({@link #blockLabel(Scenario)}).
 */
enum Target {
    /**
     * DC: offline sorting ({@code JfbSorter#performNds}).
     */
    DC(Source.NDS2018_R2019_2, "DC", Scenario.DC_SORT),
    /**
     * DC1: incremental insertion ({@code JfbSorter#performIncrementalNds}).
     */
    DC1(Source.NDS2018_R2019_2, "DC1", Scenario.DC1_INSERT),
    /**
     * DC2: incremental insertion with linear sweeps ({@code IncrementalJfbSorter}).
     */
    DC2(Source.NDS2018_R2019_2, "DC2", Scenario.DC2_INSERT),
    /**
     * {@code addRankedMembers} of {@code JfbSorter}.
     */
    ADD_RANKED_MEMBERS_DC1(Source.NDS2018_R2019_2, "addRankedMembers-DC1", Scenario.ADD_RANKED_MEMBERS),
    /**
     * {@code addRankedMembers} of {@code IncrementalJfbSorter}.
     */
    ADD_RANKED_MEMBERS_DC2(Source.NDS2018_R2019_2, "addRankedMembers-DC2", Scenario.ADD_RANKED_MEMBERS),
    /**
     * Level ({@code JfbyPopulation}).
     */
    LEVEL(Source.NDS2018_R2019_2, "Level", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * Coarse ({@code CoarseLockJfbyPopulation}).
     */
    COARSE(Source.NDS2018_R2019_2, "Coarse", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * Fine, 2019 revision with retry from a rank hint ({@code FineLockJfbyPopulationRankHint}).
     */
    FINE_RANK_HINT(Source.NDS2018_R2019_2, "Fine", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * The ENLU population ({@code EnluPopulation}, module inds-baselines).
     */
    ENLU(Source.NDS2018_R2019_2, "ENLU", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * Fine, thesis version ({@code FineLockJfbyPopulation}).
     */
    FINE(Source.NDS2018_R2018_9_1_CD1, "Fine", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * CAS inserting by full sortings ({@code CasJfbyPopulation}).
     */
    CAS(Source.NDS2018_R2018_9_1_CD1, "CAS", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * CAS inserting one point at a time, the variant used in the thesis experiments ({@code CasJfbyPopulation}).
     */
    CAS_ONE_BY_ONE(Source.NDS2018_R2018_9_1_CD1, "CAS-oneByOne", Scenario.POPULATION, Scenario.POPULATION_CLONE),
    /**
     * 2D ({@code TwoDimTreapPopulation}).
     */
    TREAP(Source.NDS2016, "TreapPopulation", Scenario.TREAP_2D);

    private final Source source;
    private final String recordedName;
    private final List<Scenario> scenarios;

    Target(final Source source, final String recordedName, final Scenario... scenarios) {
        this.source = source;
        this.recordedName = recordedName;
        this.scenarios = List.of(scenarios);
    }

    /**
     * @return the legacy build whose golden file holds this target's blocks
     */
    Source source() {
        return source;
    }

    /**
     * @return the only scenario of a single-scenario target
     * @throws IllegalStateException if the target has several scenarios
     */
    Scenario scenario() {
        if (scenarios.size() != 1) {
            throw new IllegalStateException(this + " has scenarios " + scenarios);
        }
        return scenarios.get(0);
    }

    /**
     * @param scenario one of this target's scenarios
     * @return the block label of this target's blocks of that scenario: the recorded name, with suffix
     * {@code -clone} for {@link Scenario#POPULATION_CLONE}
     */
    String blockLabel(final Scenario scenario) {
        return scenario == Scenario.POPULATION_CLONE ? recordedName + "-clone" : recordedName;
    }

    /**
     * @return the block labels of all of this target's blocks
     */
    Set<String> blockLabels() {
        final Set<String> labels = new TreeSet<>();
        for (final Scenario scenario : scenarios) {
            labels.add(blockLabel(scenario));
        }
        return labels;
    }
}

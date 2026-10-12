package org.itmo.inds.golden;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.population.CasJfbyPopulation;
import org.itmo.inds.population.CoarseLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulation;
import org.itmo.inds.population.FineLockJfbyPopulationRankHint;
import org.itmo.inds.population.JfbyPopulation;
import org.itmo.inds.sorting.IncrementalJfbSorter;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.twod.TwoDimTreap;
import org.itmo.inds.twod.TwoDimTreapPopulation;
import org.itmo.inds.twod.TwoDimTreapTraversal;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Golden-master test of inds-core (terms and scenarios: package documentation): every recorded target of a class in
 * inds-core is replayed on the ported class and must give records identical to the legacy ones, line by line, legacy
 * defects included. The ENLU targets are checked in module inds-baselines ({@code EnluGoldenMasterTest}).
 */
class GoldenMasterTest {
    @TestFactory
    Stream<DynamicTest> replayMatchesLegacyRecords() {
        return Stream.of(
                GoldenCheck.test(Target.DC, Replays.dcSort()),
                GoldenCheck.test(Target.DC1, Replays.incremental(JfbSorter::new)),
                GoldenCheck.test(Target.DC2, Replays.incremental(IncrementalJfbSorter::new)),
                GoldenCheck.test(Target.ADD_RANKED_MEMBERS_DC1, Replays.addRankedMembers(JfbSorter::new)),
                GoldenCheck.test(Target.ADD_RANKED_MEMBERS_DC2, Replays.addRankedMembers(IncrementalJfbSorter::new)),
                GoldenCheck.test(Target.LEVEL, Replays.population(JfbyPopulation::new)),
                GoldenCheck.test(Target.COARSE, Replays.population(CoarseLockJfbyPopulation::new)),
                GoldenCheck.test(Target.FINE_RANK_HINT, Replays.population(FineLockJfbyPopulationRankHint::new)),
                GoldenCheck.test(Target.FINE, Replays.population(FineLockJfbyPopulation::new)),
                GoldenCheck.test(Target.CAS, Replays.population(expected -> cas(expected, false))),
                GoldenCheck.test(Target.CAS_ONE_BY_ONE, Replays.population(expected -> cas(expected, true))),
                GoldenCheck.test(Target.TREAP, GoldenMasterTest::treap2d));
    }

    /**
     * @param expected expected population size; CAS takes an {@code int}, so larger values (unlimited) are capped
     * @param oneByOne whether to insert one point at a time or by full sortings
     * @return an empty CAS population
     */
    private static CasJfbyPopulation<Integer> cas(final long expected, final boolean oneByOne) {
        return new CasJfbyPopulation<>((int) Math.min(expected, Integer.MAX_VALUE), oneByOne);
    }

    /**
     * Replays {@link Scenario#TREAP_2D} (input sets with k = 2; others are skipped): inserts all points one by one.
     * The levels are recorded in in-order (x order), which does not depend on the random treap priorities.
     */
    private static void treap2d(final Target target, final InputSet input, final RecordBuffer out) {
        if (input.k() != 2) {
            return;
        }
        out.begin(target, Scenario.TREAP_2D, input, RecordBuffer.NONE);
        final int n = input.n();
        int step = 0;
        try {
            final List<TwoDimTreap<Object>> levels = new ArrayList<>();
            final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels);
            for (int i = 0; i < n; ++i) {
                step = i + 1;
                final double[] point = input.points()[i];
                population.addPoint(new FitnessAndCdIndividual<>(new double[]{point[0], point[1]}, null));
                if (RecordBuffer.recordAt(step, n)) {
                    final boolean withDump = RecordBuffer.dumpAt(step, n);
                    final StateEncoder encoder = new StateEncoder(withDump);
                    encoder.population(population.size(), levels.size());
                    for (int levelIndex = 0; levelIndex < levels.size(); ++levelIndex) {
                        final List<Individual<Object>> members =
                                TwoDimTreapTraversal.inOrderPoints(levels.get(levelIndex));
                        encoder.level(levelIndex, members.size());
                        for (final Individual<Object> member : members) {
                            encoder.point(member.getObjectives());
                        }
                    }
                    out.step(step, population.size(), encoder.digest());
                    if (withDump) {
                        out.dump(step, encoder);
                    }
                }
            }
            out.ok();
        } catch (final Throwable thrown) {
            out.failed(thrown, step);
        }
    }
}

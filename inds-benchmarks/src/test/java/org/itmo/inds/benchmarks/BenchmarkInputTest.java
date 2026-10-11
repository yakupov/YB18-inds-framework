package org.itmo.inds.benchmarks;

import org.itmo.inds.oracle.Oracle;
import org.itmo.inds.testdata.DataSets;
import org.itmo.inds.testdata.FrontsAndAddends;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkInputTest {
    /**
     * The fronts are the true non-domination levels, also for a snapshot whose stored fronts are not (ZDT1 after
     * 10 000 iterations), and the input keeps the points and the addend of the data set.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#10000",
            "snapshots/dtlz3d/dtlz1_dim3_gen10000_iter10000_dataset1.json#5000",
            "snapshots/generational/dtlzNd/dtlz2_dim6_gen10000_iter100_dataset0.json#90",
    })
    void snapshotHasTrueLevels(final String reference) {
        final BenchmarkInput input = BenchmarkInput.snapshot(reference);
        final FrontsAndAddends stored = DataSets.snapshot(reference);
        assertEquals(stored.populationSize(), input.population().size(), "population size");
        assertTrueLevels(input);
        assertEquals(1, input.addends().size(), "addends");
        assertArrayEquals(stored.addends().get(0), input.addends().get(0), "the addend is the stored one");
        assertEquals(reference, input.reference());
        assertEquals(stored.k(), input.k());
    }

    @Test
    void sequenceHasTrueLevels() {
        final BenchmarkInput input =
                BenchmarkInput.sequence("sequences/dtlz3d/dtlz1_dim3_initSize5000_addendsCount1000_dataset1.json");
        assertEquals(5000, input.population().size(), "population size");
        assertEquals(1001, input.addends().size(), "addends");
        assertTrueLevels(input);
    }

    private static void assertTrueLevels(final BenchmarkInput input) {
        final int[] ranks = Oracle.ranks(input.population().toArray(new double[0][]));
        int index = 0;
        for (int front = 0; front < input.fronts().size(); ++front) {
            for (int member = 0; member < input.fronts().get(front).length; ++member) {
                assertEquals(front, ranks[index++], "rank of member " + member + " of front " + front);
            }
        }
    }
}
